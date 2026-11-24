#!/usr/bin/env python3
"""Replace the Android Xbox firstOrNull selection with all-device choice."""
from pathlib import Path
import sys

def main():
    if len(sys.argv)!=2:
        raise SystemExit("Usage: android_multi_xbox.py <upstream>")
    path=Path(sys.argv[1])/"app/src/main/java/cz/titulky/tv/MainActivity.kt"
    source=path.read_text(encoding="utf-8")
    start_text="                    val x = seznam.getOrNull()?.firstOrNull()\n"
    end_text='                    hledaniStav.setTextColor(if (x != null) Color.parseColor("#7DFF9A") else Color.parseColor("#FF8A8A"))'
    if source.count(start_text)!=1 or source.count(end_text)!=1:
        raise RuntimeError("Xbox selection anchor changed; refuse unsafe patch")
    start=source.index(start_text)
    end=source.index(end_text,start)+len(end_text)
    replacement='''                    val nalezene = seznam.getOrNull().orEmpty()
                    fun zvolXbox(x: XboxSpojeni.Konzole) {
                        xboxNalezena = x
                        getSharedPreferences("gamelingo_devices", MODE_PRIVATE)
                            .edit().putString("selectedXboxId", x.id).apply()
                        hledaniStav.text = "✔ " + x.jmeno
                        hledaniStav.setTextColor(Color.parseColor("#35E57D"))
                    }
                    when {
                        seznam.isFailure -> {
                            xboxNalezena = null
                            hledaniStav.text = "✘ " +
                                (seznam.exceptionOrNull()?.message ?: "Chyba připojení")
                            hledaniStav.setTextColor(Color.parseColor("#FF8A8A"))
                        }
                        nalezene.isEmpty() -> {
                            xboxNalezena = null
                            hledaniStav.text = "Xbox nenalezen"
                            hledaniStav.setTextColor(Color.parseColor("#FF8A8A"))
                        }
                        nalezene.size == 1 -> zvolXbox(nalezene.single())
                        else -> {
                            val savedId = getSharedPreferences("gamelingo_devices", MODE_PRIVATE)
                                .getString("selectedXboxId", null)
                            val jmena = nalezene.map { it.jmeno }.toTypedArray()
                            val predvybrany = nalezene.indexOfFirst { it.id == savedId }
                            if (predvybrany >= 0) {
                                // The user already chose this device in the central
                                // library. Respect it instead of asking every time.
                                zvolXbox(nalezene[predvybrany])
                            } else {
                                hledaniStav.text = "Vyber Xbox (" + nalezene.size + ")"
                                android.app.AlertDialog.Builder(this)
                                    .setTitle("Vyber konzoli")
                                    .setSingleChoiceItems(jmena, -1) { dialog, index ->
                                        zvolXbox(nalezene[index])
                                        dialog.dismiss()
                                    }
                                    .setNegativeButton("Zrušit", null)
                                    .show()
                            }
                        }
                    }'''
    path.write_text(source[:start]+replacement+source[end:],encoding="utf-8")
    print("Android GameLingo: all Xbox consoles selectable")

if __name__=="__main__":
    main()
