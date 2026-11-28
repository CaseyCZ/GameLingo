#!/usr/bin/env python3
"""Prepare an isolated store workspace. Does not authorize or publish an app."""
import argparse
import plistlib
import re
from pathlib import Path
import xml.etree.ElementTree as ET

from permission_policy import A, T, ANDROID, TOOLS

def once(text, old, new=""):
    if text.count(old) != 1:
        raise RuntimeError("Unexpected distribution anchor: " + old[:90])
    return text.replace(old, new, 1)

MOBILE_BUTTON = '''        page.addView(GameLingoTVUi.action(activity, GameLingoLanguages.ui(activity, "Zkontrolovat aktualizace")) {
            GameLingoTVUpdater.show(activity)
        })
'''
TV_BUTTON = '''        page.addView(GameLingoTVUi.action(activity, GameLingoLanguages.ui(activity, "Zkontrolovat aktualizace")) {
            GameLingoTVUpdater.show(activity)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
'''
STARTUP = "        GameLingoTVUpdater.checkAtStartup(this)\n"
INSTALL_PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"

def prepare_android(root, variant):
    """Apply only after the ordinary branding/permission patches in a disposable copy."""
    src = root / "app/src/main"
    folder = src / "java/cz/titulky/tv"
    manifest_path = src / "AndroidManifest.xml"
    about = folder / "GameLingoAbout.kt"
    main = folder / "MainActivity.kt"
    updater = folder / "GameLingoTVUpdater.kt"
    paths = src / "res/xml/gamelingo_update_paths.xml"
    about_text = once(about.read_text(encoding="utf-8"),
                      MOBILE_BUTTON if variant == "mobile" else TV_BUTTON)
    about_text = once(about_text, '        page.addView(link("Historie vydání", "https://github.com/CaseyCZ/GameLingo/releases"))\n')
    main_text = once(main.read_text(encoding="utf-8"), STARTUP)
    if not updater.is_file() or not paths.is_file():
        raise RuntimeError("Expected GitHub update sources before conversion")
    manifest = ET.parse(manifest_path)
    app = manifest.getroot().find("application")
    if app is None:
        raise RuntimeError("Missing app")
    for node in list(manifest.getroot()):
        if node.tag in ("uses-permission", "uses-permission-sdk-23") and node.get(A + "name") == INSTALL_PERMISSION:
            manifest.getroot().remove(node)
    for tag in ("uses-permission", "uses-permission-sdk-23"):
        manifest.getroot().insert(0, ET.Element(tag, {A + "name": INSTALL_PERMISSION, T + "node": "remove"}))
    for node in list(app):
        if node.tag == "receiver" and node.get(A + "name", "").endswith(".GameLingoInstallReceiver"):
            app.remove(node)
        elif node.tag == "provider" and node.get(A + "authorities") == "${applicationId}.updates":
            app.remove(node)
    # Verify references before writing. The install implementation is not shipped.
    for file in src.rglob("*.kt"):
        if file == updater:
            continue
        text = about_text if file == about else main_text if file == main else file.read_text(encoding="utf-8")
        if re.search(r"\b(?:GameLingoTVUpdater|GameLingoInstallReceiver)\b", text):
            raise RuntimeError("Unexpected self-update reference: " + str(file.relative_to(root)))
    ET.register_namespace("android", ANDROID)
    ET.register_namespace("tools", TOOLS)
    manifest.write(manifest_path, encoding="utf-8", xml_declaration=True)
    about.write_text(about_text, encoding="utf-8")
    main.write_text(main_text, encoding="utf-8")
    updater.unlink()
    paths.unlink()

# Only version and inert compatibility views are kept; the IPA downloader,
# release API, sharing and signing instructions are absent from the store source.
IOS_STORE_UPDATE = '''import SwiftUI
import Foundation

@MainActor
final class GameLingoUpdateModel: ObservableObject {
    @Published private(set) var available: String? = nil
    @Published private(set) var busy = false
    static var currentVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }
    func checkAtStartup() {}
    func download() {}
}

struct GameLingoUpdateOffer: View {
    let version: String
    let onLater: () -> Void
    let onDownload: () -> Void
    var body: some View { EmptyView() }
}

struct GameLingoUpdatePanel: View {
    @ObservedObject var updates: GameLingoUpdateModel
    var running: Bool = false
    var body: some View { EmptyView() }
}
'''

def prepare_ios(root):
    path = root / "ios/GameLingoIOS/App/GameLingoUpdates.swift"
    if "private enum IPAUpdates" not in path.read_text(encoding="utf-8"):
        raise RuntimeError("Expected unmodified GitHub updater before conversion")
    path.write_text(IOS_STORE_UPDATE, encoding="utf-8")

def verify_privacy_manifest(data):
    manifest = plistlib.loads(data)
    reasons = {item["NSPrivacyAccessedAPIType"]: set(item["NSPrivacyAccessedAPITypeReasons"])
               for item in manifest["NSPrivacyAccessedAPITypes"]}
    for category, reason in (("UserDefaults", "CA92.1"), ("SystemBootTime", "35F9.1")):
        if reason not in reasons.get("NSPrivacyAccessedAPICategory" + category, set()):
            raise RuntimeError("Missing reviewed required-reason API: " + category)
    return manifest

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("variant", choices=("mobile", "tv", "ios"))
    parser.add_argument("workspace", type=Path)
    parser.add_argument("--isolated-copy", action="store_true", required=True,
                        help="Confirm this is a disposable generated workspace, not the main checkout")
    args = parser.parse_args()
    if args.variant == "ios":
        prepare_ios(args.workspace)
    else:
        prepare_android(args.workspace, args.variant)
    print("Store updater removed. Licensing, permissions, bundle signing and store validation remain required.")
