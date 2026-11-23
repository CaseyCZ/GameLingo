#!/usr/bin/env python3
"""GameLingo v0.5 structural regression checks.

These are static contract checks and DO NOT substitute physical PS4/PS5, Xbox,
iPhone, Android or Google TV integration tests.
"""
from pathlib import Path
import json
import subprocess
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
IOS=ROOT/"ios/GameLingoIOS/App"
TOOLS=ROOT/"tools"

def code(path):
    return path.read_text(encoding="utf-8")

class GameLingoIntegrationContracts(unittest.TestCase):
    def test_first_run_and_device_library(self):
        home=code(IOS/"GameLingoHome.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        devices=code(IOS/"GameLingoDevices.swift")
        panel=code(IOS/"GameLingoDevicesPanel.swift")
        for label in ('GameLingoSetupView', 'GameLingoDevicesPanel',
                      'openSelectedConsole', 'startCurrentSource'):
            self.assertIn(label,home)
        contract=code(ROOT/"shared/mobile-ui.json")
        for label in ('Vyber konzoli', 'Kam zobrazit titulky?'):
            self.assertIn(label,contract)
        for label in ('PlayStation', 'Xbox', 'Spárovat TV'):
            self.assertIn(label,setup)
        self.assertIn("GameLingoUIContract.setupSteps[step]", setup)
        for label in ('addXbox', 'refreshPlayStation', 'removeConsole',
                      'removeTelevision', 'preferredConsoleID',
                      'preferredTelevisionID'):
            self.assertIn(label,devices+panel)

    def test_single_xbox_picker_and_no_legacy_ios_dashboard(self):
        home=code(IOS/"GameLingoHome.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        device_ui=code(IOS/"GameLingoXboxScreen.swift")
        model=code(IOS/"PlayCaptionIOSApp.swift")
        for entry in (home, setup):
            self.assertIn('GameLingoXboxScreen(devices: devices, auth: model.xbox)', entry)
            self.assertNotIn("XboxRemotePlayView", entry)
        self.assertIn("devices.addXbox(console)", device_ui)
        self.assertIn("devices.preferredConsoleID", device_ui)
        self.assertIn("XboxAccountView(auth: auth)", device_ui)
        self.assertNotIn("struct ContentView: View", model)
        self.assertNotIn("struct XboxRemotePlayView: View", model)
        self.assertNotIn("private struct PlayCaptionSection", model)
        settings=code(IOS/"SettingsHomeView.swift")
        self.assertNotIn("PlayCaptionSettingsView",settings+home)
        self.assertIn("GameLingoSettingsView",settings+home)
        self.assertIn("struct XboxAccountView: View", model)
        self.assertNotIn("struct TVSelectionView: View", model)

    def test_live_session_cannot_silently_change_selected_console(self):
        panel=code(IOS/"GameLingoDevicesPanel.swift")
        home=code(IOS/"GameLingoHome.swift")
        self.assertIn('if !model.isRunning { devices.selectConsole(console.id) }', panel)
        self.assertIn('guard !model.isRunning else { pendingRemove = nil; return }', panel)
        self.assertIn('.disabled(model.isRunning)', panel)
        self.assertIn('"Nastaveno"', home)
        self.assertIn('"Snímání aktivní"', home)

    def test_one_tv_setup_persists_selected_receiver_for_translation(self):
        home=code(IOS/"GameLingoHome.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        shared=code(IOS/"GameLingoTVSetupScreen.swift")
        for entry in (home, setup):
            self.assertIn("GameLingoTVSetupScreen(devices: devices, tv: model.tv)",entry)
        self.assertIn("GameLingoTVPairingPanel(tv: tv, isRunning: model.isRunning)",shared)
        self.assertIn("tv.pairReceiver()",shared)
        self.assertIn("tv.forgetReceiver()",shared)
        self.assertIn("devices.addTelevision(",shared)
        self.assertIn('devices.selectTelevision("tv:" + address.lowercased())',shared)
        self.assertIn("devices.subtitleOutput = .googleTV",shared)
        self.assertIn("devices.prepareTV(tv)",shared)
        self.assertIn("tv.receiverPaired",shared)
        self.assertIn("tv.receiverConnected",shared)
        self.assertIn("Uložení televizoru neznamená úspěšné spárování",shared)
        self.assertNotIn('TVSelectionView(tv: model.tv)',home)
        self.assertNotIn('TVSelectionView(tv: model.tv)',setup)

    def test_actual_playstation_not_placeholder(self):
        ps=code(IOS/"PlayStation/GameLingoPlayStation.swift")
        core=code(IOS/"PlayStation/RemotePlaySession.swift")
        model=code(IOS/"PlayCaptionIOSApp.swift")
        native=code(IOS/"PlayStation/TPPlayCore.c")
        for label in ('RemotePlaySession(', 'onDecodedFrame:', 'tp_play_session_create'):
            self.assertIn(label,ps+core+native)
        self.assertIn('func start(_ console: RegisteredConsole)',model)
        self.assertIn('func start(_ console: XboxConsole)',model)
        self.assertIn('private func processFrame(',model)
        self.assertIn('playStation.start(console:',model)
        self.assertIn('onEnded:',model)
        self.assertIn('needsLoginPIN',ps)
        self.assertIn('submitLoginPIN(',ps)

    def test_live_connection_state_is_backed_by_real_frames(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        home=code(IOS/"GameLingoHome.swift")
        self.assertIn("model.isRunning && model.frameCount > 0",home)
        self.assertIn('model.frameCount > 0 ? "Snímání aktivní" : "Připojuji…"',home)
        xbox=model.split("func start(_ console: XboxConsole)",1)[1]
        xbox=xbox.split("private func processFrame",1)[0]
        self.assertIn("frameCount = 0",xbox)
        self.assertIn("guard self.sessionRevision == currentSession else { return }",xbox)
        self.assertIn("stop()\n                self.status = failure",xbox)
        ps=model.split("func start(_ console: RegisteredConsole)",1)[1]
        self.assertIn("frameCount = 0",ps)

    def test_async_ocr_tasks_cannot_leak_between_remote_play_sessions(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        options=model.split("guard let englishChoices = await",1)[1]
        options=options.split("self.lastTranslatedChoiceItems = translated",1)[0]
        self.assertIn("self.sessionRevision == currentSession",options)
        self.assertIn("defer {\n                            if self.sessionRevision == currentSession",model)
        self.assertIn("self.processingChoices = false",model)
        self.assertIn("if self.sessionRevision == currentSession",model)
        stopped=model.split("func stop() {",1)[1]
        self.assertIn("processingChoices = false",stopped)
        self.assertIn("processingOCR = false",stopped)

    def test_four_regions_and_output_selection(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        edit=code(IOS/"SubtitleSettingsView.swift")
        for name in ('manualOCRBox','choicesArea','choicesOutputArea',
                     'outputFont','outputWidth','choicesOutputFont'):
            self.assertIn(name,model)
        for color in ('Modrá','Zelená','Žlutá','Oranžová'):
            self.assertIn(color,edit)
        self.assertIn('subtitleOutput == .phone',model)
        self.assertIn('model.lastChoices',code(IOS/"GameLingoHome.swift"))

    def test_subtitles_go_only_to_selected_output_and_television(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        home=code(IOS/"GameLingoHome.swift")
        relocation=model.split("func relocateActiveChoices() -> Bool", 1)[1]
        relocation=relocation.split("func stop()", 1)[0]
        self.assertIn("verifiedTelevisionOutput", relocation)
        self.assertIn("let selected = devices.chosenTelevision", model)
        self.assertIn("tv.receiverPaired && tv.receiverConnected", model)
        self.assertIn("selected.ipAddress", model)
        self.assertIn("devices.prepareTV(tv)", relocation)
        self.assertIn("tv.sendChoices(", relocation)
        self.assertIn(".onChange(of: devices.preferredTelevisionID)", home)
        self.assertIn(".onChange(of: devices.subtitleOutput)", home)
        self.assertIn("selectedTV.ipAddress", home)
        self.assertIn("previous == .googleTV && output == .phone", home)
        self.assertIn("model.tv.clearOverlays(at: previousIP)",home)
        tv=code(IOS/"PlayCaptionTV.swift")
        self.assertIn("clearOverlays(at: oldValue)",tv)
        self.assertIn("func clearOverlays(at address: String)",tv)
        self.assertIn("forceDatagram: true",tv)
        clear=tv.split("func clearOverlays(at address: String)",1)[1]
        clear=clear.split("func sendSubtitle(",1)[0]
        self.assertIn("repeatTask?.cancel()",clear)
        self.assertIn("repeatTask = nil",clear)
        self.assertIn('let choices = Data("PCV1',tv)
        self.assertIn('let dialogue = Data([',tv)
        self.assertIn("devices.prepareTV(model.tv)", home)
        self.assertIn("GameLingo iOS 0.5.0", model)

    def test_both_translation_lanes_require_verified_tv_receiver(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        self.assertIn("private var verifiedTelevisionOutput: Bool",model)
        self.assertIn("tv.receiverPaired && tv.receiverConnected",model)
        self.assertIn("if self.verifiedTelevisionOutput {\n                                self.tv.sendChoices",model)
        self.assertIn("if self.verifiedTelevisionOutput {\n                                    self.tv.sendSubtitle",model)

    def test_android_and_google_tv_supported(self):
        manager=code(TOOLS/"GameLingoDeviceCenter.kt")
        psstore=code(TOOLS/"GameLingoPlayStationConsoles.kt")
        multi=code(TOOLS/"android_multi_xbox.py")
        patches=code(TOOLS/"wire_android_devices.py")
        tv=code(TOOLS/"polish_all_screens.py")
        receiver=code(TOOLS/"redesign_gamelingo.py")
        self.assertIn('onPlayStation',manager)
        self.assertIn('onXbox',manager)
        self.assertIn('onTV',manager)
        first_run=code(TOOLS/"GameLingoFirstRun.kt")
        self.assertIn('GameLingoFirstRun.show',patches)
        for step in ('Nastavit později', 'connectPlayStation()', 'connectTV()',
                     'GameLingoUIContract.setupSteps[step]'):
            self.assertIn(step,first_run)
        self.assertIn('Vyber konzoli',code(ROOT/"shared/mobile-ui.json"))
        self.assertIn('AES/GCM/NoPadding',psstore)
        self.assertIn('setSingleChoiceItems',multi)
        self.assertIn('if (predvybrany >= 0)',multi)
        self.assertIn('zvolXbox(nalezene[predvybrany])',multi)
        self.assertIn('tv_dashboard',patches)
        self.assertIn('Překlad z telefonu',tv)
        self.assertIn('IPhoneTVPairing',receiver)

    def test_onboarding_completion_and_removed_device_regressions(self):
        setup=code(IOS/"GameLingoSetupView.swift")
        devices=code(IOS/"GameLingoDevices.swift")
        ps_setup=code(IOS/"PlayStation/GameLingoPlayStationSetup.swift")
        android=code(TOOLS/"GameLingoFirstRun.kt")
        for name in ("televisionPaired", "canFinish", "finishSetup()",
                     "Nastavit později", "model.playStation.registrations.$consoles"):
            self.assertIn(name,setup)
        self.assertIn("guard !hiddenConsoleIDs.contains(id)",devices)
        self.assertIn("hiddenConsoleIDs.remove(id)",devices)
        self.assertIn("placeholderID",devices)
        self.assertIn('defaults.removeObject(forKey: "tvIP")',devices)
        self.assertIn("addRegisteredPlayStation(console)",ps_setup)
        self.assertIn("Nastavit později",android)
        self.assertIn("Uložené zařízení neznamená aktivní Remote Play",android)
        self.assertIn("Theme_Material_NoActionBar_Fullscreen",android)
        self.assertIn("ViewGroup.LayoutParams.MATCH_PARENT",android)

    def test_mobile_parity_does_not_fabricate_connected_state(self):
        android=code(TOOLS/"redesign_gamelingo.py")
        first_run=code(TOOLS/"GameLingoFirstRun.kt")
        ios_setup=code(IOS/"GameLingoSetupView.swift")
        self.assertIn("4 -> consoleRegistered() && televisionPaired()", first_run)
        self.assertIn("GameLingoTVPairing.paired(activity, selected.ip)",first_run)
        self.assertIn("isEnabled = canContinue", first_run)
        self.assertIn("disabled: !canFinish", ios_setup)
        self.assertIn("Konzole je nastavená · připojení neověřeno", android)
        self.assertIn("Uložené zařízení samo o sobě nepotvrzuje živé spojení.", android)
        self.assertIn("RemotePlayService.prehledSpojeni().uroven == UrovenSpojeni.OK", android)
        self.assertIn("liveImage && tvStore.pouzeTelefon", android)
        self.assertIn("Uložený televizor nepotvrzuje živé přijetí titulků.", android)

    def test_five_destinations_native_android_navigation(self):
        nav=code(TOOLS/"GameLingoNavigation.kt")
        mobile=code(TOOLS/"redesign_gamelingo.py")
        wiring=code(TOOLS/"wire_android_devices.py")
        about=code(TOOLS/"GameLingoAbout.kt")
        contract=json.loads(code(ROOT/"shared/mobile-ui.json"))
        self.assertEqual([t["title"] for t in contract["tabs"]],
            ["Domů", "Obrazovka", "Konzole", "Nastavení", "O aplikaci"])
        self.assertIn("GameLingoUIContract.tabs",nav)
        self.assertIn("GameLingoUIContract.tabs[rawValue]",code(IOS/"GameLingoHome.swift"))
        self.assertIn("onSettings: () -> Unit",nav)
        self.assertIn("GameLingoNavigation.create(",mobile)
        self.assertIn('onDevices = { openDevices() }',mobile)
        self.assertIn('onSettings = { openTab(3) }',mobile)
        self.assertIn('pageHost.removeAllViews()',mobile)
        self.assertIn('GameLingoDeviceCenter.content(this,',mobile)
        self.assertIn('GameLingoSettings.content(this, nast,',mobile)
        self.assertIn('fun openDevices() {',mobile)
        self.assertIn('GameLingoVisualKit.brandHeader(this)',mobile)
        self.assertIn('onAbout = { openTab(4) }',mobile)
        self.assertIn('GameLingoNavigation.kt',wiring)
        self.assertIn('GameLingoAbout.kt',wiring)
        self.assertIn("'onDevices = { openDevices() }'",wiring)
        self.assertIn("AGPL-3.0",about)

    def test_shared_mobile_contract_and_real_screens(self):
        spec=json.loads(code(ROOT/"shared/mobile-ui.json"))
        self.assertEqual(len(spec["setupSteps"]), 5)
        self.assertEqual(len(spec["settingsSections"]), 4)
        self.assertEqual(spec["settingsSections"][-1], "OBECNÉ")
        self.assertIn("GameLingoUIContract.setupSteps[step]",
                      code(TOOLS/"GameLingoFirstRun.kt"))
        self.assertIn("GameLingoUIContract.settingsSections[3]",
                      code(TOOLS/"GameLingoSettings.kt"))
        self.assertIn("GameLingoUIContract.settingsSections[3]",
                      code(IOS/"SettingsHomeView.swift"))
        self.assertIn("GameLingoUIContract.kt",code(TOOLS/"wire_android_devices.py"))
        self.assertIn("GameLingoDiagnostics.kt",code(TOOLS/"redesign_gamelingo.py"))
        appearance=code(TOOLS/"GameLingoSubtitleAppearance.kt")
        self.assertIn("tvPismo",appearance)
        self.assertIn("tvSirka",appearance)
        self.assertIn("Propojeni.Vysilac.nastavRozvrh",appearance)
        self.assertIn("GameLingoSubtitleAppearance.show(activity)",
                      code(TOOLS/"GameLingoSettings.kt"))
        subprocess.run([sys.executable, str(TOOLS/"sync_mobile_ui.py"),
                        "--check"], cwd=ROOT, check=True, capture_output=True)

    def test_real_android_mobile_settings_are_wired_not_decorative(self):
        redesign=code(TOOLS/"redesign_gamelingo.py")
        settings=code(TOOLS/"GameLingoSettings.kt")
        nav=code(TOOLS/"GameLingoNavigation.kt")
        ocr=code(TOOLS/"GameLingoOCRConfig.kt")
        ocrScreen=code(TOOLS/"GameLingoOCRSettings.kt")
        phone=code(TOOLS/"GameLingoPhoneSubtitleStyle.kt")
        subtitle=code(TOOLS/"GameLingoSubtitleAppearance.kt")
        self.assertIn("pageHost.removeAllViews()",redesign)
        self.assertIn("GameLingoRegions.content(this,",redesign)
        self.assertIn("GameLingoDeviceCenter.content(this,",redesign)
        self.assertIn("GameLingoSettings.content(this, nast,",redesign)
        self.assertIn("GameLingoAbout.content(this)",redesign)
        self.assertIn("initialIndex = selectedMobileTab",redesign)
        self.assertIn("activeIndex = index",nav)
        self.assertIn("refresh()",nav)
        self.assertIn("GameLingoOCRSettings.show(activity, onAreas)",settings)
        self.assertIn("GameLingoOCRConfig.intervalMs()",redesign)
        self.assertIn("GameLingoOCRConfig.filterMenuText",redesign)
        self.assertIn("GameLingoPhoneSubtitleStyle.apply",redesign)
        self.assertIn("fun setFilterMenu(",ocr)
        self.assertIn("fun setExpandSearch(",ocr)
        self.assertIn("fun setMode(",ocr)
        self.assertIn("OblastTitulku.uloz(activity)",ocrScreen)
        self.assertIn("setBackground(c: Context",phone)
        self.assertIn('listOf("white", "yellow", "cyan", "green", "purple")',phone)
        self.assertIn('listOf("none", "dark", "translucent")',phone)
        self.assertIn("GameLingoPhoneSubtitleStyle.setColor(activity, choice)",subtitle)
        self.assertIn("GameLingoPhoneSubtitleStyle.setBackground(activity, choice)",subtitle)
        self.assertIn("forTelevision = true",subtitle)

    def test_tv_iphone_pairing_retries_are_identical_and_safe(self):
        receiver=code(TOOLS/"IPhoneTVPairing.kt")
        patch=code(TOOLS/"patch_tv_iphone.py")
        ios=code(IOS/"PlayCaptionTV.swift")
        native=code(TOOLS/"redesign_gamelingo.py")
        # Both clients must use one exact protocol, not two superficially
        # similar PIN screens that cannot communicate.
        self.assertIn("PCPAIR1\\tPAIR",ios)
        self.assertIn("PCPAIR1\\\\tOK",patch)
        self.assertIn("IPhoneTVPairing.confirm(c, code, id)",patch)
        self.assertIn("lastSuccessPhoneId",receiver)
        self.assertIn("lastSuccessToken",receiver)
        self.assertIn("REPLY_RETRY_WINDOW_MS = 15_000L",receiver)
        self.assertIn("input == lastSuccessPin && phoneId == lastSuccessPhoneId",receiver)
        self.assertIn("self.pairingRetryTask = Task",ios)
        self.assertIn("self.pairingRetryTask?.cancel()",ios)
        self.assertIn("for _ in 0..<3",ios)
        self.assertIn("let request = Data(",ios)
        self.assertIn("var shownCode: String? = IPhoneTVPairing.startCode()",native)
        self.assertIn("PrijemService.statistikaPrijmu()",native)

    def test_tv_remote_control_and_receiver_modes(self):
        screen=code(TOOLS/"polish_all_screens.py")
        receiver=code(TOOLS/"redesign_gamelingo.py")
        tv=code(TOOLS/"GameLingoTVUi.kt")
        wiring=code(TOOLS/"wire_android_devices.py")
        for marker in ("nextFocusRightId", "nextFocusLeftId",
                       "Překlad z telefonu", "Přímo na TV",
                       "volby.post", "GameLingoTVUi.tile(",
                       "GameLingoTVUi.showSettings(this)"):
            self.assertIn(marker,screen)
        for marker in ("isFocusable = true", "setOnFocusChangeListener",
                       "showWelcome(", "Jazyk hry", "Jazyk překladu",
                       "showSettings(", "R.drawable.gamelingo_xbox",
                       "R.drawable.gamelingo_playstation"):
            self.assertIn(marker,tv)
        for marker in ("IPhoneTVPairing.startCode()", "PrijemService.statistikaPrijmu()",
                       "zpetNaVyber()", "GameLingoTVUi.action("):
            self.assertIn(marker,receiver)
        self.assertIn("GameLingoTVUi.showWelcome(this@MainActivity)",wiring)

    def test_android_can_remove_and_restore_xbox_without_signing_out(self):
        manager=code(TOOLS/"GameLingoDeviceCenter.kt")
        for marker in (
            '"Použít", "Připojit", "Odebrat"',
            '"hiddenXboxIds"',
            'filterNot { it.id in hiddenXboxIds }',
            '"Vrátit odebrané Xboxy"',
            'update.remove("selectedXboxId")',
            'RemotePlayService.aktivni',
            'getStringSet("hiddenXboxIds"',
        ):
            self.assertIn(marker,manager)
        self.assertNotIn("XboxUcet.odhlas",manager)

    def test_single_native_playstation_login_pin_prompt(self):
        home=code(IOS/"GameLingoHome.swift")
        self.assertIn('.alert("Přihlašovací PIN PlayStation"',home)
        self.assertIn('model.playStation.submitLoginPIN(psLoginPIN)',home)
        self.assertNotIn('playStationLoginPIN',home)
        self.assertNotIn('GLHeader(title: "PIN PlayStation"',home)

    def test_original_neon_screens_are_native_swiftui(self):
        theme=code(IOS/"GameLingoVisualSystem.swift")
        home=code(IOS/"GameLingoHome.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        regions=code(IOS/"SubtitleSettingsView.swift")
        devices=code(IOS/"GameLingoDevicesPanel.swift")
        settings=code(IOS/"SettingsHomeView.swift")
        for component in ("GLNeonBackground", "GLNeonPanel", "GLBrandWordmark",
                          "GLPlatformBadge", "GLDetailRow"):
            self.assertIn("struct "+component,theme)
        for marker in ("heroBanner", "GLBrandWordmark(compact: true)",
                       "GLPlatformBadge(family: .xbox",
                       "GLPlatformBadge(family: .playStation",
                       "GLNeonPanel(emphasized: connected)", "GLNeonBackground()"):
            self.assertIn(marker,home)
        for marker in ("GLBrandWordmark()", "GLPlatformBadge(family: family",
                       "case 1: languageStep", "case 2: consoleStep", "case 3: outputStep",
                       "ForEach(0..<5)", "GLNeonBackground()"):
            self.assertIn(marker,setup)
        for marker in ("GLNeonTitle(", "GLNeonPanel(emphasized: true)",
                       "ForEach(Area.allCases", "model.choicesOutputArea"):
            self.assertIn(marker,regions)
        self.assertIn("GLPlatformBadge(family: console.family",devices)
        for marker in ("GLDetailRow(icon:", "GameLingoLanguageSettingsView",
                       "GameLingoLicenseView", "GLNeonBackground()"):
            self.assertIn(marker,settings)

    def test_configured_languages_change_real_ocr_and_translation(self):
        language=code(IOS/"GameLingoLanguages.swift")
        model=code(IOS/"PlayCaptionIOSApp.swift")
        pipeline=code(IOS/"CaptionPipeline.swift")
        settings=code(IOS/"SettingsHomeView.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        self.assertIn("enum GLGameLanguage",language)
        self.assertIn("enum GLTargetLanguage",language)
        for needle in ("@Published var gameLanguage", "@Published var targetLanguage",
                       "gameLanguage: activeGameLanguage", "to: activeTargetLanguage"):
            self.assertIn(needle,model)
        for needle in ("gameLanguage.visionIdentifier", "source.rawValue",
                       "target.rawValue", 'URLQueryItem(name: "sl"',
                       'URLQueryItem(name: "tl"'):
            self.assertIn(needle,pipeline)
        for view in (settings,setup):
            self.assertIn("$model.gameLanguage",view)
            self.assertIn("$model.targetLanguage",view)
        self.assertIn('selection: $model.appLanguage',setup)
        self.assertIn('selection: $model.appLanguage',settings)
        self.assertIn('Vítej v GameLingo',setup)
        self.assertIn('ForEach(0..<5)',setup)

    def test_pinned_native_source_and_build(self):
        workflow=code(ROOT/".github/workflows/build-gamelingo.yml")
        project=code(ROOT/"ios/GameLingoIOS/project.yml")
        self.assertIn('482611539977496cad047560c9bc1ebc5809a566',workflow)
        self.assertIn('Build native PlayStation',workflow)
        self.assertIn('GameLingoPSBridge.h',project)
        self.assertIn('0.5.0',project)

    def test_no_fake_ps5_readiness(self):
        all_ios="\n".join(code(path) for path in IOS.rglob("*.swift"))
        self.assertNotIn('PS5 – coming soon',all_ios)
        self.assertNotIn('GameLingoPlayStationStatus.isReady',all_ios)

if __name__=="__main__":
    unittest.main()
