#!/usr/bin/env python3
"""Check actual DEX payloads, not just the Gradle declarations."""
from pathlib import Path
import argparse
import re
import zipfile

MARKERS = (b"Lcom/google/mlkit/nl/translate/",
           b"Lcom/google/android/gms/internal/mlkit_translate")


def verify(path, mode):
    if mode not in ("tv", "mobile"):
        raise ValueError("Unknown APK mode")
    with zipfile.ZipFile(path) as apk:
        names = [name for name in apk.namelist()
                 if re.fullmatch(r"classes(?:[0-9]+)?\.dex", name)]
        if not names:
            raise AssertionError("APK contains no DEX payload")
        found = []
        for name in names:
            dex = apk.read(name)
            if not dex.startswith(b"dex\n"):
                raise AssertionError(f"Invalid DEX header: {name}")
            if any(marker in dex for marker in MARKERS):
                found.append(name)
        if found:
            raise AssertionError(f"{mode} APK contains ML Kit Translation references: " + ", ".join(found))
    print(f"{mode}: translation SDK DEX check passed ({path.name})")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("tv", "mobile"))
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    verify(args.apk, args.mode)
