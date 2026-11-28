import plistlib
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from store_distribution import (prepare_android, prepare_ios, MOBILE_BUTTON, TV_BUTTON,
                                STARTUP, verify_privacy_manifest)
from permission_policy import A, T

class StoreDistributionTests(unittest.TestCase):
    def test_android_removes_install_implementation_and_permission(self):
        for variant, button in (("mobile", MOBILE_BUTTON), ("tv", TV_BUTTON)):
            with self.subTest(variant=variant), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                src = root / "app/src/main"
                folder = src / "java/cz/titulky/tv"
                folder.mkdir(parents=True)
                (folder / "GameLingoAbout.kt").write_text(button +
                    '        page.addView(link("Historie vydání", "https://github.com/CaseyCZ/GameLingo/releases"))\n')
                (folder / "MainActivity.kt").write_text(STARTUP + "keep\n")
                (folder / "GameLingoTVUpdater.kt").write_text("install code")
                paths = src / "res/xml/gamelingo_update_paths.xml"
                paths.parent.mkdir(parents=True)
                paths.write_text("<paths/>")
                manifest = src / "AndroidManifest.xml"
                manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                  <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
                  <application>
                  <receiver android:name=".GameLingoInstallReceiver"/>
                  <provider android:authorities="${applicationId}.updates"/>
                  <service android:name=".Keep"/>
                  </application></manifest>''')
                prepare_android(root, variant)
                self.assertFalse((folder / "GameLingoTVUpdater.kt").exists())
                self.assertFalse(paths.exists())
                self.assertEqual((folder / "MainActivity.kt").read_text(), "keep\n")
                result = ET.parse(manifest).getroot()
                self.assertEqual(result.find("uses-permission").get(T+"node"), "remove")
                self.assertIsNone(result.find("application/receiver"))
                self.assertIsNone(result.find("application/provider"))
                self.assertEqual(result.find("application/service").get(A+"name"), ".Keep")

    def test_ios_removes_ipa_download_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            file = root / "ios/GameLingoIOS/App/GameLingoUpdates.swift"
            file.parent.mkdir(parents=True)
            file.write_text("private enum IPAUpdates { /* GitHub API */ }")
            prepare_ios(root)
            text = file.read_text()
            self.assertNotIn("IPAUpdates", text)
            self.assertNotIn("https://", text)
            self.assertIn("currentVersion", text)

    def test_required_reason_declarations(self):
        path = ROOT / "ios/GameLingoIOS/App/PrivacyInfo.xcprivacy"
        verify_privacy_manifest(path.read_bytes())
        with self.assertRaises(RuntimeError):
            verify_privacy_manifest(plistlib.dumps({"NSPrivacyAccessedAPITypes": []}))

if __name__ == "__main__":
    unittest.main()
