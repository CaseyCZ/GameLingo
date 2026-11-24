#!/usr/bin/env python3
"""Inventory the generated Google TV application, not just the overlay kit."""
import argparse
import re
from pathlib import Path
import xml.etree.ElementTree as ET

def main():
    p=argparse.ArgumentParser()
    p.add_argument("root",type=Path)
    p.add_argument("--output",required=True,type=Path)
    a=p.parse_args()
    src=a.root/"app/src/main"
    manifest=ET.parse(src/"AndroidManifest.xml").getroot()
    ns="{http://schemas.android.com/apk/res/android}"
    app=manifest.find("application")
    errors=[]
    if app is None or app.get(ns+"theme")!="@style/GameLingoTVTheme":
        errors.append("Missing GameLingo TV app theme")
    theme=ET.parse(src/"res/values/gamelingo_tv_theme.xml").getroot()
    styles={e.get("name") for e in theme.findall("style")}
    for name in ("GameLingoTVTheme","GameLingoTVAlert","GameLingoTVDialog","GameLingoTVFullscreen"):
        if name not in styles: errors.append("Missing "+name)
    lines=["# Generated GameLingo TV UI inventory",
           "", "This is a source inventory, not a physical-screen or visual test.",
           "", "## Manifest activities", ""]
    if app is not None:
        for act in app.findall("activity"):
            lines.append("- "+str(act.get(ns+"name"))+" — "+str(act.get(ns+"theme","inherits app theme")))
    lines+=["","## Kotlin UI entry points and dialogs",""]
    pattern=re.compile(r'AlertDialog\.Builder|ProgressDialog|(?<![A-Za-z])Dialog\(|setContentView\(|GameLingoTVUi\.(?:page|select)\(|WebView\(')
    files=sorted((src/"java").rglob("*.kt"))
    counts=0
    for f in files:
        source=f.read_text(encoding="utf8")
        rows=[]
        for i,line in enumerate(source.splitlines(),1):
            if pattern.search(line):
                rows.append((i,line.strip()))
                if "AlertDialog.Builder(" in line and "R.style.GameLingoTVAlert" not in line:
                    errors.append(f"Unthemed app AlertDialog: {f.name}:{i}")
                if re.search(r'\bDialog\([^,\n]+,\s*android\.R\.style\.Theme_(?:Material_NoActionBar_Fullscreen|DeviceDefault_NoActionBar)',line):
                    errors.append(f"Legacy explicit Dialog theme: {f.name}:{i}")
        if rows:
            lines+=["### "+str(f.relative_to(src)),""]
            for i,line in rows:
                lines.append(f"- L{i}: {line}")
            lines.append("")
            counts+=len(rows)
    lines+=["","## Summary","",f"- Kotlin files scanned: {len(files)}",f"- UI entry references: {counts}",
            f"- Theme violations: {len(errors)}", "- System consent, browser/OAuth, keyboard and external screens are platform-owned.",
            "- Physical TCL validation remains required."]
    lines+=["","## Violations",""]+([f"- {e}" for e in errors] or ["None detected by this static check."])
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text("\n".join(lines)+"\n",encoding="utf8")
    print(f"TV UI inventory: {len(files)} Kotlin files; {counts} UI references; {len(errors)} violations")
    if errors: raise SystemExit("\n".join(errors))
if __name__=="__main__":
    main()
