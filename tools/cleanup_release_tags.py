#!/usr/bin/env python3
"""Plan deletion of older unreferenced GameLingo test tags only."""
import json
import re
import sys
from pathlib import Path

TEST_TAG = re.compile(r"v(\d+)\.(\d+)\.(\d+)-test-[0-9a-f]{7,40}(?:-r\d+)?")

def test_version(tag):
    match = TEST_TAG.fullmatch(tag)
    return tuple(map(int, match.groups())) if match else None

def plan_cleanup(releases, tags):
    published = [r for r in releases if not r["draft"]]
    if len(published) != 1:
        raise ValueError("Expected exactly one published GameLingo release")
    current = published[0]
    version = test_version(current["tag_name"])
    if version is None:
        raise ValueError("Current release is not a recognized GameLingo test tag")
    semantic = ".".join(map(str, version))
    expected = {
        f"GameLingo-iOS-v{semantic}-dev-unsigned.ipa",
        f"GameLingo-Android-v{semantic}-dev-TEST.apk",
        f"GameLingo-GoogleTV-v{semantic}-dev-TEST.apk",
        "SHA256SUMS.txt",
        "GameLingo-TV-update.json",
        "GameLingo-Android-update.json",
        "GameLingo-iOS-update.json",
    }
    assets = current["assets"]
    if (len(assets) != len(expected) or {a["name"] for a in assets} != expected
            or any(a["state"] != "uploaded" or a["size"] <= 0 for a in assets)):
        raise ValueError("Current release does not have all seven uploaded assets")
    protected = {r["tag_name"] for r in releases}
    tags = set(tags)
    if current["tag_name"] not in tags:
        raise ValueError("Current release tag is missing")
    return sorted(tag for tag in tags - protected
                  if test_version(tag) is not None and test_version(tag) < version)

def main():
    pages = json.loads(Path(sys.argv[1]).read_text(encoding="utf8"))
    releases = [release for page in pages for release in page]
    tags = Path(sys.argv[2]).read_text(encoding="utf8").splitlines()
    for tag in plan_cleanup(releases, tags):
        print(tag)

if __name__ == "__main__":
    main()
