#!/usr/bin/env python3
"""Fail closed when core GameLingo end-to-end navigation is accidentally removed.

This is a SOURCE-CONTRACT check, not an emulator, hardware or gameplay test.
Xbox and PS4/PS5 authentication and video must additionally pass physical tests.
"""
from pathlib import Path
import argparse
import sys

ROOT = Path(__file__).resolve().parent.parent


def contains(path: Path, *parts: str):
    if not path.is_file():
        raise AssertionError(f"Missing: {path}")
    src = path.read_text(encoding="utf-8")
    missing = [p for p in parts if p not in src]
    if missing:
        raise AssertionError(f"{path}: missing contracts: {missing[:4]}")
    print("OK", path)


def ios():
    home = ROOT / "ios/GameLingoIOS/App/GameLingoHome.swift"
    contains(home,
        "GameLingoSetupView", "GameLingoDevicesPanel",
        "startCurrentSource()", "model.start(ps)", "model.start(xbox)",
        "model.playStation.submitLoginPIN", "Spustit překlad")
    contains(ROOT / "ios/GameLingoIOS/App/GameLingoSetupView.swift",
        "GLConsoleFamily.allCases", "GLSubtitleOutput.allCases",
        "consoleRegistered", "Dokončit nastavení")
    contains(ROOT / "ios/GameLingoIOS/App/GameLingoDevices.swift",
        "refreshPlayStation", "refreshXbox", "addTelevision",
        "addXbox", "removeConsole", "removeTelevision")
    contains(ROOT / "ios/GameLingoIOS/App/PlayCaptionIOSApp.swift",
        "func start(_ console: RegisteredConsole)", "func start(_ console: XboxConsole)",
        "private func processFrame(", "playStation.start(",
        "self.devices.subtitleOutput == .phone")
    contains(ROOT / "ios/GameLingoIOS/App/PlayStation/GameLingoPlayStation.swift",
        "RemotePlaySession(", "onDecodedFrame", "CVPixelBuffer",
        "registered", "registration.start(", "submitLoginPIN")
    contains(ROOT / "ios/GameLingoIOS/App/PlayStation/GameLingoPlayStationSetup.swift",
        "GameLingoPSNLookup", "ps.register(", "ps.discover()", "ForEach(registered.consoles)")
    contains(ROOT / "ios/GameLingoIOS/App/PlayCaptionTV.swift",
        "PCPAIR1", "receiverCode", "sendChoices", "sendSubtitle")


def android(root: Path, mode: str):
    base = root / "app/src/main/java/cz/titulky/tv"
    contains(base / "MainActivity.kt",
        "GameLingoDeviceCenter.show", "GameLingoPlayStationConsoles.capture",
        "Vyber konzoli")
    if mode == "mobile":
        contains(base / "MainActivity.kt",
            "GameLingoFirstRun.show", "GameLingoNavigation.create",
            "GameLingoVisualKit.brandHeader(this)",
            "GameLingoAbout.content(this)", "onDevices = { openDevices() }")
        contains(base / "GameLingoNavigation.kt",
            "GameLingoUIContract.tabs", "onSettings: () -> Unit")
        contains(base / "GameLingoUIContract.kt",
            '"Domů"', '"Obrazovka"', '"Konzole"', '"Nastavení"', '"O aplikaci"')
        contains(base / "GameLingoSettings.kt",
            "GameLingoUIContract.settingsSections", "GameLingoDiagnostics.show")
        contains(base / "GameLingoRegions.kt",
            "Čtyři nezávislé oblasti", "onEdit()")
        contains(base / "GameLingoAbout.kt", "AGPL-3.0")
        contains(base / "GameLingoVisualKit.kt", "fun deviceCard(", "fun navigationTile(", "fun hero(")
        contains(base / "GameLingoLanguages.kt", "fun game(", "fun target(", "fun choose(")
        contains(base / "Prekladac.kt", "zdrojovyJazyk", "nastavZdroj(")
    else:
        contains(base / "MainActivity.kt",
            "Překlad z telefonu", "Přímo na TV",
            "GameLingoTVUi.tile(", "nextFocusRightId", "nextFocusLeftId")
        contains(base / "GameLingoTVUi.kt",
            "setOnFocusChangeListener", "isFocusable = true",
            "showWelcome(", "showSettings(")
    text = (base / "MainActivity.kt").read_text(encoding="utf8")
    if '"PlayCaption"' in text or 'PlayCaption – hlavní obrazovka' in text:
        raise AssertionError("Original PlayCaption brand remains user-visible")
    if 'seznam.getOrNull()?.firstOrNull()' in text:
        raise AssertionError("Android still selects first Xbox instead of prompting")
    contains(base / "GameLingoFirstRun.kt",
             "PlayStation 5 / 4", "Google TV / Android TV", "Xbox")
    contains(base / "GameLingoDeviceCenter.kt",
             "GameLingoPlayStationConsoles.list", "XboxSpojeni.konzole",
             "TvProfily", "Přidat zařízení")
    contains(base / "GameLingoPlayStationConsoles.kt",
             "AndroidKeyStore", "AES/GCM/NoPadding", "fun select(")
    if mode == "tv":
        contains(base / "Propojeni.kt", "IPhoneTVPairing.verify")
        contains(base / "OverlayService.kt", "EXTRA_VYSTUP_VOLEB")


if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--android-root",type=Path)
    parser.add_argument("--mode",choices=("mobile","tv"),default="mobile")
    args=parser.parse_args()
    try:
        ios()
        if args.android_root:
            android(args.android_root,args.mode)
    except AssertionError as exc:
        print("GAME LINGO SOURCE AUDIT FAILED:", exc, file=sys.stderr)
        raise SystemExit(1)
    print("GameLingo source contracts pass. Physical Remote Play and OCR still require devices.")
