#!/usr/bin/env python3
"""Check the actual TV APK carries the new UI and screen capture implementation."""
import argparse
import re
import zipfile
from pathlib import Path

REQUIRED = (
    "Lcz/titulky/tv/GameLingoTVFit;",
    "Lcz/titulky/tv/GameLingoTVSettings;",
    "Lcz/titulky/tv/GameLingoTVProfiles;",
    "Lcz/titulky/tv/GameLingoScreenConsentActivity;",
    "Lcz/titulky/tv/GameLingoScreenTranslationService;",
    "Lcz/titulky/tv/GameLingoSubtitleAccessibility;",
    "Automaticky · text + OCR",
    "Pouze text titulků",
    "Pouze OCR obrazovky",
    "Párovací QR kód",
    "Spárované telefony",
    "Vyber zdroj překladu",
    "Samostatná TV",
    "Náhled rozložení · 4 nezávislé oblasti",
    "Neprůhlednost pozadí",
)

def verify(apk: Path):
    with zipfile.ZipFile(apk) as archive:
        files = [n for n in archive.namelist() if re.fullmatch(r"classes(?:[0-9]+)?\.dex", n)]
        if not files:
            raise AssertionError("TV APK contains no DEX")
        data = b"".join(archive.read(n) for n in files)
        missing = [value for value in REQUIRED if value.encode("utf8") not in data]
        if missing:
            raise AssertionError("TV APK missing new UI markers: " + ", ".join(missing))
    print("TV APK contains the new UI, QR, profiles and consent-based screen capture code")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    verify(parser.parse_args().apk)
