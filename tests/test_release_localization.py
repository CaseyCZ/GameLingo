"""Focused source-level localization checks; native/UI QA remains required."""
from collections import Counter
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
IOS = ROOT / "ios" / "GameLingoIOS" / "App"
TOOLS = ROOT / "tools"
STRING = r'"((?:\\.|[^"\\])*)"'

def text(path):
    return path.read_text(encoding="utf-8")

def catalog_keys(path):
    return re.findall(r"^\s*" + STRING + r"\s*=", text(path), re.M)

def android_keys():
    return re.findall(r"^\s*" + STRING + r"\s+to Pair\(", text(TOOLS / "GameLingoLanguages.kt"), re.M)

class ReleaseLocalizationTests(unittest.TestCase):
    def assert_unique(self, keys):
        self.assertTrue(keys)
        self.assertEqual([k for k, n in Counter(keys).items() if n > 1], [])

    def test_ios_catalog_parity_and_uniqueness(self):
        en = catalog_keys(IOS / "en.lproj" / "Localizable.strings")
        sk = catalog_keys(IOS / "sk.lproj" / "Localizable.strings")
        self.assert_unique(en)
        self.assert_unique(sk)
        self.assertEqual(set(en), set(sk))

    def test_ios_explicit_dynamic_localization_keys_exist(self):
        keys = set(catalog_keys(IOS / "en.lproj" / "Localizable.strings"))
        for name in ("GameLingoDevicesPanel.swift", "GameLingoXboxScreen.swift",
                     "SettingsHomeView.swift", "SubtitleSettingsView.swift"):
            with self.subTest(file=name):
                found = re.findall(r"GLInterfaceText\.localized\(\s*" + STRING, text(IOS / name))
                self.assertTrue(found)
                self.assertEqual(set(found) - keys, set())

    def test_android_catalog_and_audited_ui_calls(self):
        keys = android_keys()
        self.assert_unique(keys)
        for name in ("redesign_gamelingo.py", "polish_all_screens.py",
                     "GameLingoTVSettings.kt", "GameLingoScreenTranslationService.kt",
                     "direct_tv_mode.py", "tv_navigation.py"):
            with self.subTest(file=name):
                found = re.findall(
                    r"GameLingoLanguages\.ui\(\s*(?:this(?:@MainActivity)?|a|activity|c|context)\s*,\s*" + STRING,
                    text(TOOLS / name),
                )
                self.assertTrue(found)
                self.assertEqual(set(found) - set(keys), set())

    def test_tv_dashboard_refresh_does_not_rebuild_dialog(self):
        settings = text(TOOLS / "GameLingoTVSettings.kt")
        visual = text(TOOLS / "GameLingoVisualKit.kt")
        self.assertIn("onChanged = ::refreshLanguage", settings)
        refresh = settings.split("fun refreshLanguage()", 1)[1].split("fun tile(", 1)[0]
        self.assertIn('findViewWithTag<TextView>("gamelingo-title")', refresh)
        self.assertIn('findViewWithTag<TextView>("gamelingo-detail")', refresh)
        self.assertNotIn("dialog.dismiss()", refresh)
        self.assertRegex(visual, r'tag\s*=\s*"gamelingo-title"')

    def test_tv_menu_uses_one_scale_and_shared_about_page(self):
        ui = text(TOOLS / "GameLingoTVUi.kt")
        kit = text(TOOLS / "GameLingoVisualKit.kt")
        about = text(TOOLS / "GameLingoAbout.kt")
        self.assertIn("if (isTV(c)) tvScale(c) else 1f", ui)
        self.assertIn("private const val TV_SCALE = 0.66f", ui)
        self.assertIn("density * value * GameLingoTVUi.menuScale(c)", kit)
        self.assertIn("value * GameLingoTVUi.menuScale(c)", kit)
        self.assertIn('GameLingoTVUi.page(activity, GameLingoLanguages.ui(activity, "O aplikaci / Licence"), content(activity), helpTopic = "about")', about)
        self.assertIn("textSize = GameLingoVisualKit.sp(activity, size)", about)

    def test_tv_menu_size_is_persistent_and_separate_from_ocr(self):
        ui = text(TOOLS / "GameLingoTVUi.kt")
        settings = text(TOOLS / "GameLingoTVSettings.kt")
        direct = text(TOOLS / "direct_tv_mode.py")
        self.assertIn("listOf(80, 90, 100, 110, 120)", ui)
        self.assertIn('getInt(MENU_PERCENT, 100)', ui)
        self.assertIn("if (value in menuSizes) value else 100", ui)
        self.assertIn("if (!isTV(c) || value !in menuSizes) return", ui)
        self.assertIn("putInt(MENU_PERCENT, value).apply()", ui)
        self.assertIn("GameLingoTVUi.setMenuPercent(a, next)", settings)
        self.assertIn("reopeningForScale = true", settings)
        self.assertIn("private val SKALA: Float get() = GameLingoTVUi.menuScale(this)", direct)
        self.assertNotIn("a.recreate()", settings)

    def test_tv_shared_header_focus_and_compact_about(self):
        ui = text(TOOLS / "GameLingoTVUi.kt")
        kit = text(TOOLS / "GameLingoVisualKit.kt")
        about = text(TOOLS / "GameLingoAbout.kt")
        dashboard = text(TOOLS / "GameLingoTVSettings.kt")
        header = ui.split("fun header(", 1)[1].split("private class MenuFocusOutline", 1)[0]
        self.assertLess(header.index("slot(back, Gravity.START)"), header.index("addView(heading"))
        self.assertLess(header.index("addView(heading"), header.index("slot(help, Gravity.END)"))
        self.assertIn("state_focused", ui)
        self.assertIn("view.foreground = if (previous == null) outline", ui)
        self.assertIn("GameLingoTVUi.controlFocus(activity, row)", kit)
        self.assertIn("GameLingoTVUi.header(a, heading, back, help)", dashboard)
        self.assertIn("(listOf(license) + sources).chunked(2)", about)
        self.assertIn("GameLingoTVUi.page(activity, tr(\"Licence třetích stran\"), body)", about)

    def test_android_tv_and_phone_settings_mount_fixed_navigation(self):
        for module in ("GameLingoSettings.kt", "GameLingoOCRSettings.kt",
                       "GameLingoLanguages.kt", "GameLingoDeviceCenter.kt", "GameLingoRegions.kt",
                       "GameLingoProfiles.kt"):
            with self.subTest(module=module):
                self.assertIn("GameLingoTVUi.mountPage(", text(TOOLS / module))
        ui = text(TOOLS / "GameLingoTVUi.kt")
        self.assertIn("return mountPage(a, dialog, heading, body, scroll, helpTopic, onClose)", ui)
        self.assertIn("root.addView(top", ui)
        self.assertIn("root.addView(content", ui)

    def test_ios_modal_navigation_uses_leading_back(self):
        style = text(IOS / "GameLingoStyle.swift")
        self.assertIn("struct GLBackButton: View", style)
        self.assertIn('Label("Zpět", systemImage: "chevron.left")', style)
        for path in ("SettingsHomeView.swift", "SubtitleSettingsView.swift",
                     "GameLingoXboxScreen.swift", "GameLingoTVSetupScreen.swift",
                     "PlayStation/GameLingoPlayStationSetup.swift", "GLAutomaticZonesView.swift"):
            with self.subTest(path=path):
                source = text(IOS / path)
                self.assertIn("GLBackButton", source)
                self.assertIn(".topBarLeading", source)

    def test_shared_header_respects_phone_width_and_touch_navigation(self):
        ui = text(TOOLS / "GameLingoTVUi.kt")
        self.assertIn("if (television) dp(a, 132) else GameLingoVisualKit.dp(a, 48)", ui)
        self.assertIn("back.contentDescription = back.text", ui)
        self.assertIn("back.minimumHeight = side", ui)
        self.assertIn("help.minimumHeight = side", ui)
        self.assertIn("if (isTV(a)) body.post", ui)

    def test_profile_page_and_ios_zone_editor_share_navigation(self):
        phone = text(TOOLS / "GameLingoProfiles.kt")
        self.assertIn('GameLingoTVUi.mountPage(activity, dialog, GameLingoLanguages.ui(activity, "Profily her"), outer, helpTopic = "profiles")', phone)
        self.assertNotIn('dialog.setContentView(scroll)', phone)
        self.assertIn('GameLingoLanguages.ui(activity, "Profil nelze načíst")', phone)
        editor = text(IOS / "GLAutomaticZonesView.swift").split("private struct GLAutomaticZoneEditor", 1)[1]
        self.assertIn(".navigationBarTitleDisplayMode(.inline)", editor)
        self.assertIn('GLHelpButton(title: "Upravit zónu"', editor)
        self.assertIn(".topBarTrailing", editor)
        self.assertIn("save(zone)", editor)
        self.assertNotIn("GLBackButton", editor)  # Native push back must remain intact.

    def test_preview_uses_target_language(self):
        source = text(IOS / "SubtitleSettingsView.swift")
        self.assertIn("model.tv.sendSubtitle(model.targetLanguage.sampleSubtitle,", source)
        self.assertIn("model.tv.sendChoices([model.targetLanguage.sampleSubtitle],", source)

if __name__ == "__main__":
    unittest.main()
