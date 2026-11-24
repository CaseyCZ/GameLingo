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
        self.assertIn("defer {\n            if self.sessionRevision == currentSession",model)
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
        self.assertRegex(model, r"if self\.verifiedTelevisionOutput\s*\{\s*self\.tv\.sendChoices\(")
        self.assertRegex(model, r"if self\.verifiedTelevisionOutput\s*\{\s*self\.tv\.sendSubtitle\(")

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
        self.assertIn("IPhoneTVPairing.confirm(c, code, id, casti.getOrNull(4).orEmpty(), p.address.hostAddress.orEmpty())",patch)
        self.assertIn("lastSuccessPhoneId",receiver)
        self.assertIn("return@synchronized loadSecret(ctx, phoneId)",receiver)
        self.assertNotIn("lastSuccessToken",receiver)
        self.assertIn("REPLY_RETRY_WINDOW_MS = 15_000L",receiver)
        self.assertIn("input == lastSuccessPin && phoneId == lastSuccessPhoneId",receiver)
        self.assertIn("self.pairingRetryTask = Task",ios)
        self.assertIn("self.pairingRetryTask?.cancel()",ios)
        self.assertIn("for _ in 0..<3",ios)
        self.assertIn("let request = Data(",ios)
        self.assertIn("var shownCode: String? = IPhoneTVPairing.startCode()",native)
        self.assertIn("PrijemService.statistikaPrijmu()",native)

    def test_p0_1_no_unsigned_caption_fallback(self):
        ios = code(IOS/"PlayCaptionTV.swift")
        android = code(TOOLS/"GameLingoTVPairing.kt")
        wire = code(TOOLS/"wire_android_devices.py")
        receiver = code(TOOLS/"patch_tv_iphone.py")
        tv = code(TOOLS/"GameLingoTVUi.kt")
        self.assertNotIn("sendDatagram(payload, to: target", ios)
        self.assertNotIn("sendDatagram(bytes, to: target", ios)
        self.assertIn("sendSignedSubtitle(Data(packet.utf8)", ios)
        self.assertIn('getBoolean("legacy_unsigned_receiver", false)', android)
        self.assertIn("fun signedPacket(", android)
        self.assertEqual(wire.count("GameLingoTVPairing.sendSigned("), 4)
        self.assertEqual(wire.count("GameLingoTVPairing.signedPacket(ip, data)"), 2)
        self.assertIn("GameLingoTVPairing.initialize(this)", wire)
        self.assertIn("!authenticatedGameData && !GameLingoTVPairing.legacyEnabled(c)", receiver)
        self.assertIn('p.setData(decoded.toByteArray(Charsets.UTF_8))', receiver)
        self.assertIn('listOf("PCT1", "PCV1", "PCO1", "PCQ1", "PCS1")', receiver)
        self.assertIn('casti.firstOrNull() == "PCS1" && casti.getOrNull(1) in listOf("ZADOST", "KOD")', receiver)
        self.assertIn("GameLingoTVPairing.setLegacyEnabled(a, it)", tv)

    def test_p0_2_replay_guard_and_signed_pong(self):
        ios = code(IOS/"PlayCaptionTV.swift")
        android = code(TOOLS/"GameLingoTVPairing.kt")
        receiver = code(TOOLS/"IPhoneTVPairing.kt")
        patch = code(TOOLS/"patch_tv_iphone.py")
        self.assertIn("lastWireSequence = max(lastWireSequence + 1, now * 1000)", ios)
        self.assertIn("lastWireSequence = maxOf(lastWireSequence + 1, now * 1000L)", android)
        self.assertIn('private const val FRESHNESS_MS = 120_000L', receiver)
        self.assertIn("timestamp < now - FRESHNESS_MS || timestamp > now + FRESHNESS_MS", receiver)
        self.assertIn('val slot = sequenceSlot(phoneId, lane)', receiver)
        self.assertIn("sequence <= p.getLong(slot, 0L)", receiver)
        self.assertIn("if (!p.edit().putLong(slot, sequence).commit())", receiver)
        self.assertEqual(patch.count("IPhoneTVPairing.verifyFresh(c, id,"), 3)
        self.assertIn("IPhoneTVPairing.textLane(encoded)", patch)
        self.assertIn("fields[2] == self.pendingPingNonce", ios)
        self.assertIn("self.pendingPingNonce = nil", ios)
        self.assertIn("IPhoneTVPairing.pongSignature(c, id, nonce)", patch)
        self.assertIn("MessageDigest.isEqual(", android)

    def test_p0_3_receiver_key_is_encrypted_and_migrated(self):
        receiver = code(TOOLS/"IPhoneTVPairing.kt")
        patch = code(TOOLS/"patch_tv_iphone.py")
        wire = code(TOOLS/"wire_android_devices.py")
        device_tests = code(TOOLS/"IPhoneTVPairingKeystoreTest.kt")
        self.assertIn('KeyStore.getInstance("AndroidKeyStore")', receiver)
        self.assertIn('Cipher.getInstance("AES/GCM/NoPadding")', receiver)
        self.assertIn("cipher.updateAAD(aad(phoneId))", receiver)
        self.assertIn("wrappingKey(false)", receiver)
        self.assertIn('p.edit().remove("token").remove(ENCRYPTED_TOKEN).remove("phone_id")', receiver)
        self.assertIn("putString(tokenSlot(phoneId), encrypted)", receiver)
        self.assertNotIn('putString("token", token)', receiver)
        self.assertIn("storageWriteFailed", receiver)
        self.assertIn("IPhoneTVPairing.initialize(c)", patch)
        self.assertIn("IPhoneTVPairing.initialize(this)", wire)
        self.assertIn("legacyMigrationPreservesIdentityAndReplay", device_tests)
        self.assertIn("tamperedCiphertextCannotFallBack", device_tests)
        self.assertIn("pairRetryAndClearUseEncryptedStorage", device_tests)

    def test_p0_4_independent_phone_records_and_source_isolation(self):
        receiver = code(TOOLS/"IPhoneTVPairing.kt")
        patch = code(TOOLS/"patch_tv_iphone.py")
        tv = code(TOOLS/"redesign_gamelingo.py")
        device_tests = code(TOOLS/"IPhoneTVPairingKeystoreTest.kt")
        self.assertIn('putStringSet(PHONE_IDS, ids(ctx) + phoneId)', receiver)
        self.assertIn('putStringSet(PHONE_IDS, known - phoneId)', receiver)
        self.assertIn('sequenceSlot(phoneId, lane)', receiver)
        self.assertIn('private const val SOURCE_LEASE_MS = 5000L', receiver)
        self.assertIn('IPhoneTVPairing.claimGameData(id, IPhoneTVPairing.extendsSourceLease(inner))', patch)
        self.assertIn('resetSourceIfChanged()', patch)
        self.assertIn('pribeh = newStoryReceiver()', patch)
        self.assertIn('IPhoneTVPairing.unpair(this, phone.id)', tv)
        self.assertNotIn('IPhoneTVPairing.clear(this)', tv)
        self.assertIn('twoPhonesKeepIndependentKeysReplayAndRevocation', device_tests)

    def test_p0_4_idle_packets_cannot_hold_overlay_source(self):
        receiver = code(TOOLS/"IPhoneTVPairing.kt")
        wire = code(TOOLS/"wire_android_devices.py")
        device_tests = code(TOOLS/"IPhoneTVPairingKeystoreTest.kt")
        self.assertIn("if (!extendsLease) return@synchronized", receiver)
        self.assertIn("fun stopGameData()", wire)
        self.assertIn("if (telefon) Propojeni.Vysilac.stopGameData()", wire)
        self.assertIn("live || now < upravaDo", wire)
        self.assertIn("live || now < volbyUpravaDo", wire)
        self.assertIn("emptyPacketsAndReadinessDoNotClaimSource", device_tests)
        patch = code(TOOLS/"patch_tv_iphone.py")
        self.assertIn("fun permitsStoryReadiness(phoneId: String)", receiver)
        self.assertIn("IPhoneTVPairing.permitsStoryReadiness(id)", patch)
        self.assertIn("assertEquals(revision, IPhoneTVPairing.overlayRevision())", device_tests)

    def test_p0_5_real_android_ps4_target_and_hdmi_story_contract(self):
        wire = code(TOOLS/"wire_android_devices.py")
        store = code(TOOLS/"GameLingoPlayStationConsoles.kt")
        patch = code(TOOLS/"patch_tv_iphone.py")
        verify = code(TOOLS/"verify_gamelingo.py")
        self.assertIn("Target.PS4_10", wire)
        self.assertIn("udaje.consoleTarget = udalost.host.target", wire)
        self.assertIn("ConnectInfo(ps5 = udaje.isPS5", wire)
        self.assertIn("val hevc = udaje.isPS5", wire)
        self.assertIn("it.ip == ip && it.typ == udaje.consoleType", wire)
        self.assertIn('put("target", current.consoleTarget.value)', store)
        self.assertIn("active.consoleTarget = Target.values()", store)
        self.assertIn("udaje.consoleTarget = udalost.host.target", wire)
        self.assertIn("explicit receiver mode, same as standard captions", patch)
        self.assertIn("val vstup = PrijemService.bezi ||", patch)
        self.assertIn("ConnectInfo(ps5 = udaje.isPS5", verify)

    def test_p1_1_ocr_fingerprint_is_last_successful_read_per_region(self):
        fingerprint = code(IOS/"OCRFrameFingerprint.swift")
        pipeline = code(IOS/"CaptionPipeline.swift")
        self.assertIn("CVPixelBufferLockBaseAddress(buffer, .readOnly)", fingerprint)
        self.assertIn("CVPixelBufferUnlockBaseAddress(buffer, .readOnly)", fingerprint)
        self.assertIn("now - readAt < maximumSkip", fingerprint)
        self.assertIn("lastRead = fingerprint", fingerprint)
        self.assertIn("OCRFrameGate(maximumSkip: 1.5)", pipeline)
        self.assertIn("OCRFrameGate(maximumSkip: 2.0)", pipeline)
        self.assertIn("dialogueImageGate.reset()", pipeline)
        self.assertIn("optionsImageGate.reset()", pipeline)
        self.assertEqual(pipeline.count("recordSuccessfulRead(fingerprint"), 2)
        self.assertEqual(pipeline.count("shouldRead(fingerprint"), 2)

    def test_p1_2_latest_frame_mailboxes_are_bounded_and_session_owned(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        mailbox = code(IOS/"LatestFrameMailbox.swift")
        self.assertIn("pending = value", mailbox)
        self.assertIn("guard !draining else { return false }", mailbox)
        self.assertIn("draining = false", mailbox)
        self.assertIn("guard !closed, enabled", mailbox)
        self.assertIn("play.onFrame = receiveFrame", model)
        self.assertIn("playStation.start(console: console, onFrame: receiveFrame", model)
        self.assertIn("if dialogue.offer(buffer)", model)
        self.assertIn("if options.offer(buffer)", model)
        self.assertIn("while let frame = dialogue.takeNext()", model)
        self.assertIn("while let frame = options.takeNext()", model)
        self.assertIn("optionsFrames?.setEnabled(choicesEnabled)", model)
        stop = model.split("func stop() {", 1)[1]
        self.assertIn("dialogueFrames?.close()", stop)
        self.assertIn("optionsFrames?.close()", stop)
        self.assertNotIn("await self?.processFrame(buffer", model)
        self.assertNotIn("await self?.processFrame(frame", model)

    def test_p1_3_persistent_translation_cache_uses_engine_and_language_keys(self):
        swift = code(IOS/"TranslationCache.swift")
        pipeline = code(IOS/"CaptionPipeline.swift")
        android = code(TOOLS/"GameLingoTranslationCache.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        for source in (swift, android):
            self.assertIn("20_000", source)
            self.assertIn("last_used", source)
            self.assertIn("ORDER BY last_used DESC", source)
            self.assertIn("INSERT OR REPLACE", source)
        self.assertIn("isExcludedFromBackup = true", swift)
        self.assertIn("translationCache.lookup(engine: engine", pipeline)
        self.assertIn("translationCache.remember(engine: engine", pipeline)
        self.assertNotIn("lastTranslate", pipeline)
        self.assertIn("ArrayBlockingQueue<Runnable>(128)", android)
        self.assertIn("cachedOrRun", wiring)
        self.assertIn("google(vstup, target, source)", wiring)
        self.assertIn("currentPair(source, target)", wiring)
        self.assertIn("GameLingoTranslationCacheTest.kt", wiring)

    def test_p1_4_translation_failures_never_render_original_text(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        outcome = code(IOS/"TranslationOutcome.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("async -> Result<String, GLTranslationFailure>", pipeline)
        self.assertNotIn("return original", pipeline)
        self.assertRegex(pipeline, r"case \.success\(let translated\):\s*await translationCache\.remember")
        self.assertIn("guard case .success(let rawTranslation) = outcome", model)
        self.assertIn("case .failure(let error):", model)
        self.assertIn("dialogueTranslationFailed(revision: reading.revision)", model)
        self.assertIn("optionsTranslationFailed(englishChoices", model)
        self.assertIn("dialogueRetryAt", pipeline)
        self.assertIn("optionsRetryAt", pipeline)
        self.assertIn("guard statusCode == 200", outcome)
        policy = code(TOOLS/"tv_translation_policy.py")
        self.assertIn('chyba("Online překlad není dostupný")', policy)
        self.assertIn("if (!hotovyText.isNullOrBlank())", wiring)
        self.assertIn("?.takeIf { it.isNotBlank() }", wiring)
        self.assertIn("static func edgeResponse", outcome)
        self.assertIn("text.isEmpty ? .failure(.invalidResponse)", outcome)
        self.assertIn("vysledky[i] = null", wiring)
        self.assertIn("prelozene.size != volby.size", wiring)

    def test_p1_5_stabilization_and_bounded_dialogue_display(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        stability = code(IOS/"CaptionStability.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("dialogueStability.needsConfirmation", pipeline)
        self.assertIn("gameTextStability.needsConfirmation", pipeline)
        self.assertIn('RecognizedCaption(english: "", revision: revision', pipeline)
        self.assertIn("now - since >= 0.8", stability)
        self.assertIn("text.split(separator:", stability)
        self.assertIn("min(4.0, max(0.1, requested))", model)
        self.assertIn("self.dialogueDisplayRevision == display", model)
        self.assertIn("self.clearDialogueOutput()", model)
        self.assertIn("ukaz(cesky, 4000, box, radku, barvyJmen)", wiring)
        self.assertIn("coerceIn(0L, 800L)", wiring)

    def test_p1_6_translation_breakers_and_fallback(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        swift = code(IOS/"TranslationCircuitBreaker.swift")
        kotlin = code(TOOLS/"GameLingoCircuitBreaker.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("failures >= 2", swift)
        self.assertIn("now + 30", swift)
        self.assertIn("!probing", swift)
        self.assertIn("ticket.generation == generation", swift)
        self.assertIn("failures >= 2", kotlin)
        self.assertIn("now + 30_000L", kotlin)
        self.assertIn("@Synchronized fun acquire", kotlin)
        self.assertIn('["google-gtx-v1", "microsoft-edge-v1", "mymemory-v1"]', pipeline)
        self.assertIn("GLTranslationOutcome.edgeResponse", pipeline)
        self.assertIn("requestEdge(original:", pipeline)
        self.assertIn("providerBreakers[engine", pipeline)
        self.assertIn("GLTranslationOutcome.myMemoryResponse", pipeline)
        self.assertIn("guardedGoogle(t, jazyk, source)", wiring)
        self.assertIn("guardedProvider(edgeBreaker)", wiring)
        self.assertIn("guardedProvider(myMemoryBreaker)", wiring)
        self.assertIn("GameLingoCircuitBreakerTest.kt", wiring)

    def test_p1_7_local_performance_diagnostics_are_bounded(self):
        swift = code(IOS/"PerformanceWindow.swift")
        device = code(IOS/"LocalDeviceDiagnostics.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        android = code(TOOLS/"GameLingoPerformance.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("capacity: Int = 128", swift)
        self.assertIn("samples[cursor] = value", swift)
        self.assertIn("0.95", swift)
        self.assertIn("MACH_TASK_BASIC_INFO", device)
        self.assertIn("thermalState", device)
        self.assertIn("beginPerformanceSession(currentSession:", model)
        self.assertIn("recordPerformance", model)
        self.assertIn("removeObject(forKey: sessionMarker)", model)
        self.assertIn("Debug.getPss()", android)
        self.assertIn("currentThermalStatus", android)
        self.assertIn("session_open", android)
        self.assertIn("performance.begin(this)", wiring)
        self.assertIn("performance.end(this)", wiring)
        self.assertIn("performance.record(perfToken", wiring)

    def test_p1_8_modes_use_independent_adaptive_game_text_cadence(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        config = code(TOOLS/"GameLingoOCRConfig.kt")
        cadence = code(TOOLS/"GameLingoOCRCadence.kt")
        redesign = code(TOOLS/"redesign_gamelingo.py")
        tv = code(TOOLS/"GameLingoTVUi.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("mode.cadence.gameTextInterval", pipeline)
        self.assertNotIn("timeIntervalSince(lastOptionsRead) > 1.0", pipeline)
        self.assertIn("optionsRecentCost", pipeline)
        self.assertIn("gameLanguage: activeGameLanguage, mode: self.ocrMode", model)
        self.assertIn("tvIntervalMs(lowPower: Boolean)", config)
        self.assertIn("GameLingoOCRConfig.tvIntervalMs(uspora)", redesign)
        self.assertIn('if (lowPower && !phone) return 600L', cadence)
        self.assertIn("GameLingoOCRConfig.setMode(a, keys[index])", tv)
        self.assertIn("GameLingoOCRConfig.gameTextIntervalMs", wiring)
        self.assertIn("gameTextOcrCostMs", wiring)

    def test_p1_9_translation_generation_invalidates_old_results(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        devices = code(IOS/"GameLingoDevices.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        languages = code(TOOLS/"GameLingoLanguages.kt")
        self.assertIn("dialogueRevision.accepts(readingRevision)", model)
        self.assertIn("optionsRevision.accepts(readingRevision)", model)
        self.assertIn("optionsTranslation?.cancel()", model)
        self.assertIn("dialogueTranslation?.cancel()", model)
        self.assertIn("devices.onOutputChanged", model)
        self.assertIn("onOutputChanged?()", devices)
        self.assertIn("GameTextKey(context.source, context.target, text)", wiring)
        self.assertIn("currentTranslation(context)", wiring)
        self.assertIn("generation != gameTextGeneration", wiring)
        self.assertIn("changes.incrementAndGet()", languages)

    def test_p2_1_qr_pairing_uses_existing_confirmation_and_pin(self):
        tv = code(TOOLS/"GameLingoQRDisplay.kt")
        parser = code(TOOLS/"GameLingoPairingQR.kt")
        scanner = code(TOOLS/"GameLingoQRScanner.kt")
        center = code(TOOLS/"GameLingoDeviceCenter.kt")
        ios = code(IOS/"GameLingoTVSetupScreen.swift")
        self.assertIn("GLPAIR1", parser)
        self.assertIn("IPhoneTVPairing.activeCode() != pin", tv)
        self.assertIn("handler.removeCallbacks(tick)", tv)
        self.assertIn("GameLingoPairingQR.parse(it)", scanner)
        self.assertIn("GameLingoTVPairing.show(c, candidate, initialPin = qr.pin)", center)
        self.assertIn("tv.receiverCode = code.pin", ios)
        self.assertIn("tv.pairReceiver()", ios)
        self.assertNotIn("pairReceiver()", ios.split("GameLingoQRScanner(onCode:", 1)[1])

    def test_p2_3_history_is_opt_in_bounded_and_separate(self):
        swift = code(IOS/"TranslationHistory.swift")
        kotlin = code(TOOLS/"GameLingoTranslationHistory.kt")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("enabled = false", swift)
        self.assertIn("limit = 200", swift)
        self.assertIn("if !value { clear() }", swift)
        self.assertIn("recording = false", kotlin)
        self.assertIn("LIMIT = 200", kotlin)
        self.assertIn("if (!value) entries.clear()", kotlin)
        self.assertIn("self.translationHistory.append", model)
        self.assertIn("if (dobry) GameLingoTranslationHistory.append", wiring)
        self.assertIn("GameLingoTranslationHistory.append", wiring)
        self.assertIn("GameLingoHistoryUi.show", code(TOOLS/"GameLingoSettings.kt"))
        self.assertIn("GameLingoHistoryUi.show", code(TOOLS/"GameLingoTVUi.kt"))

    def test_p2_4_glossary_is_profile_local_and_post_translation(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        profile = code(IOS/"SettingsHomeView.swift")
        store = code(TOOLS/"GameLingoGlossaryStore.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("activeGlossaryProfileID", model)
        self.assertIn("var glossary: GameGlossary? = nil", profile)
        self.assertIn("glossaryTranslation(original:", model)
        self.assertIn("GameLingoGlossaryStore.generation()", wiring)
        self.assertIn("GameLingoGlossaryStore.apply", wiring)
        self.assertIn('gamelingo_glossary_v1', store)
        self.assertIn("GameLingoGlossaryStore.activate(activity, name)", code(TOOLS/"GameLingoProfiles.kt"))

    def test_tv_translation_sdk_is_removed_at_build_and_binary_levels(self):
        policy = code(TOOLS/"tv_translation_policy.py")
        brand = code(TOOLS/"brand_android.py")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        verify = code(TOOLS/"verify_gamelingo.py")
        workflow = code(ROOT/".github/workflows/build-gamelingo.yml")
        self.assertIn("strip_dependency(gradle.read_text", brand)
        self.assertIn("t = tv_translator(t)", wiring)
        self.assertIn("finalize_tv(root, folder)", wiring)
        self.assertIn("assert_tv_source(root)", verify)
        self.assertIn("resolutionStrategy.eachDependency", policy)
        self.assertIn("skipGoogle = true", wiring)
        self.assertIn("skipGoogle: Boolean = false", policy)
        self.assertIn("Rychlý náhled není v paměti", policy)
        self.assertIn('verify_tv_translation_apk.py tv "$APK"', workflow)
        self.assertIn('verify_tv_translation_apk.py mobile "$APK"', workflow)

    def test_translation_sdk_apk_gate_detects_multidex_references(self):
        import tempfile
        import zipfile
        import importlib.util
        spec = importlib.util.spec_from_file_location("apk_policy", TOOLS/"verify_tv_translation_apk.py")
        gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(gate)
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp)/"candidate.apk"
            def make(*parts):
                with zipfile.ZipFile(apk, "w") as out:
                    for i, part in enumerate(parts):
                        out.writestr("classes.dex" if i == 0 else f"classes{i+1}.dex", b"dex\n035\x00"+part)
            make(b"Lcom/google/mlkit/vision/text/TextRecognition;")
            gate.verify(apk, "tv")
            gate.verify(apk, "mobile")
            for marker in gate.MARKERS:
                make(b"normal", marker)
                for mode in ("tv", "mobile"):
                    with self.assertRaises(AssertionError):
                        gate.verify(apk, mode)

    def test_tv_automatic_udp_port_fallback_is_coordinated(self):
        """Do not move TV port without moving iOS/Android pairing and captions."""
        tv_patch=code(TOOLS/"patch_tv_iphone.py")
        ios=code(IOS/"PlayCaptionTV.swift")
        android=code(TOOLS/"GameLingoTVPairing.kt")
        tv_screen=code(TOOLS/"redesign_gamelingo.py")
        self.assertIn("listOf(PORT_TITULKY, 41990, 41991, 41992, 41993)",tv_patch)
        self.assertIn("reuseAddress = false",tv_patch)
        self.assertIn("firstNotNullOfOrNull",tv_patch)
        self.assertIn('casti[0] == "GLPORT1"',tv_patch)
        self.assertIn('GLPORT1\\\\tPORT\\\\t$prijimaciPort',tv_patch)
        self.assertIn("fun prijimaciPort()",tv_patch)
        self.assertIn('PrijemService.prijimaciPort()',tv_screen)
        self.assertIn("private func discoverReceiverPort(",ios)
        self.assertIn('GLPORT1\\tQUERY',ios)
        self.assertIn("for candidate in 41989...41993",ios)
        self.assertIn("self.beginReceiverPairing(target: target, code: code, port: port)",ios)
        self.assertIn("self.connectLiveReceiver(target: target, port: port)",ios)
        self.assertIn("UInt16(cachedReceiverPort(target))",ios)
        self.assertIn("private fun receiverPort(ip: String)",android)
        self.assertIn("receiverPort(tv.ip)",android)
        self.assertIn("receiverPort(ip)",android)
        # The native Android caption sender must use the same resolved port,
        # not merely the PIN handshake and PING.
        sender=code(TOOLS/"wire_android_devices.py")
        for call in ('GameLingoTVPairing.portFor(cil.first)',
                     'GameLingoTVPairing.portFor(ip)'):
            self.assertIn(call, sender)
        self.assertIn("fun portFor(ip: String): Int", android)
        self.assertIn("checkedPortAt", android)
        self.assertIn('sender_marker = "    object Vysilac {"', sender)
        self.assertIn("sender = one(sender, old, new, label)", sender)
        self.assertIn("transport.write_text(prefix + sender_marker + sender", sender)

    def test_tv_style_control_and_live_game_profile_save(self):
        """TV style is authenticated and persisted; running games may be saved."""
        ios=code(IOS/"PlayCaptionTV.swift")
        model=code(IOS/"PlayCaptionIOSApp.swift")
        settings=code(IOS/"SettingsHomeView.swift")
        regions=code(IOS/"SubtitleSettingsView.swift")
        patch=code(TOOLS/"patch_tv_iphone.py")
        style=code(TOOLS/"GameLingoTVCaptionStyle.kt")
        self.assertIn("func updateCaptionStyle(color: String, background: String)", ios)
        self.assertIn("let body = \"STYLE", ios)
        self.assertIn("self.updateCaptionStyle(", ios)
        self.assertIn("tvTextColor: tvTextColor.rawValue",model)
        self.assertIn("tvBackground: tvBackground.rawValue",model)
        self.assertIn("if let name = profile.tvTextColor",model)
        self.assertIn("if let name = profile.tvBackground",model)
        self.assertIn('Text("Barva textu na TV")',settings)
        self.assertIn('Picker("Pozadí na TV"',settings)
        self.assertIn('savedProfileMessage',settings)
        self.assertNotIn('disabled: profileName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty\n                        || model.isRunning',settings)
        self.assertIn("if model.isRunning && model.relocateActiveChoices()",regions)
        self.assertIn("GameLingoTVCaptionStyle.kt",patch)
        self.assertIn('"STYLE" -> {',patch)
        self.assertIn("GameLingoTVCaptionStyle.save(c, textColor, background)",patch)
        self.assertIn("PixelFormat.TRANSLUCENT",patch)
        self.assertIn("pod.visibility = if (GameLingoTVCaptionStyle.isDark(this))",patch)
        for item in ('"white"', '"yellow"', '"cyan"', '"green"', '"purple"',
                     '"none"', '"dark"', '"translucent"'):
            self.assertIn(item,style)

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

    def test_tv_audit_followup_has_reachable_controls_and_binary_gate(self):
        receiver = code(TOOLS/"redesign_gamelingo.py")
        direct = code(TOOLS/"direct_tv_mode.py")
        settings = code(TOOLS/"GameLingoTVSettings.kt")
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        fit = code(TOOLS/"GameLingoTVFit.kt")
        for value in ("GameLingoTVFit(this, obsah)", "GameLingoQRDisplay.bitmap", "paticka.addView(prijemDiag", "versionName"):
            self.assertIn(value, receiver)
        self.assertIn("cards.chunked(3)", settings)
        self.assertIn("GameLingoTVCaptionStyle.show", settings)
        self.assertIn("GameLingoTVProfiles.show", settings)
        self.assertIn("GameLingoTVSettings.show(this,", direct)
        self.assertIn("GameLingoScreenTranslationService.open(this)", direct)
        self.assertIn("createScreenCaptureIntent", capture)
        self.assertIn("registerCallback", capture)
        self.assertNotIn("FLAG_SECURE", capture)
        self.assertIn("content.scaleX=factor", fit)
        self.assertIn("GITHUB_RUN_NUMBER", code(TOOLS/"brand_android.py"))
        self.assertIn("verify_tv_ui_apk.py", code(TOOLS.parent/".github/workflows/build-gamelingo.yml"))

    def test_tv_projection_waits_for_typed_foreground_and_success_before_background(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        activity = capture.split("class GameLingoScreenTranslationService", 1)[0]
        session = activity.split("private fun startSession", 1)[1]
        self.assertNotIn("OverlayService.spust", session)
        self.assertNotIn("moveTaskToBack", session)
        self.assertIn("GameLingoScreenTranslationService.running ->", activity)
        self.assertIn("GameLingoScreenTranslationService.startupFailed ->", activity)
        service = capture.split("class GameLingoScreenTranslationService", 1)[1]
        self.assertLess(service.index("notification()", service.index("starting = true")), service.index("ui.postDelayed(task, 500L)"))
        self.assertIn("foregroundServiceType", service)
        start = service.split("private fun beginCapture", 1)[1].split("private fun failStartup", 1)[0]
        self.assertLess(start.index("getMediaProjection"), start.index("createVirtualDisplay"))
        self.assertLess(start.index("createVirtualDisplay"), start.index("OverlayService.spust"))
        self.assertIn("pendingStart?.let", service)
        self.assertIn("FOREGROUND_SERVICE_MEDIA_PROJECTION", code(TOOLS/"direct_tv_mode.py"))

    def test_standalone_tv_ocr_and_translation_are_independent_and_menu_filtered(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        wiring = code(TOOLS/"direct_tv_mode.py")
        self.assertIn("prectiPosledniTitulek(cropped, hotovo", capture)
        self.assertIn("standaloneScreen = true", capture)
        self.assertIn("if (!paused && !ocrBusy && !capturePending && wantFrame.get() == 0)", capture)
        self.assertIn("translationBusy", capture)
        self.assertIn("value == candidate", capture)
        self.assertIn("Starý výsledek zahozen", capture)
        self.assertIn("coverOriginal = settings.prekryvat", capture)
        self.assertIn("getRealMetrics(metrics)", capture)
        self.assertIn("kusy.size > 8", wiring)
        self.assertIn("kod == sourceLanguage", wiring)
        self.assertNotIn("fun prectiSamostatnyTitulek", wiring)
        self.assertIn("lastOcrMs", capture)
        self.assertIn("lastTranslationMs", capture)

    def test_standalone_tv_reuses_all_four_profile_regions(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        layout = code(TOOLS/"GameLingoDirectTVLayout.kt")
        profiles = code(TOOLS/"GameLingoTVProfiles.kt")
        for marker in ("GameLingoDirectTVLayout.blue(this)", "GameLingoDirectTVLayout.green(this)",
                       "GameLingoDirectTVLayout.yellow(this)", "GameLingoDirectTVLayout.orange(this)",
                       "Volby.zapnuto", "Volby.rezimText", "prectiVolby(cropped", "ACTION_SET_VOLBY",
                       "GameLingoTVProfiles.chooseForStart(a)", "if (yellow) yellowRoi else roi"):
            self.assertIn(marker, capture)
        self.assertIn("restore(a, items.getJSONObject(names[index-1]))", profiles)
        self.assertIn("fun greenBounds", layout)
        self.assertIn("wantFrame.getAndSet(0)", capture)
        self.assertIn("yellowEpoch", capture)
        self.assertIn("GameLingoTVProfiles.chooseForStart(this)", code(TOOLS/"direct_tv_mode.py"))
        self.assertIn("suppressStandaloneCapture(true)", capture)
        self.assertIn("settings.casPribehuProcenta", capture)
        self.assertIn("PribehovaRelace(", capture)
        self.assertIn("shownUntil", capture)

    def test_tv_uses_shared_mobile_settings_and_persists_layout_locks(self):
        tv = code(TOOLS/"GameLingoTVSettings.kt")
        settings = code(TOOLS/"GameLingoSettings.kt")
        profiles = code(TOOLS/"GameLingoTVProfiles.kt")
        layout = code(TOOLS/"GameLingoDirectTVLayout.kt")
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("GameLingoOCRSettings.show(a)", tv)
        self.assertIn("GameLingoSettings.showTranslation(a", tv)
        for marker in ("settings.napovedy", "settings.prekryvat", "settings.casPribehuProcenta",
                       "Volby.rezimText", "OblastTitulku.rucne"):
            self.assertIn(marker, settings)
        for marker in ("blueLocked", "yellowLocked", "greenLocked", "orangeLocked"):
            self.assertIn(marker, profiles)
        self.assertIn("Obnovit oblast", layout)
        self.assertIn("GameLingoTVProfiles.saveCurrent(a)",layout)
        self.assertIn("fun saveCurrent",profiles)
        for marker in ("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_CENTER", "TYPE_APPLICATION_OVERLAY", "FLAG_NOT_TOUCHABLE", "Uložit do profilu", "removePreview()", "30000L"):
            self.assertIn(marker, layout)
        self.assertIn("GameLingoOCRConfig.expandSearch", capture)
        self.assertIn("blockedHome", capture)
        self.assertIn("clearHint()", capture)

    def test_tv_fullscreen_pages_and_global_dialog_theme(self):
        ui=code(TOOLS/"GameLingoTVUi.kt")
        wiring=code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("fun page(a: Activity",ui)
        for module in ("GameLingoTVCaptionStyle.kt","GameLingoHistoryUi.kt","GameLingoTVSettings.kt","GameLingoTranslationCache.kt"):
            self.assertIn("GameLingoTVUi.page(a",code(TOOLS/module))
        for marker in ("install_tv_theme(root)","GameLingoTVTheme","GameLingoTVAlert","state_focused","gamelingo_tv_focus"):
            self.assertIn(marker,wiring)
        self.assertIn("if(television) ViewGroup.LayoutParams.MATCH_PARENT",code(TOOLS/"GameLingoSettings.kt"))

    def test_tv_diagnostics_distinguishes_stopped_resources_from_last_session_counters(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        service = capture.split("class GameLingoScreenTranslationService",1)[1]
        cleanup = service.split("override fun onDestroy()",1)[1].split("companion object",1)[0]
        for marker in ("releaseDisplay.isSuccess", "releaseProjection.isSuccess",
                       "stopForeground(STOP_FOREGROUND_REMOVE)", "stopForegroundResult.isSuccess"):
            self.assertIn(marker, cleanup)
        self.assertIn("reportView.text = report()", service)
        self.assertIn("reportView.removeCallbacks(reportRefresh)", service)

    def test_tv_standalone_requires_consent_and_uses_only_ocr(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        wiring = code(TOOLS/"direct_tv_mode.py")
        self.assertFalse((TOOLS/"GameLingoSubtitleAccessibility.kt").exists())
        for forbidden in ("takeScreenshot(", "dispatchGesture(", "performAction(", "AudioRecord", "RECORD_AUDIO",
                          "GameLingoSubtitleAccessibility", "Pouze text titulků"):
            self.assertNotIn(forbidden, capture)
        for marker in ("resultCode == RESULT_OK", "HandlerThread", "setOnImageAvailableListener",
                       "acquireLatestImage", "finally { image.close() }", "plane.rowStride",
                       "received.incrementAndGet()", "decoded.incrementAndGet()", "ticket == epoch",
                       "Spustit překlad TV", "Diagnostika samostatného OCR", "Test překryvu"):
            self.assertIn(marker, capture)
        for marker in ("ACTION_TEST_OVERLAY", "ACTION_STOP_TEST_OVERLAY", 'showText("Testovací titulek", 0L)',
                       "TYPE_APPLICATION_OVERLAY", "FLAG_NOT_TOUCHABLE", "recordOverlayTest"):
            self.assertIn(marker, wiring)
        self.assertNotIn("android.permission.BIND_ACCESSIBILITY_SERVICE", wiring)
        self.assertIn('android:foregroundServiceType="mediaProjection"', wiring)
        self.assertIn("GameLingoScreenTranslationService.running ||", wiring)
        self.assertIn("Obsolete accessibility implementation included", code(TOOLS/"verify_tv_ui_apk.py"))

    def test_tv_device_management_uses_branded_picker_for_all_device_lists(self):
        manager=code(TOOLS/"GameLingoDeviceCenter.kt")
        self.assertIn("GameLingoTVUi.select(c, heading, options, onSelect = onSelect)",manager)
        for marker in ('chooseDevice(c, "Přidat zařízení"', "chooseDevice(c, ps.name",
                       "chooseDevice(c, xbox.jmeno", "chooseDevice(c, tv.jmeno"):
            self.assertIn(marker,manager)

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
