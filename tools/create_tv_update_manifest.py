#!/usr/bin/env python3
"""Generate update metadata from the actual APK; require the configured permanent signer."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

PACKAGE = "cz.caseycz.gamelingo.tv"

def metadata(apk, tag, version, badging, cert, expected):
    name = f"GameLingo-GoogleTV-v{version}-dev-TEST.apk"
    if apk.name != name or not re.fullmatch(r"v" + re.escape(version) + r"-test-[a-f0-9]{8}-r[0-9]+", tag):
        raise ValueError("APK name/tag does not match release")
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match or match[1] != PACKAGE or not match[3].startswith(version + "-dev."):
        raise ValueError("Wrong APK identity/version")
    if not re.fullmatch(r"[a-f0-9]{64}", cert) or cert != expected:
        raise ValueError("APK is not signed with the configured permanent TV key")
    if not 0 < int(match[2]) <= 2100000000 or not 0 < apk.stat().st_size <= 150 * 1024 * 1024:
        raise ValueError("Invalid APK size or version code")
    return dict(schema=1, package=PACKAGE, tag=tag, file=name, versionName=match[3],
                versionCode=int(match[2]), size=apk.stat().st_size,
                sha256=hashlib.file_digest(apk.open("rb"), "sha256").hexdigest(), certSha256=cert)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("apk", type=Path)
    p.add_argument("tag")
    p.add_argument("version")
    p.add_argument("output", type=Path)
    args = p.parse_args()
    encoded = os.environ.get("GAMELINGO_TV_KEYSTORE_B64")
    password = os.environ.get("GAMELINGO_TV_KEYSTORE_PASSWORD")
    if not encoded or not password:
        raise SystemExit("Publication blocked: permanent TV signing key is not configured.")
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools/34.0.0"
    # Private key material never enters the release or repository.
    with tempfile.TemporaryDirectory() as tmp:
        keystore = Path(tmp) / "tv.p12"
        fd = os.open(keystore, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as out:
            out.write(base64.b64decode(encoded, validate=True))
        public = subprocess.check_output(["keytool", "-exportcert", "-keystore", str(keystore),
            "-storetype", "PKCS12", "-alias", "gamelingo-tv",
            "-storepass:env", "GAMELINGO_TV_KEYSTORE_PASSWORD"])
        expected = hashlib.sha256(public).hexdigest()
    verified = subprocess.check_output([str(sdk / "apksigner"), "verify", "--print-certs", str(args.apk)], text=True)
    certs = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})", verified)
    if len(certs) != 1:
        raise SystemExit("Publication blocked: expected exactly one APK signer.")
    badging = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(args.apk)], text=True)
    info = metadata(args.apk, args.tag, args.version, badging, certs[0].lower(), expected)
    args.output.write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    print("TV update manifest verified against permanent signing certificate.")

if __name__ == "__main__":
    main()
