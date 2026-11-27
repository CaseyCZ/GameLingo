#!/usr/bin/env python3
"""Build-time license inventory, not a complete SBOM or release clearance."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args]).decode("utf-8").strip()

def license_path(path):
    name = path.name.lower()
    return (name.startswith(("license", "licence", "copying", "notice", "copyright", "third_party_notices"))
            or any(p.lower() == "licenses" for p in path.parts))

def collect(root, label):
    revision = git(root, "rev-parse", "HEAD")
    files = git(root, "ls-files", "--recurse-submodules", "-z").split("\0")
    found = []
    for rel in sorted(set(files)):
        if not rel or not license_path(Path(rel)):
            continue
        path = root / rel
        if not path.is_file() or path.is_symlink() or path.stat().st_size > 2_000_000:
            continue
        data = path.read_bytes()
        try:
            text = data.decode("utf-8-sig")
        except UnicodeDecodeError:
            continue
        if "\0" in text:
            continue
        found.append((label + "/" + rel, data, text))
    return revision, found

def generate(args):
    kit = Path(args.kit).resolve()
    upstream = Path(args.upstream).resolve()
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    manifest = {"platform": args.platform, "scope": "tracked source notices, not complete SBOM",
                "sources": {}, "files": []}
    parts = ["GameLingo — bundled license texts\n",
             "Inventory is limited to collected source notices. Binary and transitive license review remains required.\n"]
    for root, label in [(kit, "GameLingo"), (upstream, "TP-Play" if args.platform == "ios" else "PlayCaption")]:
        revision, files = collect(root, label)
        manifest["sources"][label] = revision
        parts.append(label + " revision: " + revision)
        if not files:
            raise RuntimeError("No tracked license notices found: " + label)
        for name, data, text in files:
            manifest["files"].append({"path": name, "sha256": hashlib.sha256(data).hexdigest()})
            parts.extend(["\n===== " + name + " =====\n", text])
    if args.platform == "ios":
        for name in ("mbedtls-2.28.0", "opus-1.5.2"):
            root = upstream / "ios/.build/sources" / name
            if not root.is_dir():
                continue
            for path in sorted(root.iterdir()):
                if not license_path(path) or not path.is_file() or path.is_symlink():
                    continue
                data = path.read_bytes()
                try:
                    text = data.decode("utf-8-sig")
                except UnicodeDecodeError:
                    continue
                label = name + "/" + path.name
                manifest["files"].append({"path": label, "sha256": hashlib.sha256(data).hexdigest()})
                parts.extend(["\n===== " + label + " =====\n", text])
    for name in ("LICENSE", "ATTRIBUTION.md", "THIRD_PARTY_NOTICES.md"):
        path = kit / name
        if not path.is_file():
            raise RuntimeError("Missing project notice " + name)
        parts.extend(["\n===== GameLingo/" + name + " =====\n", path.read_text(encoding="utf-8")])
    (output / "GameLingoNotices.txt").write_text("\n".join(parts), encoding="utf-8")
    (output / "GameLingoLicenseInventory.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

def verify(path, platform):
    with zipfile.ZipFile(path) as z:
        prefix = "Payload/GameLingoIOS.app/" if platform == "ios" else "assets/"
        notices = z.read(prefix + "GameLingoNotices.txt").decode("utf-8")
        manifest = json.loads(z.read(prefix + "GameLingoLicenseInventory.json"))
        if manifest["platform"] != platform or "GNU AFFERO GENERAL PUBLIC LICENSE" not in notices.upper():
            raise RuntimeError("Missing required license content")
        if not manifest["files"] or len(manifest["sources"]) != 2:
            raise RuntimeError("Missing source license inventory")
        for name in ("LICENSE", "ATTRIBUTION.md", "THIRD_PARTY_NOTICES.md"):
            if "===== GameLingo/" + name + " =====" not in notices:
                raise RuntimeError("Missing bundled project notice: " + name)
        print("Bundled license inventory verified:", platform, len(manifest["files"]), "source notice files")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("platform", choices=["ios", "mobile", "tv"])
    parser.add_argument("--kit")
    parser.add_argument("--upstream")
    parser.add_argument("--output")
    parser.add_argument("--verify")
    args = parser.parse_args()
    if args.verify:
        verify(args.verify, args.platform)
    elif args.kit and args.upstream and args.output:
        generate(args)
    else:
        parser.error("Provide --verify or --kit --upstream --output")
