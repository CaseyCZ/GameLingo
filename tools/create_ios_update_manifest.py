"""Derive unsigned IPA update metadata from the exact validated build."""
import argparse
import hashlib
import json
import plistlib
import re
import zipfile
from pathlib import Path

def metadata(ipa, tag, version, build):
    if ipa.name != f"GameLingo-iOS-v{version}-dev-unsigned.ipa" or not re.fullmatch(r"v" + re.escape(version) + r"-test-[a-f0-9]{8}-r[0-9]+", tag):
        raise ValueError("Wrong IPA name or tag")
    with zipfile.ZipFile(ipa) as archive:
        paths = [p for p in archive.namelist() if re.fullmatch(r"Payload/[^/]+\.app/Info\.plist", p)]
        if len(paths) != 1 or archive.getinfo(paths[0]).file_size > 1024*1024:
            raise ValueError("Missing or ambiguous app metadata")
        info = plistlib.loads(archive.read(paths[0]))
    if info.get("CFBundleIdentifier") != "cz.caseycz.gamelingo.ios" or info.get("CFBundleShortVersionString") != version or str(info.get("CFBundleVersion")) != str(build):
        raise ValueError("Wrong IPA identity or version")
    if not 0 < ipa.stat().st_size <= 150*1024*1024 or not 0 < int(build) <= 2000:
        raise ValueError("Invalid IPA size/build")
    with ipa.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return dict(schema=1, package="cz.caseycz.gamelingo.ios", tag=tag, file=ipa.name,
                versionName=version, versionCode=int(build), size=ipa.stat().st_size, sha256=digest)

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("ipa", type=Path)
    p.add_argument("tag")
    p.add_argument("version")
    p.add_argument("build", type=int)
    p.add_argument("output", type=Path)
    a = p.parse_args()
    a.output.write_text(json.dumps(metadata(a.ipa,a.tag,a.version,a.build),indent=2)+"\n", encoding="utf8")
