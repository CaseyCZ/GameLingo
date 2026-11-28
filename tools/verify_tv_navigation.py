#!/usr/bin/env python3
"""Check the real generated TV source after all pinned build transformations."""
import sys
from pathlib import Path
import re

folder = Path(sys.argv[1]) / "app/src/main/java/cz/titulky/tv"
main = (folder / "MainActivity.kt").read_text(encoding="utf-8")
settings = (folder / "GameLingoTVSettings.kt").read_text(encoding="utf-8")
ui = (folder / "GameLingoTVUi.kt").read_text(encoding="utf-8")

def section(text, start, end):
    return text.split(start, 1)[1].split(end, 1)[0]

boot = section(main, "override fun onCreate(", "override fun onNewIntent(")
resume = section(main, "override fun onResume()", "override fun onPause()")
back = section(main, "override fun onBackPressed()", "// ------------------------------------------------------------------")
return_home = section(main, "private fun zpetNaVyber()", "private fun zastavPrijemANavrat()")
stop = section(main, "private fun zastavPrijemANavrat()", "/** Velká volba")
assert "ukazUvod()" in boot and "hratSTelefonem()" not in boot
assert 'if (obrazovka == "hlavni") ukazHlavni()' in resume
assert "hratSTelefonem()" not in resume
assert '"prijem" -> zpetNaVyber()' in back
assert "finish()" not in back and "PrijemService.zastav(this)" not in back
assert "ukazHlavni()" in return_home and "PrijemService.zastav" not in return_home
assert "PrijemService.zastav(this)" in stop and "zpetNaVyber()" in stop
assert 'if (!telefon && PrijemService.bezi) { ukazPrijem(); return }' not in main
assert re.search(r'val zastav = GameLingoTVUi\.action\(this, GameLingoLanguages\.ui\(this, "Zpět"\)\)\s*\{\s*zpetNaVyber\(\)', main)
receiver = section(main, "private fun ukazPrijem()", "private val obnova:")
assert 'GameLingoTVUi.header(this, logo, zastav, GameLingoHelp.button(this, "tv"))' in receiver
assert "paticka.addView(zastav)" not in receiver
assert re.search(r'GameLingoLanguages\.ui\(this, "Zastavit příjem"\)\)\s*\{\s*zastavPrijemANavrat\(\)', main)
assert 'val back = GameLingoTVUi.action(a, GameLingoLanguages.ui(a, "Zpět")) { dialog.dismiss() }' in settings
assert 'dialog.setOnDismissListener {' in settings and 'if (!reopeningForScale && !openingDevices && !a.isFinishing && !a.isDestroyed) onUpdate()' in settings
assert 'openingDevices = true; dialog.dismiss(); onDevices()' in settings
assert 'val back=action(a,GameLingoLanguages.ui(a,"Zpět")){dialog.dismiss()}' in ui
assert 'dialog.setOnDismissListener { onClose() }' in ui
phone_receiver = section(main, "private fun hratSTelefonem()", "private fun ukazPrijem()")
assert "GameLingoScreenTranslationService.running || RemotePlayService.aktivni" in phone_receiver
assert "GameLingoScreenTranslationService.stop(this)" not in phone_receiver
assert phone_receiver.index("return") < phone_receiver.index("PrijemService.spust(this)")
print("TV_NAVIGATION_SOURCE_CHECKS passed: startup, resume, remote back, on-screen back, explicit stop, settings dismissal, nested pages")
