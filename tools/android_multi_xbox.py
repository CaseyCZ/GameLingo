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
    replacement='''                    val prefs = getSharedPreferences("gamelingo_devices", MODE_PRIVATE)
                    val hidden = prefs.getStringSet("hiddenXboxIds", emptySet()).orEmpty()
                    val nalezene = seznam.getOrNull().orEmpty().filterNot { it.id in hidden }
                    xboxRows.removeAllViews()
                    fun zvolXbox(x: XboxSpojeni.Konzole) {
                        if (RemotePlayService.aktivni) {
                            hledaniStav.text = GameLingoLanguages.ui(this, "Nejdřív zastav překlad.")
                            return
                        }
                        xboxNalezena = x
                        prefs.edit().putString("selectedXboxId", x.id).putBoolean("cameraSourceSelected", false).apply()
                        XboxUcet.ulozKonzoli(this, x.id, x.jmeno)
                        RemotePlayService.prepni(this, true)
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
                        else -> {
                            val savedId = prefs.getString("selectedXboxId", null)
                            val predvybrany = nalezene.indexOfFirst { it.id == savedId }
                            hledaniStav.text = GameLingoLanguages.ui(this, "Nalezeno:") + " " + nalezene.size
                            fun renderXboxRows() {
                                xboxRows.removeAllViews()
                                for (x in nalezene) {
                                    val selected = xboxNalezena?.id == x.id
                                    val state = x.stav.ifBlank { "Stav konzole není známý" }
                                    xboxRows.addView(GameLingoVisualKit.detailRow(this, "gamecontroller",
                                        (if (selected) "✓ " else "") + x.jmeno,
                                        GameLingoLanguages.ui(this, state)) {
                                        zvolXbox(x)
                                        renderXboxRows()
                                    })
                                }
                            }
                            if (predvybrany >= 0) zvolXbox(nalezene[predvybrany])
                            renderXboxRows()
                        }
                    }'''
    source=source[:start]+replacement+source[end:]
    anchor='        fun hledej() {\n            xboxNalezena = null'
    if source.count(anchor)!=1:
        raise RuntimeError("Xbox list layout anchor changed")
    source=source.replace(anchor,'''        val xboxRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        k2.addView(xboxRows)
        var xboxListGeneration = 0L
        fun hledej() {
            xboxRows.removeAllViews()
            val generation = ++xboxListGeneration
            xboxNalezena = null''',1)
    anchor='                    if (obrazovka != "xbox") return@runOnUiThread\n                    val prefs'
    if source.count(anchor)!=1:
        raise RuntimeError("Xbox discovery callback anchor changed")
    source=source.replace(anchor,'                    if (obrazovka != "xbox" || generation != xboxListGeneration || isFinishing || isDestroyed) return@runOnUiThread\n                    val prefs',1)
    anchor='        k3.addView(pripojStav, odsazeni(top = 6))'
    if source.count(anchor)!=1:
        raise RuntimeError("Xbox finish action anchor changed")
    source=source.replace(anchor,'''        k3.addView(tlacitko(GameLingoLanguages.ui(this, "Hotovo"), 18f) {
            val selected = xboxNalezena
            if (RemotePlayService.aktivni) {
                pripojStav.text = GameLingoLanguages.ui(this, "Nejdřív zastav překlad.")
            } else if (selected == null || !XboxUcet.prihlasen(this)) {
                pripojStav.text = GameLingoLanguages.ui(this, "Vybrat konzoli")
            } else {
                xboxRelace?.zastav()
                xboxRelace = null
                ukazHlavni()
            }
        }, obal(top = 8))
        k3.addView(pripojStav, odsazeni(top = 6))''',1)
    path.write_text(source,encoding="utf-8")
    print("Android GameLingo: all Xbox consoles selectable")

if __name__=="__main__":
    main()
