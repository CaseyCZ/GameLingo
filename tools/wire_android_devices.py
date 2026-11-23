#!/usr/bin/env python3
"""Wire GameLingo's device library into the existing Android/TV Remote Play core.

This moves device management to GameLingo-owned Kotlin views. It preserves the
actual Sony/Microsoft login, Chiaki PS5 keys, Xbox WebRTC, TV discovery/receiver.
"""
from pathlib import Path
import sys

SRC = Path("app/src/main/java/cz/titulky/tv")

def one(data, before, after, file):
    count=data.count(before)
    if count!=1:
        raise RuntimeError(f"{file}: expected one device UI anchor, found {count}: {before[:110]}")
    return data.replace(before,after,1)

def main():
    if len(sys.argv)!=2:
        raise SystemExit("Usage: wire_android_devices.py <upstream>")
    upstream=Path(sys.argv[1])
    folder=upstream/SRC
    for name in ("GameLingoDeviceCenter.kt","GameLingoPlayStationConsoles.kt"):
        original=Path(__file__).with_name(name)
        (folder/name).write_text(original.read_text(encoding="utf-8"),encoding="utf-8")

    mainFile=folder/"MainActivity.kt"
    code=mainFile.read_text(encoding="utf-8")

    def manager():
        return '''GameLingoDeviceCenter.show(this,
                    onXbox = { ukazPripojeniXbox() },
                    onPlayStation = { ukazPripojeni() },
                    onTV = { vyberVystupTv() },
                    onUpdated = { ukazHlavni() })'''

    # Mobile and TV share the PSN source, but the TV has a different
    # dashboard and no phone-only 'druhy' tiles. Never require a mobile
    # anchor inside the Google TV branch.
    mobile_dashboard = 'druhy.addView(akce("▭", "Zařízení")' in code
    tv_dashboard = '"Překlad z telefonu"' in code and '"Přímo na TV"' in code
    if mobile_dashboard:
        code=one(code,
            '''druhy.addView(akce("▭", "Zařízení") {
                    vyberVystupTv()
                }''',
            '''druhy.addView(akce("▭", "Zařízení") {
                    '''+manager()+'''
                }''',"MainActivity devices tile")

        code=one(code,
            '''Triple("▭\\nTV", 3, { vyberVystupTv() })''',
            '''Triple("▭\\nZařízení", 3, { '''+manager()+''' })''',
            "MainActivity bottom navigation")

    elif tv_dashboard:
        print("Google TV landing screen uses its own GameLingo device actions")
    else:
        raise RuntimeError("Neither Android phone nor Google TV dashboard recognized")

    code=one(code,
        '''                            if (ok) {
                                AutoStart.nastavZapnuto(this, true)''',
        '''                            if (ok) {
                                if (ip != null)
                                    GameLingoPlayStationConsoles.capture(this,
                                        "PlayStation", ip)
                                AutoStart.nastavZapnuto(this, true)''',
        "PlayStation registration success")

    mainFile.write_text(code,encoding="utf-8")

    profiles=folder/"TvProfily.kt"
    text=profiles.read_text(encoding="utf-8")
    insert='''    fun odeber(id: String) {
        val entry = najdi(id) ?: return
        val k = klic(id)
        val ids = prefs.getStringSet("ids", emptySet()).orEmpty() - id
        val editor = prefs.edit()
            .putStringSet("ids", ids)
            .remove("$k.jmeno").remove("$k.ip").remove("$k.platforma")
        if (posledniId == id) editor.remove("posledni")
        editor.apply()
        if (aktivniId == id) aktivniId = null
    }

'''
    text=one(text,"    fun vyber(id: String) {",insert+"    fun vyber(id: String) {","TvProfily device removal")
    profiles.write_text(text,encoding="utf-8")
    print("GameLingo Android/TV: independent device center + secure multi-PS5 saved console switching installed")

if __name__=="__main__":
    main()
