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
        self.assertIn('tag="gamelingo-title"', visual)

    def test_tv_menu_uses_one_scale_and_shared_about_page(self):
        ui = text(TOOLS / "GameLingoTVUi.kt")
        kit = text(TOOLS / "GameLingoVisualKit.kt")
        about = text(TOOLS / "GameLingoAbout.kt")
        self.assertIn("if (isTV(c)) tvScale(c) else 1f", ui)
        self.assertIn("private const val TV_SCALE = 0.66f", ui)
        self.assertIn("density * value * GameLingoTVUi.menuScale(c)", kit)
        self.assertIn("value * GameLingoTVUi.menuScale(c)", kit)
        self.assertIn('GameLingoTVUi.page(activity, GameLingoLanguages.ui(activity, "O aplikaci / Licence"), content(activity))', about)
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

    def test_preview_uses_target_language(self):
        source = text(IOS / "SubtitleSettingsView.swift")
        self.assertIn("model.tv.sendSubtitle(model.targetLanguage.sampleSubtitle,", source)
        self.assertIn("model.tv.sendChoices([model.targetLanguage.sampleSubtitle],", source)

if __name__ == "__main__":
    unittest.main()
