"""Direct-on-TV rendering and OCR; receiver mode and mobile remain unchanged."""
from pathlib import Path
from tv_navigation import patch_navigation

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
        if (telefon || PrijemService.bezi) return
        val i = Intent(this, OverlayService::class.java)""")
    # TV startup restores its own ROI. Validate normalized bounds before crop.
    one("if (telefon && OblastTitulku.rucne) OblastTitulku.oblast.copyOf() else null",
        """if (OblastTitulku.rucne) OblastTitulku.oblast.copyOf().takeIf {
            it.size == 4 && it.all { value -> value.isFinite() && value in 0f..1f } &&
                it[2] > it[0] + 0.10f && it[3] > it[1] + 0.05f
        } else if (!telefon) GameLingoOCRConfig.preset(this) else null""")
    # Optional iOS timing in the console path, without changing the verified default.
    # The common language integration already bounds ordinary captions to four seconds.
    one("        ukaz(cesky, 4000, box, radku, barvyJmen)",
        "        ukaz(cesky, if (telefon) 4000L else GameLingoOCRConfig.displayMs(this,4000L), box, radku, barvyJmen)")
    one("            (ukazanoKdy + 4000L - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)",
        "            (ukazanoKdy + (if (telefon) 4000L else GameLingoOCRConfig.displayMs(this,4000L)) - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)")
    one("        val token = platnostPrekladu.novy()\n        val oblast =",
        "        val token = platnostPrekladu.novy()\n        val tvTranslationStarted = android.os.SystemClock.elapsedRealtime()\n        val oblast =")
    one("            if (cislo < posledniUkazany) return // mezitím se ukázala novější replika",
        "            if (!telefon && GameLingoOCRConfig.expired(this,android.os.SystemClock.elapsedRealtime()-tvTranslationStarted)) return\n            if (cislo < posledniUkazany) return // mezitím se ukázala novější replika")
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
        if (PrijemService.bezi) return
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
    one("    private var pribehLokalni: CteciFronta.Token? = null",
        "    private var pribehLokalni: CteciFronta.Token? = null\n    private var lokalniStranka = -1")
    one("                } else {\n                    if (p?.token != pribehLokalni) {",
        """                } else {
                    if (!telefon && pauza) {
                        if (pribehLokalni != null) ukazLokalne("", 0, null, 1, null)
                        pribehLokalni = null
                        lokalniStranka = -1
                    } else if (p?.token != pribehLokalni || (!telefon && (p?.index ?: -1) != lokalniStranka)) {""")
    one("                        pribehLokalni = p?.token",
        "                        pribehLokalni = p?.token\n                        lokalniStranka = p?.index ?: -1")
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

    # TV UI source is copied into both variants. Wire the TV-only module only
    # in the generated TV workspace, so the mobile build has no missing symbol.
    tv_ui = folder / "GameLingoTVUi.kt"
    text = tv_ui.read_text(encoding="utf8")
    anchor = '        addGrid(listOf(overlay))'
    if text.count(anchor) != 1:
        raise RuntimeError("Direct TV settings anchor changed")
    text = text.replace(anchor, '''        addGrid(listOf(overlay, settingsTile("▣", "Oblasti přímého překladu",
            "Modrá, zelená, žlutá a oranžová") {
            GameLingoDirectTVLayout.show(a) {
                dialog.dismiss()
                onUpdate()
            }
        }))''', 1)
    tv_ui.write_text(text, encoding="utf8")

    glossary_ui = folder / "GameLingoGlossaryUi.kt"
    glossary_source = glossary_ui.read_text(encoding="utf8")
    glossary_source = glossary_source.replace("!RemotePlayService.aktivni && !PrijemService.bezi",
        "!RemotePlayService.aktivni && !PrijemService.bezi && !GameLingoScreenTranslationService.running")
    glossary_ui.write_text(glossary_source,encoding="utf8")

    # Install all TV-only screens and services in the actual generated workspace.
    for name in ("GameLingoTVSettings.kt", "GameLingoTVProfiles.kt",
                 "GameLingoScreenTranslationService.kt", "GameLingoTVFit.kt", "GameLingoTVUpdater.kt",
                 "GameLingoSettings.kt", "GameLingoOCRSettings.kt"):
        (folder / name).write_text(Path(__file__).with_name(name).read_text(encoding="utf8"), encoding="utf8")

    # TV updater lives inside the existing About page, not the dashboard grid.
    about = folder / "GameLingoAbout.kt"
    about_text = about.read_text(encoding="utf8")
    update_anchor = '        page.addView(label("Licence a původní autoři", 19f, true))'
    if about_text.count(update_anchor) != 1:
        raise RuntimeError("TV About update anchor changed")
    about_text = about_text.replace(update_anchor, '''        page.addView(GameLingoTVUi.action(activity, GameLingoLanguages.ui(activity, "Zkontrolovat aktualizace")) {
            GameLingoTVUpdater.show(activity)
        })
''' + update_anchor, 1)
    about.write_text(about_text, encoding="utf8")

    # The translation and OCR panels are shared; the other destinations use the
    # TV's local stores, not the phone's paired-receiver preferences or sender.
    shared_settings = folder / "GameLingoSettings.kt"
    shared_text = shared_settings.read_text(encoding="utf8")
    for old, new in (
        ("GameLingoProfiles.show(activity, settings) {}", "GameLingoTVProfiles.show(activity)"),
        ("GameLingoSubtitleAppearance.show(activity)", "GameLingoTVCaptionStyle.show(activity)"),
        ("GameLingoDiagnostics.show(activity)", "GameLingoScreenTranslationService.diagnostics(activity)"),
    ):
        if shared_text.count(old) != 1:
            raise RuntimeError("Shared TV settings anchor changed: " + old)
        shared_text = shared_text.replace(old, new, 1)
    shared_settings.write_text(shared_text, encoding="utf8")

    main = folder / "MainActivity.kt"
    m = main.read_text(encoding="utf8")
    def patch_main(old, new):
        nonlocal m
        if m.count(old) != 1:
            raise RuntimeError("TV main anchor changed: " + old[:90])
        m = m.replace(old, new, 1)

    patch_main("        GameLingoTVPairing.initialize(this)",
               "        GameLingoTVPairing.initialize(this)\n        GameLingoTVUpdater.checkAtStartup(this)")
    patch_main("GameLingoTVUi.showSettings(this) { ukazHlavni() }",
        """GameLingoTVSettings.show(this,
                    onDevices = {
                        GameLingoDeviceCenter.show(this, onXbox = { ukazPripojeniXbox() },
                            onPlayStation = { ukazPripojeni() }, onTV = {
                                if (!android.provider.Settings.canDrawOverlays(this)) pozadejOPovoleni()
                                else if (PrijemService.bezi) ukazPrijem() else hratSTelefonem()
                            },
                            onUpdated = { ukazHlavni() })
                    }, onUpdate = { ukazHlavni() })""")
    patch_main('else if (jeSlabaTv()) varovaniSlabaTv() else hratTelevizi()',
               'else vyberZdrojTelevize()')
    patch_main('private fun hratTelevizi() {', """private fun vyberZdrojTelevize() {
        GameLingoTVUi.select(this,GameLingoLanguages.ui(this, "Vyber zdroj překladu"),arrayOf("PlayStation 4 / 5", "Xbox Series / One", GameLingoLanguages.ui(this, "Samostatná TV"))) { index ->
                if (index == 2) {
                    if (!GameLingoScreenTranslationService.running && (RemotePlayService.aktivni || PrijemService.bezi)) {
                        android.app.AlertDialog.Builder(this).setMessage(GameLingoLanguages.ui(this, "Nejdříve zastav překlad z konzole nebo telefonu."))
                            .setPositiveButton("OK", null).show()
                    } else {
                        AutoStart.nastavZapnuto(this, false)
                        GameLingoScreenTranslationService.open(this)
                    }
                } else if (RemotePlayService.aktivni) {
                    android.app.AlertDialog.Builder(this).setMessage(GameLingoLanguages.ui(this, "Nejdřív zastav probíhající překlad."))
                        .setPositiveButton("OK", null).show()
                } else if (index == 0) vyberPrimouPS() else vyberPrimyXbox()
            }
    }

    private fun spustPrimyZdroj(xbox: Boolean) {
        if (GameLingoScreenTranslationService.running || PrijemService.bezi) {
            android.app.AlertDialog.Builder(this).setMessage(GameLingoLanguages.ui(this, "Nejdříve zastav stávající překlad."))
                .setPositiveButton("OK",null).show()
            return
        }
        GameLingoTVProfiles.chooseForStart(this) {
            GameLingoScreenTranslationService.stop(this)
            getSharedPreferences("gamelingo", MODE_PRIVATE).edit()
                .putString("directPlatform", if (xbox) "xbox" else "ps").apply()
            RemotePlayService.prepni(this, xbox)
            if (jeSlabaTv()) varovaniSlabaTv() else hratTelevizi()
        }
    }

    private fun vyberPrimouPS() {
        val consoles = GameLingoPlayStationConsoles.list(this)
        if (consoles.isEmpty()) {
            if (udaje.sparovano) spustPrimyZdroj(false) else ukazPripojeni()
            return
        }
        GameLingoTVUi.select(this,GameLingoLanguages.ui(this, "Vyber PlayStation"),(consoles.map { it.type + " · " + it.name } + ("+ " + GameLingoLanguages.ui(this, "Přidat PlayStation"))).toTypedArray()) { index ->
                if (index == consoles.size) ukazPripojeni()
                else if (GameLingoPlayStationConsoles.select(this, consoles[index].ip)) spustPrimyZdroj(false)
                else android.app.AlertDialog.Builder(this).setMessage(GameLingoLanguages.ui(this, "Konzoli se nepodařilo načíst.")).setPositiveButton("OK", null).show()
            }
    }

    private fun vyberPrimyXbox() {
        if (!XboxUcet.prihlasen(this)) { ukazPripojeniXbox(); return }
        val loading = android.app.ProgressDialog.show(this, "Xbox", GameLingoLanguages.ui(this, "Hledám konzole…"), true, true)
        Thread {
            val result = runCatching { XboxSpojeni.konzole(this) }
            ui.post {
                if (isFinishing || isDestroyed || !loading.isShowing) return@post
                loading.dismiss()
                if (result.isFailure) {
                    android.app.AlertDialog.Builder(this).setMessage(GameLingoLanguages.ui(this, "Seznam Xboxů se nepodařilo načíst."))
                        .setPositiveButton("OK", null).show()
                    return@post
                }
                val prefs = getSharedPreferences("gamelingo_devices", MODE_PRIVATE)
                val hidden = prefs.getStringSet("hiddenXboxIds", emptySet()).orEmpty()
                val consoles = result.getOrDefault(emptyList()).filterNot { it.id in hidden }
                GameLingoTVUi.select(this,GameLingoLanguages.ui(this, "Vyber Xbox"),(consoles.map { it.jmeno } + ("+ " + GameLingoLanguages.ui(this, "Přidat Xbox"))).toTypedArray()) { index ->
                        if (index == consoles.size) ukazPripojeniXbox()
                        else {
                            val x = consoles[index]
                            prefs.edit().putString("selectedXboxId", x.id).apply()
                            XboxUcet.ulozKonzoli(this, x.id, x.jmeno)
                            spustPrimyZdroj(true)
                        }
                    }
            }
        }.start()
    }

    private fun hratTelevizi() {
        GameLingoScreenTranslationService.stop(this)""")
    # Always land on the dashboard. Opening the app must not redirect to
    # the phone receiver merely because its background service is running.
    patch_main('        if (!telefon && PrijemService.bezi) { ukazPrijem(); return }',
               '        // Active phone reception stays in the background; its tile opens it explicitly.')
    patch_main('private fun hratSTelefonem() {',
               'private fun hratSTelefonem() {\n        GameLingoScreenTranslationService.stop(this)')
    patch_main("private val SKALA = 0.72f", "private val SKALA: Float get() = GameLingoTVUi.menuScale(this)")
    m = patch_navigation(m)
    main.write_text(m, encoding="utf8")

    # Receiver keys exist in the TV build only. Do not add dependencies on
    # receiver code to the shared Android-phone device manager.
    devices = folder / "GameLingoDeviceCenter.kt"
    device_source = devices.read_text(encoding="utf8")
    marker = "            // TV_RECEIVER_PHONE_ROWS"
    if device_source.count(marker) != 1:
        raise RuntimeError("TV phone list anchor changed")
    device_source = device_source.replace(marker, """            val phones = IPhoneTVPairing.phones(c)
            if (phones.isEmpty()) {
                tvPanel.addView(label(c, GameLingoLanguages.ui(c, "Zatím žádný spárovaný telefon."), 14f, muted))
            }
            for (phone in phones) {
                add(tvPanel, line(c, phone.label,
                    if (phone.connected) GameLingoLanguages.ui(c, "Připojen") else GameLingoLanguages.ui(c, "Spárován"),
                    phone.connected) {
                    chooseDevice(c, phone.label, arrayOf(GameLingoLanguages.ui(c, "Odpárovat"))) {
                        AlertDialog.Builder(c)
                            .setTitle(GameLingoLanguages.ui(c, "Odpárovat") + " " + phone.label + "?")
                            .setMessage(GameLingoLanguages.ui(c, "Ostatní telefony zůstanou spárované."))
                            .setNegativeButton(GameLingoLanguages.ui(c, "Zrušit"), null)
                            .setPositiveButton(GameLingoLanguages.ui(c, "Odpárovat")) { _, _ ->
                                if (IPhoneTVPairing.unpair(c, phone.id)) {
                                    onDismiss()
                                    onUpdated()
                                } else AlertDialog.Builder(c)
                                    .setMessage(GameLingoLanguages.ui(c, "Odpárování se nepodařilo uložit."))
                                    .setPositiveButton("OK", null).show()
                            }.show()
                    }
                })
            }""", 1)
    devices.write_text(device_source, encoding="utf8")

    # Respect explicit TV platform choice; never silently fall back to the other console.
    s = service.read_text(encoding="utf8")
    one('''        val xboxId = XboxUcet.konzole(this)?.first
        val maPs5 = udaje.sparovano && ip != null''',
        '''        val platform = if (telefon) null else getSharedPreferences("gamelingo", MODE_PRIVATE).getString("directPlatform", null)
        val xboxId = if (platform == "ps") null else XboxUcet.konzole(this)?.first
        val maPs5 = platform != "xbox" && udaje.sparovano && ip != null''')
    service.write_text(s, encoding="utf8")

    # folder = app/src/main/java/cz/titulky/tv; main is parents[3].
    manifest = folder.parents[3] / "AndroidManifest.xml"
    xml = manifest.read_text(encoding="utf8")
    if xml.count("</application>") != 1:
        raise RuntimeError("TV manifest application anchor changed")
    for permission in ("android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION", "android.permission.REQUEST_INSTALL_PACKAGES"):
        if f'android:name="{permission}"' not in xml:
            xml = xml.replace("<application", f'<uses-permission android:name="{permission}"/>\n    <application', 1)
    xml = xml.replace("</application>", """    <activity android:name=".GameLingoScreenConsentActivity" android:exported="false"/>
        <service android:name=".GameLingoScreenTranslationService" android:exported="false"
            android:foregroundServiceType="mediaProjection"/>
    </application>""", 1)
    xml = xml.replace("</application>", """    <provider android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.updates" android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/gamelingo_update_paths"/>
        </provider>
    </application>""", 1)
    paths = manifest.parent / "res" / "xml" / "gamelingo_update_paths.xml"
    paths.parent.mkdir(parents=True, exist_ok=True)
    paths.write_text('<paths xmlns:android="http://schemas.android.com/apk/res/android"><cache-path name="updates" path="gamelingo-updates/"/></paths>', encoding="utf8")
    manifest.write_text(xml, encoding="utf8")
    # Reuse the actual game dialogue reader; no second ML Kit selection pipeline.
    reader = folder / "Ctecka.kt"
    reader_text = reader.read_text(encoding="utf8")
    def reader_one(old, new):
        nonlocal reader_text
        if reader_text.count(old) != 1:
            raise RuntimeError("Shared dialogue reader anchor changed: " + old[:100])
        reader_text = reader_text.replace(old, new, 1)

    reader_one("pasmaNapovedy: List<IntRange> = emptyList()\n    ) {",
        """pasmaNapovedy: List<IntRange> = emptyList(),
        sourceLanguage: String = "en",
        standaloneScreen: Boolean = false
    ) {""")
    reader_one("                var hotoveRadky = VyberTitulku.radky(kusy, obraz.width, ::doplnJmeno)",
        """                // Screen capture also sees unrelated application grids.
                // Keep this guard at the source boundary; game reading is unchanged.
                if (standaloneScreen && GameLingoOCRConfig.filterMenuText &&
                    (kusy.size > 8 || kusy.any { k ->
                        kusy.any { other -> other !== k &&
                            kotlin.math.abs(k.top - other.top) < maxOf(1, (k.bottom-k.top)/2) &&
                            (k.right < other.left - obraz.width*.08f || other.right < k.left - obraz.width*.08f)
                        }
                    })) {
                    hotovo(Titulek.NIC); return@addOnSuccessListener
                }
                var hotoveRadky = VyberTitulku.radky(kusy, obraz.width, ::doplnJmeno)""")
    # Preserve exact English game behavior. Other source languages use the same
    # grouping and geometry but do not run English-only cleanup or hint heuristics.
    reader_one('val napovedaRadek = hotoveRadky.lastOrNull()?.takeIf { r ->',
        'val napovedaRadek = hotoveRadky.lastOrNull()?.takeIf { r ->\n                    if (sourceLanguage != "en") return@takeIf false\n                    if (standaloneScreen && !NAPOVEDA.containsMatchIn(r.text)) return@takeIf false')
    reader_one('r === napovedaRadek || NAPOVEDA.containsMatchIn(r.text)',
        'r === napovedaRadek || (sourceLanguage == "en" && NAPOVEDA.containsMatchIn(r.text))')
    reader_one('val vybrane = VyberTitulku.vyber(hotoveRadky)',
        '''val vybrane = VyberTitulku.vyber(hotoveRadky)
                if (vybrane.isEmpty()) { hotovo(Titulek.NIC); return@addOnSuccessListener }''')
    reader_one('if (jeNesmysl(bezJmen)) {', 'if (sourceLanguage == "en" && jeNesmysl(bezJmen)) {')
    reader_one('if (kod == "en" || (kod != "cs" && kod != "sk" && kod != cilovyJazyk && bezDiakritiky && bezJmen.length < 40 && anglickeSlovo(bezJmen))) hotovo(Titulek(repliky.map { vycisti(it) },',
        '''if (kod == sourceLanguage || (sourceLanguage == "en" && kod != "cs" && kod != "sk" && kod != cilovyJazyk && bezDiakritiky && bezJmen.length < 40 && anglickeSlovo(bezJmen))) hotovo(Titulek(repliky.map { if (sourceLanguage == "en") vycisti(it) else it },''')
    reader.write_text(reader_text, encoding="utf8")

    # Standalone captions must not be hidden by the console-only foreground filter.
    overlay = folder / "OverlayService.kt"
    overlay_text = overlay.read_text(encoding="utf8")
    marker = "val vstup = PrijemService.bezi || runCatching { poprediPribehu.jeVstupTv() }"
    if overlay_text.count(marker) != 1:
        raise RuntimeError("TV overlay foreground anchor changed")
    overlay_text = overlay_text.replace(marker,
        "val vstup = PrijemService.bezi || GameLingoScreenTranslationService.running || runCatching { poprediPribehu.jeVstupTv() }", 1)

    # The persistent self-test uses the real subtitle window, not a separate
    # overlay, a screenshot, OCR or a translation request.
    def overlay_one(old, new):
        nonlocal overlay_text
        if overlay_text.count(old) != 1:
            raise RuntimeError("TV overlay test anchor changed: " + old[:90])
        overlay_text = overlay_text.replace(old, new, 1)

    overlay_one('        super.onCreate()\n        startAsForeground()', '''        super.onCreate()
        captureInstance = this
        startAsForeground()''')
    overlay_one('        pripravena = false\n        ui.removeCallbacks(hideRunnable)',
        '''        if (captureInstance === this) captureInstance = null
        pripravena = false
        ui.removeCallbacks(hideRunnable)''')
    overlay_one('    private fun showText(text: CharSequence, hideAfterMs: Long = 6000) {',
        '''    // Only our own windows are hidden during a consented MediaProjection sample.
    private fun suppressForCapture(hidden: Boolean): Boolean {
        val views = listOfNotNull(textView, podklad, napoveda, seznamVoleb, ramecekTitulku, ramecekVoleb)
        val visible = views.any { it.visibility == android.view.View.VISIBLE && it.alpha > 0f }
        views.forEach { it.alpha = if (hidden) 0f else 1f }
        val zonesVisible = automaticZoneOverlay.suppress(hidden)
        return visible || zonesVisible
    }

    private fun showText(text: CharSequence, hideAfterMs: Long = 6000) {''')
    overlay_one('        const val ACTION_SET_TEXT = "cz.titulky.tv.SET_TEXT"',
        '''        private var captureInstance: OverlayService? = null
        internal fun suppressStandaloneCapture(hidden: Boolean): Boolean {
            check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            if (hidden && !GameLingoScreenTranslationService.running) return false
            return captureInstance?.suppressForCapture(hidden) ?: false
        }
        const val ACTION_SET_TEXT = "cz.titulky.tv.SET_TEXT"''')

    overlay_one('        if (!android.provider.Settings.canDrawOverlays(this)) {',
        '''        recordOverlayTest("Oprávnění překryvu: " + android.provider.Settings.canDrawOverlays(this))
        if (!android.provider.Settings.canDrawOverlays(this)) {''')
    overlay_one('        wm.addView(tv, lp)',
        '''        recordOverlayTest("addView: TYPE_APPLICATION_OVERLAY · FLAG_NOT_TOUCHABLE")
        try {
            wm.addView(tv, lp)
            recordOverlayTest("addView titulků: úspěch")
        } catch (e: Exception) {
            recordOverlayTest("addView titulků: chyba " + e.javaClass.simpleName)
            throw e
        }''')
    overlay_one('        runCatching { startAsForeground() }',
        '''        runCatching { startAsForeground() }
            .onSuccess { if (intent?.action == ACTION_TEST_OVERLAY) recordOverlayTest("Foreground service: OK") }
            .onFailure { recordOverlayTest("Foreground service: chyba " + it.javaClass.simpleName) }
        if (intent?.action == ACTION_STOP_TEST_OVERLAY) {
            if (overlayTestActive) {
                overlayTestActive = false
                showText("")
                recordOverlayTest("Test vypnut")
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_TEST_OVERLAY) {
            if (RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running) {
                recordOverlayTest("Test odmítnut: běží jiný režim")
                return START_NOT_STICKY
            }
            val allowed = android.provider.Settings.canDrawOverlays(this)
            recordOverlayTest("Oprávnění překryvu: " + allowed)
            if (!allowed) {
                overlayTestActive = false
                recordOverlayTest("Chybí oprávnění překryvu")
                return START_NOT_STICKY
            }
            if (!pripravena || textView?.isAttachedToWindow != true) {
                recordOverlayTest("Okno nepřipojeno, opakuji addView")
                zkusOkna(0)
            }
            if (!pripravena || textView?.isAttachedToWindow != true) {
                overlayTestActive = false
                recordOverlayTest("Chyba: okno titulků není připojené")
                return START_NOT_STICKY
            }
            recordOverlayTest("Okno titulků připojeno")
            zrusPribeh()
            konecUpravy.run()
            val position = GameLingoDirectTVLayout.green(this) ?: floatArrayOf(.5f,.84f,.75f,1f)
            nastavVlastniPolohu(position)
            try {
                val view = checkNotNull(textView)
                val lp = checkNotNull(layout)
                wm.updateViewLayout(view, lp)
                showText("Testovací titulek", 0L)
                overlayTestActive = true
                recordOverlayTest("Testovací titulek: zobrazení vyžádáno, bez časovače")
            } catch (e: Exception) {
                overlayTestActive = false
                recordOverlayTest("Zobrazení: chyba " + e.javaClass.simpleName)
            }
            return START_NOT_STICKY
        }
        if (overlayTestActive && (intent?.action == null || intent.action == ACTION_SET_TEXT ||
            intent.action == ACTION_SET_PRIBEH || RemotePlayService.aktivni || PrijemService.bezi ||
            GameLingoScreenTranslationService.running)) {
            overlayTestActive = false
            showText("")
            recordOverlayTest("Test ukončen: jiný režim")
        }''')
    overlay_one('        pripravena = false\n        ui.removeCallbacks(hideRunnable)',
        '''        if (overlayTestActive) recordOverlayTest("Test ukončen: služba zastavena")
        overlayTestActive = false
        pripravena = false
        ui.removeCallbacks(hideRunnable)''')
    overlay_one('        const val ACTION_SET_TEXT = "cz.titulky.tv.SET_TEXT"',
        '''        const val ACTION_TEST_OVERLAY = "gamelingo.overlay.TEST"
        const val ACTION_STOP_TEST_OVERLAY = "gamelingo.overlay.STOP_TEST"
        @Volatile var overlayTestActive = false
            private set
        private val overlayTestEvents = ArrayDeque<String>()
        internal fun recordOverlayTest(message: String) {
            android.util.Log.i("GameLingoOverlay", message)
            synchronized(overlayTestEvents) {
                if (overlayTestEvents.size >= 14) overlayTestEvents.removeFirst()
                overlayTestEvents.addLast(message)
            }
        }
        internal fun overlayTestReport(): String = synchronized(overlayTestEvents) {
            overlayTestEvents.joinToString("\\n")
        }
        const val ACTION_SET_TEXT = "cz.titulky.tv.SET_TEXT"''')
    # Authenticated full snapshots use the existing foreground overlay service.
    for name in ("GameLingoZonePacket.kt", "GameLingoZoneOverlay.kt"):
        (folder / name).write_text(Path(__file__).with_name(name).read_text(encoding="utf8"), encoding="utf8")
    unit_dir = folder.parents[4] / "test/java/cz/titulky/tv"
    unit_dir.mkdir(parents=True, exist_ok=True)
    (unit_dir / "GameLingoZonePacketTest.kt").write_text(Path(__file__).with_name("GameLingoZonePacketTest.kt").read_text(encoding="utf8"), encoding="utf8")
    overlay_one('    private lateinit var wm: WindowManager',
        '''    private lateinit var wm: WindowManager
    private val automaticZoneOverlay by lazy { GameLingoZoneOverlay(this, wm) }''')
    overlay_one('        if (intent?.action == ACTION_ZRUS_PRIBEH) { zrusPribeh(); showText(""); return START_STICKY }',
        '''        if (intent?.action == ACTION_SET_ZONES || intent?.action == ACTION_SET_LOCAL_ZONES) {
            val packet = GameLingoZonePacket.parse(intent.getStringExtra(EXTRA_ZONE_PACKET).orEmpty().split('\\t'))
            if (packet != null) {
                if (packet.blocks.isNotEmpty()) { zrusPribeh(); showText("") }
                automaticZoneOverlay.show(packet, local = intent.action == ACTION_SET_LOCAL_ZONES)
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_ZRUS_PRIBEH) { automaticZoneOverlay.clear(); zrusPribeh(); showText(""); return START_STICKY }''')
    overlay_one('            val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()\n            val box',
        '            val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()\n            if (text.isNotBlank()) automaticZoneOverlay.clear()\n            val box')
    overlay_one('    override fun onDestroy() {\n        zrusPribeh()',
        '    override fun onDestroy() {\n        automaticZoneOverlay.clear()\n        zrusPribeh()')
    overlay_one('        const val ACTION_SET_TEXT = "cz.titulky.tv.SET_TEXT"',
        '''        const val ACTION_SET_ZONES = "gamelingo.overlay.ZONES"
        const val ACTION_SET_LOCAL_ZONES = "gamelingo.overlay.LOCAL_ZONES"
        const val EXTRA_ZONE_PACKET = "zone_packet"
        const val ACTION_SET_TEXT = "cz.titulky.tv.SET_TEXT"''')
    overlay.write_text(overlay_text, encoding="utf8")

