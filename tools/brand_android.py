#!/usr/bin/env python3
"""Rebrand the pinned Android/TV PlayCaption build without altering protocols."""
import pathlib
import re
import sys

def exactly_one(path, old, new):
    value=path.read_text(encoding="utf8")
    if value.count(old)!=1:
        raise RuntimeError(f"{path}: expected one patch anchor {old[:90]!r}, found {value.count(old)}")
    path.write_text(value.replace(old,new,1),encoding="utf8")

def main():
    if len(sys.argv)!=3 or sys.argv[1] not in ("mobile","tv"):
        raise SystemExit("Usage: brand_android.py mobile|tv <upstream-checkout>")
    mode,root=sys.argv[1],pathlib.Path(sys.argv[2])
    gradle=root/"app/build.gradle.kts"
    strings=root/"app/src/main/res/values/strings.xml"
    main=root/"app/src/main/java/cz/titulky/tv/MainActivity.kt"
    if mode=="mobile":
        exactly_one(gradle,'applicationId = "cz.titulky.tv"',
                    'applicationId = "cz.caseycz.gamelingo.mobile"')
        exactly_one(strings,">PlayCaption</string>",">GameLingo</string>")
    else:
        # BuildType has an applicationIdSuffix, not an applicationId property.
        # Set the independent TV ID in defaultConfig and remove the old suffix.
        exactly_one(gradle,'applicationId = "cz.titulky.tv"',
                    'applicationId = "cz.caseycz.gamelingo.tv"')
        exactly_one(gradle,'applicationIdSuffix = ".iphonebridge"',
                    '// Independent GameLingo TV application ID')
        exactly_one(gradle,'versionNameSuffix = "-iphone-tv-test"',
                    'versionNameSuffix = "-tv"')
        exactly_one(strings,">PlayCaption iPhone TV TEST</string>",">GameLingo TV</string>")
    exactly_one(gradle,'versionName = "0.22"',
                'versionName = "0.4.0-beta.1"')
    exactly_one(gradle, "versionCode = 35", "versionCode = 404")
    original=main.read_text(encoding="utf8")
    branded=original.replace('"PlayCaption"', '"GameLingo"')
    branded=branded.replace("PlayCaption – hlavní obrazovka", "GameLingo – hlavní obrazovka")
    if mode=="tv":
        branded=branded.replace("TCL C835", "Google TV")
        branded=branded.replace("TCL Android TV", "Google TV")
    # Remove the upstream developer donation QR to avoid displaying its
    # bank account as if donations were accepted by our new project.
    branded,n=re.subn(r'const val PODPORA_QR = "[^"]+"',
                      'const val PODPORA_QR = ""',branded,count=1)
    if n!=1: raise RuntimeError("Expected exactly one original support QR")
    main.write_text(branded,encoding="utf8")
    print(f"Branded independent GameLingo {mode} app")
if __name__=="__main__":
    main()
