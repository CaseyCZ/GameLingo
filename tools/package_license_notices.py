#!/usr/bin/env python3
"""Build-time license inventory, not a complete SBOM or release clearance."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile
import re

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

def native_provenance(upstream):
    """Validate the exact ARM prebuilts against the pinned upstream manifest."""
    path = upstream / "native/chiaki-build.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    if not re.fullmatch(r"[0-9a-f]{40}", data["upstream"]):
        raise RuntimeError("Invalid Chiaki revision")
    expected = {(abi, name) for abi in ("arm64-v8a", "armeabi-v7a")
                for name in ("libchiaki-jni.so", "libchiaki-pc.so")}
    libraries = data["libraries"]
    entries = {}
    for abi, name in sorted(expected):
        digest = libraries[abi][name]
        source = upstream / "app/src/main/jniLibs" / abi / name
        if not re.fullmatch(r"[0-9a-f]{64}", digest) or hashlib.sha256(source.read_bytes()).hexdigest() != digest:
            raise RuntimeError("Chiaki source binary hash mismatch: " + str(source))
        entries["lib/" + abi + "/" + name] = digest
    return {"revision": data["upstream"], "ndk": data["ndk"],
            "manifest_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "source_binary_sha256": entries}

def add_notice(manifest, parts, name, data, text):
    # Verify the exact UTF-8 text as embedded, separately from original bytes
    # (which may have a BOM). Offsets are Unicode code-point offsets.
    parts.append("\n===== " + name + " =====\n")
    start = sum(len(part) for part in parts) + len(parts)
    parts.append(text)
    manifest["files"].append({"path": name, "sha256": hashlib.sha256(data).hexdigest(),
        "text_sha256": hashlib.sha256(text.encode("utf-8")).hexdigest(),
        "start": start, "length": len(text)})

def generate(args):
    kit = Path(args.kit).resolve()
    upstream = Path(args.upstream).resolve()
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    manifest = {"platform": args.platform, "scope": "tracked source notices, not complete SBOM",
                "sources": {}, "files": [], "schema": 2}
    parts = ["GameLingo — bundled license texts\n",
             "Inventory is limited to collected source notices. Binary and transitive license review remains required.\n"]
    for root, label in [(kit, "GameLingo"), (upstream, "TP-Play" if args.platform == "ios" else "PlayCaption")]:
        revision, files = collect(root, label)
        manifest["sources"][label] = revision
        parts.append(label + " revision: " + revision)
        if not files:
            raise RuntimeError("No tracked license notices found: " + label)
        manifest.setdefault("submodules", {})[label] = git(root, "submodule", "status", "--recursive").splitlines()
        for name, data, text in files:
            add_notice(manifest, parts, name, data, text)
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
                add_notice(manifest, parts, label, data, text)
    if args.platform in ("mobile", "tv"):
        manifest["chiaki"] = native_provenance(upstream)
    else:
        core = upstream / "ios/.build/iphoneos/libTPPlayCore.a"
        if not core.is_file():
            raise RuntimeError("Missing built TP Play core")
        manifest["native_core_sha256"] = hashlib.sha256(core.read_bytes()).hexdigest()
    for name in ("LICENSE", "ATTRIBUTION.md", "THIRD_PARTY_NOTICES.md"):
        path = kit / name
        if not path.is_file():
            raise RuntimeError("Missing project notice " + name)
        if not any(entry["path"] == "GameLingo/" + name for entry in manifest["files"]):
            data = path.read_bytes()
            add_notice(manifest, parts, "GameLingo/" + name, data, data.decode("utf-8-sig"))
    (output / "GameLingoNotices.txt").write_text("\n".join(parts), encoding="utf-8")
    (output / "GameLingoLicenseInventory.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

def verify(path, platform, evidence=None, expected_kit=None):
    with zipfile.ZipFile(path) as z:
        prefix = "Payload/GameLingoIOS.app/" if platform == "ios" else "assets/"
        notices = z.read(prefix + "GameLingoNotices.txt").decode("utf-8")
        manifest = json.loads(z.read(prefix + "GameLingoLicenseInventory.json"))
        if manifest["platform"] != platform or "GNU AFFERO GENERAL PUBLIC LICENSE" not in notices.upper():
            raise RuntimeError("Missing required license content")
        if manifest.get("schema") != 2 or not manifest["files"] or len(manifest["sources"]) != 2:
            raise RuntimeError("Missing source license inventory")
        if expected_kit and manifest["sources"].get("GameLingo") != expected_kit:
            raise RuntimeError("License inventory source revision mismatch")
        seen = set()
        for item in manifest["files"]:
            name = item["path"]
            if name in seen:
                raise RuntimeError("Duplicate license inventory entry: " + name)
            seen.add(name)
            start, length = item["start"], item["length"]
            header = "\n===== " + name + " =====\n\n"
            if (start < len(header) or length < 0 or start + length > len(notices)
                or notices[start-len(header):start] != header
                or hashlib.sha256(notices[start:start+length].encode("utf-8")).hexdigest() != item["text_sha256"]):
                raise RuntimeError("Bundled license text hash mismatch: " + name)
        for name in ("LICENSE", "ATTRIBUTION.md", "THIRD_PARTY_NOTICES.md"):
            if "GameLingo/" + name not in seen:
                raise RuntimeError("Missing bundled project notice: " + name)
        # Report actual package contents. Android Gradle may strip symbols;
        # distinguish a changed packaged binary from a verified byte match.
        native = {}
        for entry in z.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            if (platform != "ios" and name.startswith("lib/") and name.endswith(".so")) or (
                platform == "ios" and name.startswith(prefix) and
                (name == prefix + "GameLingoIOS" or name.endswith(".dylib")
                 or (".framework/" in name and name.rsplit("/", 1)[-1] == name.split(".framework/", 1)[0].rsplit("/", 1)[-1]))):
                with z.open(entry) as stream:
                    native[name] = hashlib.file_digest(stream, "sha256").hexdigest()
        comparison = {}
        if platform != "ios":
            for name, source_hash in manifest["chiaki"]["source_binary_sha256"].items():
                if name not in native:
                    raise RuntimeError("Missing expected Chiaki package library: " + name)
                comparison[name] = "byte_match" if native[name] == source_hash else "different_from_source_requires_review"
        elif prefix + "GameLingoIOS" not in native:
            raise RuntimeError("Missing iOS main executable")
        report = {"platform": platform, "sources": manifest["sources"],
                  "notice_count": len(seen), "native_files": native,
                  "chiaki_package_comparison": comparison,
                  "scope": "technical evidence; not complete SBOM or distribution clearance"}
        if evidence:
            Path(evidence).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print("Bundled license inventory verified:", platform, len(seen), "source notice files")
        print("Native files:", len(native), "Chiaki differing bytes:", sum(v != "byte_match" for v in comparison.values()))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("platform", choices=["ios", "mobile", "tv"])
    parser.add_argument("--kit")
    parser.add_argument("--upstream")
    parser.add_argument("--output")
    parser.add_argument("--verify")
    parser.add_argument("--evidence")
    parser.add_argument("--expected-kit")
    args = parser.parse_args()
    if args.verify:
        verify(args.verify, args.platform, args.evidence, args.expected_kit)
    elif args.kit and args.upstream and args.output:
        generate(args)
    else:
        parser.error("Provide --verify or --kit --upstream --output")
