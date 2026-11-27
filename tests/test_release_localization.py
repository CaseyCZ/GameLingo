"""Focused source-level localization checks; native/UI QA remains required."""
from collections import Counter
import json
import subprocess
import sys
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
    return [json.loads('"' + encoded + '"') for encoded in
            re.findall(r"^\s*" + STRING + r"\s*=", text(path), re.M)]

def android_keys():
    # Both Android variants and iOS now build their UI tables from one source.
    return list(json.loads(text(ROOT / "shared" / "locales" / "cs.json")).keys())

class ReleaseLocalizationTests(unittest.TestCase):
    def test_shared_locale_catalog_and_generator(self):
        shared = ROOT / "shared" / "locales"
        manifest = json.loads(text(shared / "manifest.json"))
        self.assertEqual(manifest["source"], "cs")
        self.assertEqual(manifest["fallback"], "en")
        codes = [entry["code"] for entry in manifest["languages"]]
        self.assertEqual(len(codes), len(set(codes)))
        source_keys = set(json.loads(text(shared / "cs.json")))
        self.assertGreaterEqual(len(source_keys), 1080)
        for code in codes:
            with self.subTest(locale=code):
                entries = json.loads(text(shared / f"{code}.json"))
                self.assertEqual(set(entries), source_keys)
                self.assertTrue(all(isinstance(value, str) and value.strip()
                                    for value in entries.values()))
        result = subprocess.run([sys.executable, str(TOOLS / "generate_locales.py"), "--check"],
                                cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_new_ui_languages_are_manifest_driven_on_all_platforms(self):
        swift = text(IOS / "GameLingoAppLanguage.swift")
        android = text(TOOLS / "GameLingoLanguages.kt")
        self.assertIn("struct GLAppLanguage: RawRepresentable", swift)
        self.assertIn("supportedCodes.compactMap", swift)
        self.assertIn('if current == "cs" { return key }', swift)
        self.assertIn("value: missing, table: \"Localizable\"", swift)
        self.assertIn("BEGIN GENERATED UI LANGUAGES", swift)
        self.assertIn("BEGIN GENERATED APP LANGUAGES", android)
        self.assertIn("BEGIN GENERATED UI LABELS", android)
        self.assertIn('uiLabels["en"]?.get(cs)', android)
        project = text(IOS.parent / "project.yml")
        manifest = json.loads(text(ROOT / "shared" / "locales" / "manifest.json"))
        codes = [item["code"] for item in manifest["languages"]]
        self.assertIn("CFBundleLocalizations: [" + ", ".join(codes) + "]", project)


    def assert_unique(self, keys):
        self.assertTrue(keys)
        self.assertEqual([k for k, n in Counter(keys).items() if n > 1], [])

    def test_ios_catalog_parity_and_uniqueness(self):
        manifest = json.loads(text(ROOT / "shared" / "locales" / "manifest.json"))
        source = set(json.loads(text(ROOT / "shared" / "locales" / "cs.json")))
        for item in manifest["languages"]:
            code = item["code"]
            if code == manifest["source"]:
                continue
            with self.subTest(locale=code):
                actual = catalog_keys(IOS / (code + ".lproj") / "Localizable.strings")
                self.assert_unique(actual)
                self.assertEqual(set(actual), source)

    def test_ios_main_screen_czech_ui_literals_have_shared_translations(self):
        source = set(json.loads(text(ROOT / "shared" / "locales" / "cs.json")))
        screens = (
            "GLAutomaticZonesView.swift", "GameLingoHome.swift",
            "GameLingoTVSetupScreen.swift", "SettingsHomeView.swift",
            "SubtitleSettingsView.swift", "GameLingoDevicesPanel.swift",
            "GameLingoXboxScreen.swift", "GameLingoSetupView.swift",
            "GameLingoSubtitleAppearance.swift", "GameLingoUpdates.swift",
            "GameLingoVisualSystem.swift", "GameLingoStyle.swift",
            "GameLingoDevices.swift",
        )
        for name in screens:
            with self.subTest(screen=name):
                literals = re.findall(STRING, text(IOS / name))
                untranslated = {
                    value for value in literals
                    if re.search(r"[áčďéěíňóřšťúůýž]", value, re.I)
                    and value not in source and r"\(" not in value
                    and len(value) < 350
                }
                self.assertEqual(untranslated, set(), "Missing common UI translations")

    def test_phone_output_title_and_license_view(self):
        home = text(IOS / "GameLingoHome.swift")
        self.assertIn("name: GLInterfaceText.localized(devices.subtitleOutput.rawValue)", home)
        self.assertNotIn("name: devices.subtitleOutput.rawValue", home)
        for language, expected in (("cs", "Telefon"), ("en", "Phone"), ("sk", "Telefón")):
            entries = json.loads(text(ROOT / "shared" / "locales" / f"{language}.json"))
            self.assertEqual(entries["Telefon"], expected)
        settings = text(IOS / "SettingsHomeView.swift")
        self.assertIn("GameLingoNoticeBlocksView()", settings)
        self.assertIn("ForEach(blocks.indices", settings)
        self.assertIn("Text(verbatim: blocks[index])", settings)
        self.assertIn("offsetBy: 1400", settings)
        self.assertIn('Button("Zrušit", role: .cancel)', settings)
        about = text(TOOLS / "GameLingoAbout.kt")
        self.assertIn("takeIf { it.isNotBlank() }", about)
        self.assertIn("Licence třetích stran na GitHubu", about)

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
        editor = text(IOS / "GLAutomaticZonesView.swift")
        self.assertIn('GLNeonTitle(title: "Oblasti překladu"', editor)
        self.assertIn('GLHeader(title: "Upravit zónu"', editor)
        self.assertIn(".navigationBarTitleDisplayMode(.inline)", editor)
        self.assertIn('GLHelpButton(title: "Oblasti překladu"', editor)
        self.assertIn(".topBarTrailing", editor)
        self.assertIn(".topBarLeading", editor)
        self.assertIn("GLBackButton", editor)
        self.assertIn("model.updateAutomaticZones(zones)", editor)
        self.assertNotIn("private struct GLAutomaticZoneEditor", editor)

    def test_automatic_zone_editor_shares_one_screen_on_phone_and_tv(self):
        source = text(TOOLS / "GameLingoAutomaticConfig.kt")
        self.assertIn("fun showZones(a: Activity)", source)
        self.assertIn('GameLingoTVUi.mountPage(a, dialog, tr("Oblasti překladu"), page, helpTopic = "regions")', source)
        self.assertIn("GameLingoVisualKit.neonTitle(", source)
        self.assertIn('GameLingoVisualKit.card(a, true)', source)
        self.assertIn("val tv = GameLingoTVUi.isTV(a)", source)
        self.assertIn("android.view.MotionEvent.ACTION_MOVE", source)
        self.assertIn("bars.forEachIndexed", source)
        self.assertIn("entries.forEachIndexed", source)
        self.assertIn("save(a, entries)", source)
        self.assertNotIn('setSingleChoiceItems(labels,selected)', source.split("fun showZones(a: Activity)", 1)[1])
        self.assertNotIn('private fun edit(a: Activity,z: AutoZone', source)

    def test_preview_uses_target_language(self):
        source = text(IOS / "SubtitleSettingsView.swift")
        self.assertIn("model.tv.sendSubtitle(model.targetLanguage.sampleSubtitle,", source)
        self.assertIn("model.tv.sendChoices([model.targetLanguage.sampleSubtitle],", source)

    def test_audited_settings_and_pairing_labels_exist(self):
        keys = set(android_keys())
        expected = {
            "Jedna",
            "Barva textu na TV",
            "Originál | chybný překlad | správný překlad",
            "Jeden řádek = jedno pravidlo. Prostřední část je překlad, který chceš opravit.",
            "Vymazat historii",
            "Typ obsahu",
            "PIN",
            "Zadat IP adresu",
            "IP adresa televize",
            "Zrušit párování TV?",
            "Zkušební text",
            "OK",
            "Licence (AGPLv3)",
            "Vyhledat televizi",
            "GameLingo TV ve stejné domácí síti",
            "Zobrazí vlastní testovací text na vybraném, skutečně připojeném GameLingo přijímači.",
            "Vyber jen oblasti, které chceš překládat.",
            "Vyber zónu a přetáhni její rámeček. Změny se ukládají automaticky.",
        }
        self.assertEqual(expected - keys, set())
        self.assertIn('GLInterfaceText.localized("Verze:")', text(IOS / "GameLingoHome.swift"))
        self.assertIn('GLInterfaceText.localized(choice.title)', text(IOS / "SettingsHomeView.swift"))

    def test_console_and_tv_connection_status_keys(self):
        keys = set(android_keys())
        required = {
            "PlayStation nepřipojen",
            "Hledám PlayStation…",
            "Chybí adresa PlayStation",
            "Páruji PlayStation…",
            "PlayStation připojen",
            "Zadej přihlašovací PIN PlayStation",
            "PlayStation odpojen",
            "IP adresa konzole",
            "Nepřihlášeno",
            "Xbox účet uložen. Načti svoje konzole.",
            "Čeká se na potvrzení Microsoftu. Přihlášení lze dokončit.",
            "Čekám na návrat z přihlášení Microsoftu…",
            "Přihlašovací kód vypršel. Vyžádej nový.",
            "Čekám na potvrzení Microsoftu…",
            "Spojení s Microsoftem se přerušilo. Opakuji ověření…",
            "Přihlášeno. Načítám Xbox konzole…",
            "Čekám na dokončení přihlášení v Microsoftu…",
            "Microsoft požádal o pomalejší ověřování…",
            "Microsoft je dočasně nedostupný. Zkouším znovu…",
            "Přihlášení bylo odmítnuto. Můžeš začít znovu.",
            "Odhlášeno",
            "Už jsi přihlášený. Načti konzole.",
            "Získávám přihlašovací kód…",
            "Načítám Xbox konzole…",
            "Nenalezena žádná konzole. Zkontroluj vzdálené funkce.",
            "Kód je připraven. Přihlas se na stránce Microsoftu.",
            "Kód je připraven, ale iOS nepovolil bezpečné uložení. Nezavírej aplikaci.",
            "Televize zatím není spárovaná s iPhonem.",
            "Na Google TV otevři GameLingo TV a zvol „Překlad z telefonu“.",
            "Hledám GameLingo TV… Pokud ji nenajdu, zadej IP adresu z televize.",
            "Hledám dostupný přijímací port televize…",
            "TV neodpověděla ani po opakovaném odeslání. Na TV zvol Překlad z telefonu a zkontroluj, že je vidět platný párovací kód.",
            "TV neodpověděla. Ověř režim S mobilem, IP a síť.",
            "iPhone úspěšně spárován. Ověřuji spojení…",
            "TV zapomenuta na iPhonu. Přístup telefonu odvolej také v nastavení TV.",
            "Nejdříve vyber televizi.",
            "TV je vybraná. Pro ověřené spojení ji spáruj šestímístným kódem.",
            "Vyhledávám přijímač na TV…",
            "TV spárovaná · spojení aktivní",
            "TV spárovaná · čekám na odpověď",
            "Čekám na síť TV",
            "Nejprve vyber televizi nebo zadej její IP.",
            "Nejdříve bezpečně spáruj GameLingo TV.",
            "Nejprve vyber televizi.",
            "Titulek předán síti. Zobrazení ověř na TV.",
            "Navazuji Xbox Remote Play…",
            "Obnovení Xbox zrušeno – mění se cache",
        }
        self.assertEqual(required - keys, set())
        self.assertIn("GLInterfaceText.localizedStatus(tv.receiverStatus)", text(IOS / "GameLingoTVSetupScreen.swift"))
        self.assertIn("GLInterfaceText.localizedStatus(tv.message)", text(IOS / "GameLingoTVSetupScreen.swift"))
        self.assertIn("GLInterfaceText.localizedStatus(ps.status)", text(IOS / "PlayStation/GameLingoPlayStationSetup.swift"))
        self.assertIn("GLInterfaceText.localizedStatus(auth.status)", text(IOS / "PlayCaptionIOSApp.swift"))
        self.assertIn("GLInterfaceText.localizedStatus(model.status)", text(IOS / "GameLingoHome.swift"))

    def test_setup_action_and_diagnostics_dynamic_labels_are_localized(self):
        setup = text(IOS / "GameLingoSetupView.swift")
        self.assertNotIn("Text(language.title)", setup)
        self.assertNotIn("Text(devices.subtitleOutput.rawValue)", setup)
        # Empty TV cards were removed from first-run; keep the real device name verbatim.
        self.assertIn("if let television = devices.chosenTelevision", setup)
        self.assertIn("Text(verbatim: television.name)", setup)
        self.assertIn('GLPrimaryButton(title: "Spárovat TV"', setup)
        self.assertNotIn('GLInterfaceText.localized("Vyber nebo přidej TV")', setup)
        self.assertIn('GLInterfaceText.localized("Nevybráno")', setup)
        home = text(IOS / "GameLingoHome.swift")
        self.assertIn('Text(LocalizedStringKey(model.isRunning ? "Zastavit překlad" : "Spustit překlad"))', home)
        settings = text(IOS / "SettingsHomeView.swift")
        self.assertIn("GLInterfaceText.localized(model.cacheStatus)", settings)
        self.assertIn("GLInterfaceText.localizedDiagnostics(model.performanceSummary)", settings)
        for value in ("model.tv.receiverStatus", "model.status"):
            self.assertIn(f"GLInterfaceText.localizedStatus({value})", settings)
        self.assertIn('GameLingoHelp.newsButton(activity)', text(TOOLS / "GameLingoVisualKit.kt"))
        self.assertIn('GLRocketIcon()', home)
        self.assertIn('GameLingoHelp.report(a)', text(TOOLS / "GameLingoTVSettings.kt"))

    def test_dynamic_status_templates_exist_in_every_locale(self):
        shared = ROOT / "shared" / "locales"
        manifest = json.loads(text(shared / "manifest.json"))
        helper = text(IOS / "GameLingoAppLanguage.swift")
        templates = re.findall(STRING, helper.split("private static let statusTemplates = [", 1)[1].split("]", 1)[0])
        self.assertGreaterEqual(len(templates), 18)
        for language in manifest["languages"]:
            catalog = json.loads(text(shared / (language["code"] + ".json")))
            for template in templates:
                self.assertEqual(catalog[template].count("%@"), 1, template)

    def test_diagnostic_fields_and_glossary_example(self):
        keys = set(android_keys())
        self.assertEqual({
            "Průměr / P95 (posledních 128 vzorků)",
            "Dialog čekání snímku",
            "Dialog OCR",
            "Dialog čekání po OCR",
            "Dialog překlad",
            "Dialog celkem k výstupu",
            "Herní text čekání snímku",
            "Herní text OCR",
            "Herní text překlad",
            "Herní text celkem k výstupu",
            "dlouhé čekání ≥ 5 s",
            "Chyby překladu",
            "Xbox dekódovaný obraz",
            "bez nového snímku ≥ 5 s",
            "čekám na první snímek",
            "Xbox síť",
            "nedostupná",
            "Xbox síť (video)",
            "ztráta",
            "paketů",
            "Paměť",
            "RSS nedostupná",
            "teplotní stav",
            "nominal",
            "fair",
            "serious",
            "critical",
            "unknown",
            "Příklad pravidla (angličtina → čeština)",
        } - keys, set())
        glossary = text(TOOLS / "GameLingoGlossaryUi.kt")
        self.assertIn('hint = GameLingoLanguages.ui(a, "Originál | chybný překlad | správný překlad")', glossary)
        self.assertEqual(glossary.count("Save game | Zachránit hru | Uložit hru"), 1)
        self.assertNotIn("val header = LinearLayout(activity)", text(TOOLS / "GameLingoSubtitleAppearance.kt"))
        self.assertIn("GameLingoTVUi.mountPage(", text(TOOLS / "GameLingoSubtitleAppearance.kt"))

if __name__ == "__main__":
    unittest.main()
