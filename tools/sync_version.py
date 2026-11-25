#!/usr/bin/env python3
"""Synchronize release metadata from shared/version.json; historical docs stay untouched."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ("build-gamelingo.yml", "build-ios-gamelingo.yml", "qa-android-v05.yml",
             "qa-android-mobile-v05.yml", "publish-gamelingo-current.yml")

def expected_files():
    release = json.loads((ROOT / "shared/version.json").read_text(encoding="utf8"))
    version, build = release["version"], release["build"]
    if not re.fullmatch(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)", version):
        raise ValueError("Expected a semantic release version")
    if type(build) is not int or not 1 <= build <= 2000:
        raise ValueError("Invalid release build range")
    path = ROOT / "ios/GameLingoIOS/project.yml"
    text = path.read_text(encoding="utf8")
    for key, value in (("CFBundleShortVersionString", version), ("CFBundleVersion", str(build))):
        text, count = re.subn(r'(?m)(^\s*' + key + r': ")[^"]+(")', lambda m: m[1] + value + m[2], text)
        if count != 1:
            raise ValueError("Missing or duplicate " + key)
    yield path, text
    for name in WORKFLOWS:
        path = ROOT / ".github/workflows" / name
        text = path.read_text(encoding="utf8")
        # Only versioned GameLingo package names, tags, explicit plist validation
        # and release titles. Do not change dependency versions or workflow IDs.
        pattern = r'(?<=-v)\d+\.\d+\.\d+(?=-dev)|(?<=v)\d+\.\d+\.\d+(?=-test-)|(?<=GameLingo )\d+\.\d+\.\d+(?= TEST)|(?<=QA v)\d+\.\d+\.\d+(?=:)|(?<=test "\$VERSION" = ")\d+\.\d+\.\d+(?=")'
        text, count = re.subn(pattern, version, text)
        if count == 0:
            raise ValueError("No version anchors in " + name)
        yield path, text

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    dirty = []
    for path, expected in expected_files():
        if path.read_text(encoding="utf8") != expected:
            dirty.append(str(path.relative_to(ROOT)))
            if not args.check:
                path.write_text(expected, encoding="utf8")
    if args.check and dirty:
        raise SystemExit("Version mismatch: " + ", ".join(dirty))
    print("Version metadata synchronized")

if __name__ == "__main__":
    main()
