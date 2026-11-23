#!/usr/bin/env python3
"""GameLingo v0.5 structural regression checks.

These are static contract checks and DO NOT substitute physical PS4/PS5, Xbox,
iPhone, Android or Google TV integration tests.
"""
from pathlib import Path
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
        for label in ('Vyber konzoli', 'Kam zobrazit titulky?',
                      'PlayStation', 'Xbox', 'Spárovat TV'):
            self.assertIn(label,setup)
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
        self.assertIn("struct XboxAccountView: View", model)
        self.assertIn("struct TVSelectionView: View", model)

    def test_live_session_cannot_silently_change_selected_console(self):
        panel=code(IOS/"GameLingoDevicesPanel.swift")
        home=code(IOS/"GameLingoHome.swift")
        self.assertIn('if !model.isRunning { devices.selectConsole(console.id) }', panel)
        self.assertIn('guard !model.isRunning else { pendingRemove = nil; return }', panel)
        self.assertIn('.disabled(model.isRunning)', panel)
        self.assertIn('"Nastaveno"', home)
        self.assertIn('"Probíhá překlad"', home)

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
        self.assertIn("devices.subtitleOutput == .googleTV", relocation)
        self.assertIn("devices.chosenTelevision != nil", relocation)
        self.assertIn("devices.prepareTV(tv)", relocation)
        self.assertIn("tv.sendChoices(", relocation)
        self.assertIn(".onChange(of: devices.preferredTelevisionID)", home)
        self.assertIn(".onChange(of: devices.subtitleOutput)", home)
        self.assertIn("selectedTV.ipAddress", home)
        self.assertIn("previous == .googleTV && output == .phone", home)
        self.assertIn('model.tv.sendSubtitle("", duration: 0)', home)
        self.assertIn("devices.prepareTV(model.tv)", home)
        self.assertIn("GameLingo iOS 0.5.0", model)

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
        for step in ('Kde hraješ?', 'Kam zobrazit titulky?',
                     'Nastavit později', 'connectPlayStation()', 'connectTV()'):
            self.assertIn(step,first_run)
        self.assertIn('AES/GCM/NoPadding',psstore)
        self.assertIn('setSingleChoiceItems',multi)
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
        self.assertIn("Výběr není potvrzení spojení",android)

    def test_five_destinations_native_android_navigation(self):
        nav=code(TOOLS/"GameLingoNavigation.kt")
        mobile=code(TOOLS/"redesign_gamelingo.py")
        wiring=code(TOOLS/"wire_android_devices.py")
        about=code(TOOLS/"GameLingoAbout.kt")
        for title in ("Domů", "Oblasti", "Profily", "Zařízení", "O aplikaci"):
            self.assertIn(title,nav)
        self.assertIn("GameLingoNavigation.create(",mobile)
        self.assertIn('onDevices = { vyberVystupTv() }',mobile)
        self.assertIn('onAbout = { GameLingoAbout.show(this) }',mobile)
        self.assertIn('GameLingoNavigation.kt',wiring)
        self.assertIn('GameLingoAbout.kt',wiring)
        self.assertIn("'onDevices = { '+manager()+' },'",wiring)
        self.assertIn("AGPL-3.0",about)

    def test_tv_remote_control_and_receiver_modes(self):
        tv=code(TOOLS/"polish_all_screens.py")
        for marker in ("dlazdiceTel.isFocusable", "nastavDpad(",
                       "nextFocusRightId", "nextFocusLeftId",
                       "Překlad z telefonu", "Přímo na TV",
                       "volby.post"):
            if marker == "dlazdiceTel.isFocusable":
                self.assertIn("v.isFocusable = true",tv)
            else:
                self.assertIn(marker,tv)

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
