#!/usr/bin/env python3
"""Build-time license inventory, not a complete SBOM or release clearance."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile
import re
import xml.etree.ElementTree as ET

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

def archive_notices(archive, prefix, nested=True):
    """Read textual notices from the resolved archive, including embedded JARs."""
    for entry in sorted(archive.infolist(), key=lambda item: item.filename):
        name = entry.filename
        if entry.is_dir() or any(ord(ch) < 32 for ch in name):
            continue
        path = Path(name)
        if path.is_absolute() or ".." in path.parts:
            continue
        basename = path.name.lower()
        recognized = (license_path(path) or basename in
                      {"al2.0", "lgpl2.1", "dependencies"} or
                      re.fullmatch(r"third_party_licenses?(?:_metadata)?(?:\.(?:txt|json))?", basename) is not None)
        if recognized and entry.file_size <= 2_000_000:
            data = archive.read(entry)
            try:
                text = data.decode("utf-8-sig")
            except UnicodeDecodeError:
                continue
            if text.strip() and "\0" not in text:
                yield prefix + "/" + name, data, text
        elif nested and name.endswith(".jar") and entry.file_size <= 64_000_000:
            with zipfile.ZipFile(io.BytesIO(archive.read(entry))) as jar:
                yield from archive_notices(jar, prefix + "/" + name, nested=False)


def archive_native_files(archive):
    """Identify the actual native payload in a resolved AAR by ABI and hash."""
    found = {}
    for entry in archive.infolist():
        match = re.fullmatch(r"(?:jni|lib)/(arm64-v8a|armeabi-v7a|x86|x86_64)/([A-Za-z0-9_.+-]+\.so)", entry.filename)
        if not match or entry.is_dir():
            continue
        key = "lib/" + "/".join(match.groups())
        with archive.open(entry) as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if key in found and found[key] != digest:
            raise RuntimeError("Ambiguous native archive entry: " + key)
        found[key] = digest
    return found


def native_archive_comparison(native, dependencies, upstream):
    """Match package files to actual source bytes, without guessing by library name."""
    origins = {}
    for item in dependencies:
        for name, digest in item.get("native_files", {}).items():
            origins.setdefault(name, []).append({"source": item["coordinate"],
                                                "artifact_sha256": item["sha256"], "sha256": digest})
    for name, digest in upstream.items():
        origins.setdefault(name, []).append({"source": "PlayCaption pinned prebuilts", "sha256": digest})
    result = {}
    for name, digest in sorted(native.items()):
        candidates = origins.get(name, [])
        matches = [item for item in candidates if item["sha256"] == digest]
        status = ("byte_match" if matches else "different_bytes_requires_review" if candidates
                  else "unmapped_requires_review")
        result[name] = {"status": status, "candidates": candidates, "matches": matches}
    return result


def pom_declarations(path):
    """Read direct license declarations; never infer a grant from absent metadata."""
    if not path:
        return {"status": "pom_unavailable", "licenses": []}
    data = Path(path).read_bytes()
    if len(data) > 2_000_000 or re.search(br"<!DOCTYPE|<!ENTITY", data, re.IGNORECASE):
        raise RuntimeError("Unexpected Maven POM XML")
    root = ET.fromstring(data)
    namespace = root.tag.split("}")[0] + "}" if root.tag.startswith("{") else ""
    def field(node, name):
        return (node.findtext(namespace + name) or "").strip()
    entries = []
    for node in root.findall(namespace + "licenses/" + namespace + "license"):
        entry = {key: field(node, key) for key in ("name", "url", "distribution", "comments")}
        if any(entry.values()):
            entries.append(entry)
    parent = root.find(namespace + "parent")
    result = {"status": "declared" if entries else "no_direct_declaration",
              "licenses": entries, "sha256": hashlib.sha256(data).hexdigest()}
    if parent is not None:
        result["parent"] = {key: field(parent, key) for key in ("groupId", "artifactId", "version")}
    return result


def inherited_pom_declarations(child, parent_record):
    """Only use a parent declaration if it matches the child's explicit parent."""
    if not parent_record or child["licenses"] or not child.get("parent"):
        return None
    identity = ":".join(child["parent"].get(key, "") for key in
                        ("groupId", "artifactId", "version"))
    if identity != parent_record["coordinate"]:
        raise RuntimeError("Maven parent coordinate differs from child POM: " + identity)
    parent = pom_declarations(parent_record["file"])
    return {"coordinate": identity, **parent}


def android_dependency_notices(path, manifest, parts):
    records = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(records, list) or not records:
        raise RuntimeError("Missing resolved Android runtime artifacts")
    results = []
    seen = set()
    for record in sorted(records, key=lambda r: (r["coordinate"], r["name"])):
        coordinate, name = record["coordinate"], record["name"]
        file = Path(record["file"])
        if not re.fullmatch(r"[A-Za-z0-9_.+-]+:[A-Za-z0-9_.+-]+:[A-Za-z0-9_.+-]+", coordinate):
            raise RuntimeError("Unexpected runtime component: " + coordinate)
        if name != file.name or not re.fullmatch(r"[A-Za-z0-9_.+-]+", name):
            raise RuntimeError("Unexpected runtime artifact name: " + name)
        with file.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        # Gradle may resolve the same module artifact through multiple variants.
        # Collapse only byte-identical artifacts; preserve distinct contents.
        identity = (coordinate, name, digest)
        if identity in seen:
            continue
        seen.add(identity)
        label = "Android-runtime/" + coordinate + "/" + digest + "/" + name
        result = {"coordinate": coordinate, "artifact": name,
                  "sha256": digest, "notices": [], "native_files": {}}
        if zipfile.is_zipfile(file):
            with zipfile.ZipFile(file) as archive:
                result["native_files"] = archive_native_files(archive)
                for title, data, text in archive_notices(archive, label):
                    add_notice(manifest, parts, title, data, text)
                    result["notices"].append(title)
        result["pom"] = pom_declarations(record.get("pom"))
        parent = inherited_pom_declarations(result["pom"], record.get("parent_pom"))
        if parent is not None:
            result["parent_pom"] = parent
        declared = result["pom"]["licenses"] or (parent["licenses"] if parent else [])
        if declared:
            # Keep declarations visibly distinct from original license texts.
            declaration = ("Publisher Maven license declarations (not full license text):\n" +
                           coordinate + "\n" +
                           ("Inherited from " + parent["coordinate"] + "\n" if parent and parent["licenses"] else "") +
                           json.dumps(declared, ensure_ascii=False, indent=2) + "\n")
            title = label + "/Maven-declared-licenses.txt"
            add_notice(manifest, parts, title, declaration.encode("utf-8"), declaration)
            result["pom_notice"] = title
        result["review"] = "notices_collected" if result["notices"] else "no_embedded_notice_found"
        results.append(result)
    return sorted(results, key=lambda item: (item['coordinate'], item['artifact'], item['sha256']))


def ios_dependency_notices(args, manifest, parts):
    resolved = json.loads(Path(args.swift_resolved).read_text(encoding="utf-8"))
    pins = resolved["pins"]
    webrtc = [pin for pin in pins if pin["identity"].lower() == "webrtc"]
    if len(webrtc) != 1:
        raise RuntimeError("Expected one resolved WebRTC package")
    checkout = Path(args.webrtc_checkout)
    revision = git(checkout, "rev-parse", "HEAD")
    if revision != webrtc[0]["state"]["revision"]:
        raise RuntimeError("WebRTC checkout differs from Package.resolved")
    framework = Path(args.webrtc_license)
    if framework.parent.name != "WebRTC.xcframework" or framework.name != "LICENSE":
        raise RuntimeError("Expected license from resolved WebRTC XCFramework")
    files = [("SwiftPM/WebRTC/LICENSE.md", checkout / "LICENSE.md"),
             ("SwiftPM/WebRTC.xcframework/LICENSE", framework)]
    for name, file in files:
        data = file.read_bytes()
        text = data.decode("utf-8-sig")
        if not text.strip() or "\0" in text:
            raise RuntimeError("Empty or invalid WebRTC notice: " + name)
        add_notice(manifest, parts, name, data, text)
    return {"pins": pins, "webrtc_notices": [name for name, _ in files]}


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
    patches = {}
    for filename, field in (("chiaki-audio-unlock.patch", "patchSha256"),
                            ("chiaki-silent-audio.patch", "playbackPatchSha256")):
        digest = hashlib.sha256((upstream / "native" / filename).read_bytes()).hexdigest()
        if digest != data[field]:
            raise RuntimeError("Chiaki source patch hash mismatch: " + filename)
        patches[filename] = digest
    return {"revision": data["upstream"], "ndk": data["ndk"],
            "manifest_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "patch_sha256": patches, "source_binary_sha256": entries}


def pcobraz_provenance(upstream):
    """Record the original shim and its tracked prebuilts, not a reproducible build."""
    revision = git(upstream, "rev-parse", "HEAD")
    result = {"revision": revision, "source": "native/pcobraz.c",
              "source_binary_sha256": {}, "rebuild_verified": False}
    paths = ["native/pcobraz.c"] + [
        "app/src/main/jniLibs/" + abi + "/libpcobraz1.so"
        for abi in ("arm64-v8a", "armeabi-v7a")]
    for rel in paths:
        source = (upstream / rel).read_bytes()
        tracked = subprocess.check_output(["git", "-C", str(upstream), "show", revision + ":" + rel])
        if source != tracked:
            raise RuntimeError("PlayCaption shim differs from pinned source: " + rel)
        digest = hashlib.sha256(source).hexdigest()
        if rel == result["source"]:
            result["source_sha256"] = digest
        else:
            result["source_binary_sha256"][rel.replace("app/src/main/jniLibs/", "lib/")] = digest
    return result

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
        manifest["pcobraz"] = pcobraz_provenance(upstream)
        manifest["runtime_dependencies"] = android_dependency_notices(args.runtime_artifacts, manifest, parts)
    else:
        manifest["swift_dependencies"] = ios_dependency_notices(args, manifest, parts)
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
        if platform == "ios":
            swift = manifest.get("swift_dependencies", {})
            if not swift.get("pins") or not {"SwiftPM/WebRTC/LICENSE.md", "SwiftPM/WebRTC.xcframework/LICENSE"}.issubset(seen):
                raise RuntimeError("Missing resolved Swift dependency notices")
        else:
            dependencies = manifest.get("runtime_dependencies", [])
            if not dependencies or any(not set(item["notices"]).issubset(seen) or
                                       (item.get("pom_notice") and item["pom_notice"] not in seen) or
                                       "pom" not in item for item in dependencies):
                raise RuntimeError("Missing resolved Android dependency evidence")
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
        native_origins = {}
        if platform != "ios":
            for name, source_hash in manifest["chiaki"]["source_binary_sha256"].items():
                if name not in native:
                    raise RuntimeError("Missing expected Chiaki package library: " + name)
                comparison[name] = "byte_match" if native[name] == source_hash else "different_from_source_requires_review"
            shim = manifest.get("pcobraz", {}).get("source_binary_sha256", {})
            if len(shim) != 2 or any(name not in native for name in shim):
                raise RuntimeError("Missing PlayCaption shim package evidence")
            native_origins = native_archive_comparison(native, manifest["runtime_dependencies"],
                {**manifest["chiaki"]["source_binary_sha256"], **shim})
        elif prefix + "GameLingoIOS" not in native:
            raise RuntimeError("Missing iOS main executable")
        report = {"platform": platform, "sources": manifest["sources"],
                  "notice_count": len(seen), "native_files": native,
                  "chiaki_package_comparison": comparison,
                  "pcobraz": manifest.get("pcobraz", {}),
                  "native_origins": native_origins,
                  "runtime_dependencies": manifest.get("runtime_dependencies", []),
                  "swift_dependencies": manifest.get("swift_dependencies", {}),
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
    parser.add_argument("--runtime-artifacts")
    parser.add_argument("--swift-resolved")
    parser.add_argument("--webrtc-checkout")
    parser.add_argument("--webrtc-license")
    args = parser.parse_args()
    if args.verify:
        verify(args.verify, args.platform, args.evidence, args.expected_kit)
    elif args.kit and args.upstream and args.output:
        if args.platform == "ios" and not all((args.swift_resolved, args.webrtc_checkout, args.webrtc_license)):
            parser.error("iOS generation requires resolved Swift package and framework license inputs")
        if args.platform != "ios" and not args.runtime_artifacts:
            parser.error("Android generation requires resolved runtime artifacts")
        generate(args)
    else:
        parser.error("Provide --verify or --kit --upstream --output")
