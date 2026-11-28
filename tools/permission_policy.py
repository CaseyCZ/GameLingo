#!/usr/bin/env python3
"""Finalize per-platform permissions and verify the actual packaged declarations."""
import argparse
import plistlib
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ANDROID = "http://schemas.android.com/apk/res/android"
TOOLS = "http://schemas.android.com/tools"
ET.register_namespace("android", ANDROID)
ET.register_namespace("tools", TOOLS)
A = "{" + ANDROID + "}"
T = "{" + TOOLS + "}"
# These are unnecessary for OCR, local console access or GitHub installation.
FORBIDDEN = {
    "RECORD_AUDIO", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
    "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO",
    "READ_MEDIA_AUDIO", "READ_MEDIA_VISUAL_USER_SELECTED", "READ_CONTACTS",
    "WRITE_CONTACTS", "READ_CALL_LOG", "READ_SMS", "SEND_SMS",
    "QUERY_ALL_PACKAGES", "ACCESS_BACKGROUND_LOCATION",
}
MOBILE_ONLY = {"CAMERA"}
TV_ONLY = {"PACKAGE_USAGE_STATS", "RECEIVE_BOOT_COMPLETED"}

def forbidden(variant):
    return FORBIDDEN | (TV_ONLY if variant == "mobile" else MOBILE_ONLY)

def apply_policy(root, variant):
    path = root / "app/src/main/AndroidManifest.xml"
    tree = ET.parse(path)
    manifest = tree.getroot()
    app = manifest.find("application")
    if app is None:
        raise RuntimeError("Missing application")
    app.set(A + "allowBackup", "false")
    # Remove transitive declarations too, not just the original manifest.
    for name in sorted(forbidden(variant)):
        full = "android.permission." + name
        for element in list(manifest):
            if element.tag in ("uses-permission", "uses-permission-sdk-23") and element.get(A + "name") == full:
                manifest.remove(element)
        manifest.insert(0, ET.Element("uses-permission", {A + "name": full, T + "node": "remove"}))
        manifest.insert(0, ET.Element("uses-permission-sdk-23", {A + "name": full, T + "node": "remove"}))
    for element in list(app):
        kind = element.tag
        name = element.get(A + "name", "")
        if kind == "receiver" and name.endswith(".AutoStart"):
            if variant == "mobile":
                app.remove(element)
            else:
                element.set(A + "exported", "false")
                # Only protected Android system broadcasts; no vendor spoofable action.
                for intent in element.findall("intent-filter"):
                    for action in intent.findall("action"):
                        if action.get(A + "name") == "android.intent.action.QUICKBOOT_POWERON":
                            intent.remove(action)
        elif kind in ("activity", "service", "receiver", "provider"):
            if not (kind == "activity" and name.endswith(".MainActivity")):
                element.set(A + "exported", "false")
    tree.write(path, encoding="utf-8", xml_declaration=True)

    folder = root / "app/src/main/java/cz/titulky/tv"
    auto = folder / "AutoStart.kt"
    code = auto.read_text(encoding="utf-8")
    anchor = 'getBoolean("zapnuto", true)'
    if code.count(anchor) != 1:
        raise RuntimeError("AutoStart default changed")
    auto.write_text(code.replace(anchor, 'getBoolean("zapnuto", false)'), encoding="utf-8")
    foreground = folder / "Popredi.kt"
    code = foreground.read_text(encoding="utf-8")
    anchor = "        val ted = System.currentTimeMillis()"
    if code.count(anchor) < 1:
        raise RuntimeError("Foreground usage query changed")
    # Query only on TV, and only when the OS grants usage access.
    code = code.replace(anchor, "        if (Rezim.jeTelefon(context) || !hasUsageAccess(context)) return null\n" + anchor, 1)
    anchor = "        fun maOpravneni(context: Context): Boolean = runCatching {"
    start = code.index(anchor)
    end = code.index("\n        }.getOrDefault(false)", start) + len("\n        }.getOrDefault(false)")
    code = code[:start] + '''        private fun hasUsageAccess(context: Context): Boolean = runCatching {
            val ops = context.getSystemService(android.app.AppOpsManager::class.java)
            ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

        fun maOpravneni(context: Context): Boolean =
            !Rezim.jeTelefon(context) && hasUsageAccess(context)''' + code[end:]
    # Clear any in-memory foreground cache when access is withdrawn.
    code = code.replace("if (Rezim.jeTelefon(context) || !hasUsageAccess(context)) return null",
                        "if (Rezim.jeTelefon(context) || !hasUsageAccess(context)) { posledni = null; kdyZjisteno = 0L; return null }")
    foreground.write_text(code, encoding="utf-8")

def verify_apk(apk, variant, aapt):
    output = subprocess.check_output([aapt, "dump", "permissions", str(apk)], text=True)
    actual = set(re.findall(r"uses-permission(?:-sdk-23)?: name='([^']+)'", output))
    if not actual:
        raise RuntimeError("Cannot inspect APK permissions")
    denied = {"android.permission." + name for name in forbidden(variant)}
    unexpected = sorted(actual & denied)
    if unexpected:
        raise RuntimeError("Unexpected packaged permissions: " + ", ".join(unexpected))
    required = {"INTERNET", "FOREGROUND_SERVICE", "REQUEST_INSTALL_PACKAGES"}
    required |= {"CAMERA"} if variant == "mobile" else {"SYSTEM_ALERT_WINDOW", "FOREGROUND_SERVICE_MEDIA_PROJECTION"}
    missing = {"android.permission." + name for name in required} - actual
    if missing:
        raise RuntimeError("Missing required permissions: " + ", ".join(sorted(missing)))
    print(variant, "packaged permissions:", ", ".join(sorted(actual)))

def verify_ipa(ipa):
    with zipfile.ZipFile(ipa) as z:
        info = plistlib.loads(z.read("Payload/GameLingoIOS.app/Info.plist"))
    for key in ("NSCameraUsageDescription", "NSLocalNetworkUsageDescription"):
        if not info.get(key):
            raise RuntimeError("Missing iOS usage description: " + key)
    for key in ("NSMicrophoneUsageDescription", "NSPhotoLibraryUsageDescription",
                "NSPhotoLibraryAddUsageDescription", "NSContactsUsageDescription",
                "NSLocationAlwaysUsageDescription", "NSLocationAlwaysAndWhenInUseUsageDescription",
                "NSLocationWhenInUseUsageDescription"):
        if key in info:
            raise RuntimeError("Unexpected iOS permission description: " + key)
    print("iOS camera and local-network usage descriptions verified")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("variant", choices=["mobile", "tv", "ios"])
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--apply", type=Path)
    group.add_argument("--verify", type=Path)
    parser.add_argument("--aapt")
    args = parser.parse_args()
    if args.apply:
        if args.variant == "ios":
            parser.error("iOS uses project.yml")
        apply_policy(args.apply, args.variant)
    elif args.variant == "ios":
        verify_ipa(args.verify)
    else:
        if not args.aapt:
            parser.error("--aapt required for APK")
        verify_apk(args.verify, args.variant, args.aapt)
