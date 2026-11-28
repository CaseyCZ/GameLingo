#!/usr/bin/env python3
"""Update the pinned Android toolchain and audit actual APK compatibility."""
import argparse
import json
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

AGP = "8.10.1"
GRADLE = "8.11.1"
KOTLIN = "2.2.21"
COMPILE_SDK = 36
MIN_SDK = 26
TARGETS = {"mobile": 36, "tv": 34}
ANDROID = "{http://schemas.android.com/apk/res/android}"
PAGE_SIZE = 16384

def once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError("Android SDK anchor changed: " + old)
    return text.replace(old, new, 1)

def apply(root, variant):
    plugins = root / "build.gradle.kts"
    s = plugins.read_text(encoding="utf-8")
    s = once(s, 'id("com.android.application") version "8.5.2"', f'id("com.android.application") version "{AGP}"')
    s = once(s, 'id("org.jetbrains.kotlin.android") version "1.9.24"', f'id("org.jetbrains.kotlin.android") version "{KOTLIN}"')
    plugins.write_text(s, encoding="utf-8")
    wrapper = root / "gradle/wrapper/gradle-wrapper.properties"
    s = wrapper.read_text(encoding="utf-8")
    s = once(s, "gradle-9.6.0-bin.zip", f"gradle-{GRADLE}-bin.zip")
    wrapper.write_text(s, encoding="utf-8")
    gradle = root / "app/build.gradle.kts"
    s = gradle.read_text(encoding="utf-8")
    s = once(s, "compileSdk = 34", f"compileSdk = {COMPILE_SDK}")
    s = once(s, "targetSdk = 34", f"targetSdk = {TARGETS[variant]}")
    if f"minSdk = {MIN_SDK}" not in s:
        raise RuntimeError("Unexpected minimum SDK")
    s = once(s, '    kotlinOptions { jvmTarget = "17" }', "")
    s = once(s, "dependencies {", """kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {""")
    gradle.write_text(s, encoding="utf-8")

    folder = root / "app/src/main/java/cz/titulky/tv"
    helper = Path(__file__).with_name("GameLingoWindowInsets.kt")
    (folder / helper.name).write_text(helper.read_text(encoding="utf-8"), encoding="utf-8")
    manifest = root / "app/src/main/AndroidManifest.xml"
    ET.register_namespace("android", ANDROID[1:-1])
    ET.register_namespace("tools", "http://schemas.android.com/tools")
    tree = ET.parse(manifest)
    app = tree.getroot().find("application")
    if app is None or app.get(ANDROID + "name"):
        raise RuntimeError("Unexpected Android application class")
    if variant == "mobile":
        app.set(ANDROID + "name", ".GameLingoApplication")
    tree.write(manifest, encoding="utf-8", xml_declaration=True)

    # The shared page helper and two full-screen dialog paths also run on mobile.
    # Floating dialogs retain the platform's own layout.
    ui = folder / "GameLingoTVUi.kt"
    s = ui.read_text(encoding="utf-8")
    s = once(s, "        dialog.window?.setLayout(-1,-1)", """        dialog.window?.setLayout(-1,-1)
        dialog.window?.let { GameLingoWindowInsets.apply(it) }""")
    anchor = """        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)"""
    if s.count(anchor) != 2:
        raise RuntimeError("Full-screen dialog anchors changed")
    s = s.replace(anchor, anchor + "\n        dialog.window?.let { GameLingoWindowInsets.apply(it) }")
    ui.write_text(s, encoding="utf-8")

def elf_load_segments(data):
    """Read program headers, not section alignment. Supports both ELF classes."""
    if len(data) < 52 or data[:4] != b"\x7fELF" or data[5] not in (1, 2):
        raise ValueError("Invalid ELF header")
    endian = "<" if data[5] == 1 else ">"
    if data[4] == 2:
        header = struct.Struct(endian + "HHIQQQIHHHHHH")
        program = struct.Struct(endian + "IIQQQQQQ")
        indexes = (2, 3, 7)
    elif data[4] == 1:
        header = struct.Struct(endian + "HHIIIIIHHHHHH")
        program = struct.Struct(endian + "IIIIIIII")
        indexes = (1, 2, 7)
    else:
        raise ValueError("Unknown ELF class")
    fields = header.unpack_from(data, 16)
    offset, size, count = fields[4], fields[8], fields[9]
    if count == 0 or size < program.size or offset + size * count > len(data):
        raise ValueError("Invalid ELF program headers")
    result = []
    for i in range(count):
        fields = program.unpack_from(data, offset + i * size)
        if fields[0] == 1:  # PT_LOAD
            file_offset, virtual_address, alignment = (fields[j] for j in indexes)
            result.append({"offset": file_offset, "vaddr": virtual_address, "alignment": alignment,
                           "aligned_16kb": alignment >= PAGE_SIZE and
                           (virtual_address - file_offset) % PAGE_SIZE == 0})
    if not result:
        raise ValueError("ELF has no PT_LOAD segments")
    return result

def audit(apk, variant, aapt, zipalign, output, strict=False):
    badging = subprocess.check_output([aapt, "dump", "badging", str(apk)], text=True)
    def sdk(key):
        match = re.search(r"(?m)^" + key + r":'(\d+)'", badging)
        if not match:
            raise RuntimeError("APK missing " + key)
        return int(match[1])
    minimum, target = sdk("sdkVersion"), sdk("targetSdkVersion")
    if minimum != MIN_SDK or target != TARGETS[variant]:
        raise RuntimeError(f"Unexpected SDK min={minimum} target={target}")
    native = []
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            if not entry.filename.startswith("lib/") or not entry.filename.endswith(".so"):
                continue
            abi = entry.filename.split("/")[1]
            segments = elf_load_segments(archive.read(entry))
            native.append({"path": entry.filename, "abi": abi, "load_segments": segments,
                           "aligned_16kb": all(s["aligned_16kb"] for s in segments)})
    abis = sorted({item["abi"] for item in native})
    if not {"arm64-v8a", "armeabi-v7a"}.issubset(abis):
        raise RuntimeError("Missing required ARM ABI")
    incompatible = [item["path"] for item in native
                    if item["abi"] in ("arm64-v8a", "x86_64") and not item["aligned_16kb"]]
    check = subprocess.run([zipalign, "-c", "-P", "16", "4", str(apk)],
                           capture_output=True, text=True)
    report = {"variant": variant, "min_sdk": minimum, "target_sdk": target,
              "abis": abis, "native_libraries": native,
              "incompatible_64bit_libraries": incompatible,
              "apk_zip_alignment_passed": check.returncode == 0,
              "static_16kb_checks_passed": not incompatible and check.returncode == 0,
              "runtime_tested_on_16kb_device": False}
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "native_libraries"}, indent=2))
    if not report["static_16kb_checks_passed"]:
        print("::warning::16 KB checks failed; Store distribution is blocked. Existing GitHub candidate build remains available.")
        if strict:
            raise RuntimeError("APK failed 16 KB checks")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("variant", choices=TARGETS)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--apply", type=Path)
    group.add_argument("--apk", type=Path)
    parser.add_argument("--aapt")
    parser.add_argument("--zipalign")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    if args.apply:
        apply(args.apply, args.variant)
    else:
        if not args.aapt or not args.zipalign or not args.output:
            parser.error("APK audit requires --aapt, --zipalign and --output")
        audit(args.apk, args.variant, args.aapt, args.zipalign, args.output, args.strict)
