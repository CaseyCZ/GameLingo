#!/usr/bin/env python3
"""One source for semantic release version and monotonically increasing release counter."""
import argparse
import json
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION_FILE = ROOT / "shared/version.json"
PROJECT = ROOT / "ios/GameLingoIOS/project.yml"
TOKENS = {"CFBundleShortVersionString": "$(MARKETING_VERSION)",
          "CFBundleVersion": "$(CURRENT_PROJECT_VERSION)"}
WORKFLOWS = ("build-gamelingo.yml", "build-ios-gamelingo.yml", "qa-android-v05.yml",
             "qa-android-mobile-v05.yml", "publish-gamelingo-current.yml")

def validate(release):
    version, build = release["version"], release["build"]
    if not isinstance(version, str) or not re.fullmatch(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)", version):
        raise ValueError("Expected a semantic release version")
    if type(build) is not int or not 1 <= build <= 2000:
        raise ValueError("Invalid release build range")
    return version, build

def load_release():
    release = json.loads(VERSION_FILE.read_text(encoding="utf8"))
    validate(release)
    return release

def bumped(release, part):
    version, build = validate(release)
    major, minor, patch = map(int, version.split("."))
    if part == "major":
        major, minor, patch = major + 1, 0, 0
    elif part == "minor":
        minor, patch = minor + 1, 0
    elif part == "patch":
        patch += 1
    else:
        raise ValueError("Invalid version component")
    result = {"version": f"{major}.{minor}.{patch}", "build": build + 1}
    validate(result)
    return result

def ios_project(text, release):
    version, build = validate(release)
    for (key, token), value in zip(TOKENS.items(), (version, str(build))):
        anchor = f'{key}: "{token}"'
        if text.count(anchor) != 1:
            raise ValueError("Missing or duplicate iOS version placeholder: " + key)
        text = text.replace(anchor, f'{key}: "{value}"', 1)
    return text

def check(release):
    ios_project(PROJECT.read_text(encoding="utf8"), release)
    for name in WORKFLOWS:
        text = (ROOT / ".github/workflows" / name).read_text(encoding="utf8")
        if "GAMELINGO_VERSION" not in text or "--github-env" not in text:
            raise ValueError("Missing shared version in " + name)
        if re.search(r'GameLingo[^\n]*-v\d+\.\d+\.\d+-dev|v\d+\.\d+\.\d+-test-|GameLingo \d+\.\d+\.\d+ TEST', text):
            raise ValueError("Hardcoded release version in " + name)
    print("Shared version and dynamic workflow contracts validated")

def main():
    parser = argparse.ArgumentParser()
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--check", action="store_true")
    action.add_argument("--github-env", action="store_true")
    action.add_argument("--prepare-ios", action="store_true")
    action.add_argument("--bump", choices=("major", "minor", "patch"))
    args = parser.parse_args()
    release = load_release()
    version, build = validate(release)
    if args.github_env:
        with open(os.environ["GITHUB_ENV"], "a", encoding="utf8") as env:
            env.write(f"GAMELINGO_VERSION={version}\nGAMELINGO_BUILD={build}\n")
    elif args.prepare_ios:
        PROJECT.write_text(ios_project(PROJECT.read_text(encoding="utf8"), release), encoding="utf8")
    elif args.bump:
        result = bumped(release, args.bump)
        VERSION_FILE.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
        print(f'Next version: {result["version"]} (build {result["build"]})')
    else:
        check(release)

if __name__ == "__main__":
    main()
