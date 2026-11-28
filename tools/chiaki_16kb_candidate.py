#!/usr/bin/env python3
"""Experimental 16 KB build; never install candidate libraries into the APK."""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from android_sdk_policy import elf_load_segments

NDK = "28.2.13676358"
UPSTREAM = "f814cf69d636f149ad5a3cfffd19cb22da90beda"
PLAYCAPTION = "633a847400b6c4bac1e7f034463e7847a53a9590"

def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("playcaption", type=Path)
    parser.add_argument("chiaki", type=Path)
    parser.add_argument("ndk", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    pc, chiaki, ndk, output = (p.resolve() for p in (args.playcaption, args.chiaki, args.ndk, args.output))
    if git(pc, "rev-parse", "HEAD") != PLAYCAPTION or git(chiaki, "rev-parse", "HEAD") != UPSTREAM:
        raise RuntimeError("Unexpected native source revision")
    if git(pc, "status", "--porcelain", "--untracked-files=no") or git(chiaki, "status", "--porcelain", "--untracked-files=no"):
        raise RuntimeError("Expected clean source trees")
    if output.is_relative_to(pc) or output.is_relative_to(chiaki):
        raise RuntimeError("Candidate output must be outside source checkouts")
    if f"Pkg.Revision = {NDK}" not in (ndk / "source.properties").read_text():
        raise RuntimeError("Unexpected NDK")
    source = pc / "native/build_chiaki.py"
    original = source.read_text(encoding="utf-8")
    old = 'NDK_REVISION = "21.4.7075529"'
    if original.count(old) != 1:
        raise RuntimeError("Upstream native build contract changed")
    patched = original.replace(old, f'NDK_REVISION = "{NDK}"')
    patched = patched.replace("NDK r21e", "NDK r28c").replace("21.4.7075529", NDK)
    # NDK r28 enables flexible pages by default. State explicit linker flags
    # as a build contract; do not alter JNI source, audio patches or crypto here.
    anchor = '"-DANDROID_ABI=" + abi, "-DANDROID_PLATFORM=android-21", "-DCMAKE_BUILD_TYPE=Release",'
    if patched.count(anchor) != 1:
        raise RuntimeError("Upstream CMake build anchor changed")
    patched = patched.replace(anchor, anchor + '\n            "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384,-z,common-page-size=16384",')
    # The original script resolves its own directory for reviewed audio patches.
    # Run an in-memory derivative with the original __file__ to retain them.
    subprocess.run([sys.executable, "-c",
        "import sys; p=sys.argv[1]; sys.argv=sys.argv[1:]; "
        "exec(compile(sys.stdin.read(), p, 'exec'), {'__name__':'__main__','__file__':p})",
        str(source), str(chiaki), str(ndk), str(output)],
        input=patched, text=True, check=True)
    manifest = json.loads((output / "manifest.json").read_text())
    evidence = {"source_revision": UPSTREAM, "playcaption_revision": PLAYCAPTION,
                "ndk": NDK, "upstream_build_script_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "candidate_build_script_sha256": hashlib.sha256(patched.encode()).hexdigest(),
                "submodules": git(chiaki, "submodule", "status", "--recursive").splitlines(),
                "libraries": [], "runtime_tested": False, "installed_in_application": False}
    for abi in ("arm64-v8a", "armeabi-v7a"):
        for name in ("libchiaki-jni.so", "libchiaki-pc.so"):
            path = output / "jniLibs" / abi / name
            data = path.read_bytes()
            digest = hashlib.sha256(data).hexdigest()
            if digest != manifest["libraries"][abi][name]:
                raise RuntimeError("Native artifact hash mismatch")
            segments = elf_load_segments(data)
            aligned = all(item["aligned_16kb"] for item in segments)
            evidence["libraries"].append({"abi": abi, "name": name, "sha256": digest,
                                           "aligned_16kb": aligned, "load_segments": segments})
            if abi == "arm64-v8a" and not aligned:
                raise RuntimeError("16 KB candidate not aligned: " + name)
    (output / "candidate-evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print("Candidate passed source build, inherited JNI/dependency comparisons and ARM64 ELF alignment; device tests still required.")
if __name__ == "__main__":
    main()
