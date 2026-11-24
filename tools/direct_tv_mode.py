"""Direct-on-TV rendering and OCR; receiver mode and mobile remain unchanged."""
from pathlib import Path

def patch_direct_tv(folder):
    service = folder / "RemotePlayService.kt"
    s = service.read_text(encoding="utf8")
    def one(old, new):
        nonlocal s
        if s.count(old) != 1:
            raise RuntimeError("TV direct anchor changed: " + old[:100])
        s = s.replace(old, new, 1)

    # The local TV uses the same reading queue, but never a remote story ACK.
    one("private fun pozadujePribeh() = telefon && nast.pribehovy && nast.dialogy",
        "private fun pozadujePribeh() = nast.pribehovy && nast.dialogy")
    one("private fun chcePribeh() = pozadujePribeh() && PodporaPribehu.pouzit(\n        Propojeni.Vysilac.cilIp, pribehSpojeni?.takeIf { it.podporujePribeh }?.ip)",
        "private fun chcePribeh() = pozadujePribeh() && (!telefon || PodporaPribehu.pouzit(\n        Propojeni.Vysilac.cilIp, pribehSpojeni?.takeIf { it.podporujePribeh }?.ip))")
    # Only phone story can negotiate a network receiver.
    one("                val ip = Propojeni.Vysilac.cilIp\n                pribehSpojeni?.chyba",
        "                val ip = if (telefon) Propojeni.Vysilac.cilIp else null\n                pribehSpojeni?.chyba")
    one("                        TelefonTitulky.nastav(p?.text ?: \"\", p?.let { it.doMs - ted } ?: 0, p?.let { pribeh.barvy(it.token) })",
        """                        val text = p?.text ?: ""
                        val duration = p?.let { (it.doMs - ted).coerceAtLeast(1L) } ?: 0L
                        val colors = p?.let { pribeh.barvy(it.token) }
                        if (telefon) TelefonTitulky.nastav(text, duration, colors)
                        else ukazLokalne(text, duration, null, 1, colors)""")
    # On TV local story must clear on context reset / stop, not only phone storage.
    one("        if (vycistitTelefon) {\n            TelefonTitulky.nastav(\"\", 0)",
        "        if (vycistitTelefon) {\n            if (telefon) TelefonTitulky.nastav(\"\", 0) else ukazLokalne(\"\", 0, null, 1, null)")
    one("                platnostPrekladu.zrus()\n                TelefonTitulky.nastav(\"\", 0)\n                ui.removeCallbacks(schovatCesky)",
        "                platnostPrekladu.zrus()\n                if (telefon) TelefonTitulky.nastav(\"\", 0) else ukazLokalne(\"\", 0, null, 1, null)\n                ui.removeCallbacks(schovatCesky)")
    one("        if (chcePribeh() && (Propojeni.Vysilac.cilIp == null || pribehZacal)) return\n        if (telefon) { TelefonTitulky.nastav(text, doba, barvyJmen); return }\n        val i = Intent(this, OverlayService::class.java)",
        """        if (bezi && chcePribeh() && (Propojeni.Vysilac.cilIp == null || pribehZacal)) return
        if (telefon) { TelefonTitulky.nastav(text, doba, barvyJmen); return }
        ukazLokalne(text, doba, box, radku, barvyJmen)
    }

    private fun ukazLokalne(text: String, doba: Long, box: FloatArray?,
                            radku: Int, barvyJmen: IntArray?) {
        if (telefon) return
        val i = Intent(this, OverlayService::class.java)""")
    # TV startup restores its own ROI. Validate normalized bounds before crop.
    one("if (telefon && OblastTitulku.rucne) OblastTitulku.oblast.copyOf() else null",
        """if (OblastTitulku.rucne) OblastTitulku.oblast.copyOf().takeIf {
            it.size == 4 && it.all { value -> value.isFinite() && value in 0f..1f } &&
                it[2] > it[0] + 0.10f && it[3] > it[1] + 0.05f
        } else null""")
    one("        ui.post(pribehHlidac)\n        Pady.hlidej(this)",
        "        if (!telefon) { Volby.nacti(this); OblastTitulku.nacti(this) }\n        ui.post(pribehHlidac)\n        Pady.hlidej(this)")
    # YUV PS decoder uses aligned crop just like GL and I420.
    one("""        val odX = (celaSirka * (1 - STRED_SIRKA) / 2).toInt() and 1.inv() // sudé kvůli barvám
        val w = (celaSirka * STRED_SIRKA).toInt()
        val od = (h * (1 - SPODNI_CAST)).toInt() and 1.inv()
        val vyska = h - od""",
        """        val r = rucniOblast()
        val odX: Int; val w: Int; val od: Int; val vyska: Int
        if (r != null) {
            odX = (celaSirka * r[0]).toInt().coerceIn(0, celaSirka - 16) and 1.inv()
            w = ((celaSirka * (r[2] - r[0])).toInt().coerceIn(16, celaSirka - odX)) and 1.inv()
            od = (h * r[1]).toInt().coerceIn(0, h - 16) and 1.inv()
            vyska = ((h * (r[3] - r[1])).toInt().coerceIn(16, h - od)) and 1.inv()
        } else {
            odX = (celaSirka * (1 - STRED_SIRKA) / 2).toInt() and 1.inv()
            w = (celaSirka * STRED_SIRKA).toInt()
            od = (h * (1 - SPODNI_CAST)).toInt() and 1.inv()
            vyska = h - od
        }""")
    # Read the independent yellow region while the ImageReader frame is owned.
    one("                        potvrdObraz(false, relaceObrazu)\n                        val volna",
        """                        potvrdObraz(false, relaceObrazu)
                        if (Volby.zapnuto) runCatching { ctiVolbyYuv(img) }
                            .onFailure { zapis("[CHYBA volby YUV: " + it.javaClass.simpleName + "]") }
                        val volna""")
    one("    private fun ctiVolby(sirka: Int, vyska: Int, precti:",
        """    private fun ctiVolbyYuv(img: android.media.Image) {
        ctiVolby(img.width, img.height) { x, y, w, h ->
            val planes = img.planes
            val buffers = planes.map { it.buffer.duplicate() }
            fun read(index: Int, row: Int, col: Int): Int {
                val p = planes[index]
                return buffers[index].get(row * p.rowStride + col * p.pixelStride).toInt() and 255
            }
            val px = IntArray(w * h)
            for (r in 0 until h) for (c in 0 until w) {
                val yy = read(0, y+r, x+c) - 16
                val uu = read(1, (y+r)/2, (x+c)/2) - 128
                val vv = read(2, (y+r)/2, (x+c)/2) - 128
                val rr = ((298 * yy + 409 * vv + 128) shr 8).coerceIn(0, 255)
                val gg = ((298 * yy - 100 * uu - 208 * vv + 128) shr 8).coerceIn(0, 255)
                val bb = ((298 * yy + 516 * uu + 128) shr 8).coerceIn(0, 255)
                px[r*w+c] = (255 shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
            px
        }
    }

    private fun ctiVolby(sirka: Int, vyska: Int, precti:""")
    # All already-guarded translation success/clear paths share a local renderer.
    if s.count("Propojeni.Vysilac.nastavVolby(") < 5:
        raise RuntimeError("TV choice output anchors missing")
    s = s.replace("Propojeni.Vysilac.nastavVolby(", "zobrazHerniText(")
    one("    private fun ctiVolbyYuv(img: android.media.Image) {",
        """    private var lokalniVolby = false
    private fun zobrazHerniText(items: List<String>, selected: Int) {
        if (telefon) { Propojeni.Vysilac.nastavVolby(items, selected); return }
        lokalniVolby = items.isNotEmpty() && selected != Volby.VYBRANA_TEXT
        OverlayService.posli(this, Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_SET_VOLBY)
            .putExtra(OverlayService.EXTRA_VOLBY, items.toTypedArray())
            .putExtra(OverlayService.EXTRA_VYBRANA, selected)
            .putExtra(OverlayService.EXTRA_OBLAST, Volby.oblast.copyOf())
            .putExtra(OverlayService.EXTRA_RAMECEK, false)
            .putExtra(OverlayService.EXTRA_VYSTUP_VOLEB, GameLingoDirectTVLayout.orange(this)))
    }

    private fun ctiVolbyYuv(img: android.media.Image) {""")
    one("                val pauza = ip != null && Propojeni.Vysilac.aktivniVolby()",
        "                val pauza = if (telefon) ip != null && Propojeni.Vysilac.aktivniVolby() else lokalniVolby")
    # Local reading queue pauses for choices; hide current dialogue while paused.
    one("                } else {\n                    if (p?.token != pribehLokalni) {",
        """                } else {
                    if (!telefon && pauza) {
                        if (pribehLokalni != null) ukazLokalne("", 0, null, 1, null)
                        pribehLokalni = null
                    } else if (p?.token != pribehLokalni) {""")
    one("        bezi = false\n        stav = zprava",
        """        bezi = false
        if (!telefon) {
            zrusPribeh(vycistitTelefon = false)
            ui.removeCallbacks(schovatCesky)
            zobrazHerniText(emptyList(), -1)
            ukazLokalne("", 0, null, 1, null)
        }
        stav = zprava""")
    one("        ui.removeCallbacks(pribehHlidac)\n        zrusPribeh()",
        "        ui.removeCallbacks(pribehHlidac)\n        zrusPribeh()\n        if (!telefon) zobrazHerniText(emptyList(), -1)")
    # Receiver overlay uses explicit local green output when configured; otherwise
    # retain original automatic box positioning.
    one("        if (box != null) {\n            i.putExtra(OverlayService.EXTRA_BOX, box)",
        """        val custom = GameLingoDirectTVLayout.green(this)
        if (custom != null) {
            i.putExtra(OverlayService.EXTRA_VLASTNI, custom)
        } else if (box != null) {
            i.putExtra(OverlayService.EXTRA_BOX, box)""")
    service.write_text(s, encoding="utf8")
    module = Path(__file__).with_name("GameLingoDirectTVLayout.kt")
    (folder/module.name).write_text(module.read_text(encoding="utf8"), encoding="utf8")
