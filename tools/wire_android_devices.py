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
    for name in ("GameLingoDeviceCenter.kt","GameLingoPlayStationConsoles.kt","GameLingoFirstRun.kt","GameLingoAbout.kt","GameLingoNavigation.kt","GameLingoUIContract.kt","GameLingoVisualKit.kt","GameLingoLanguages.kt","GameLingoTVUi.kt","GameLingoTVPairing.kt"):
        original=Path(__file__).with_name(name)
        (folder/name).write_text(original.read_text(encoding="utf-8"),encoding="utf-8")

    mainFile=folder/"MainActivity.kt"
    code=mainFile.read_text(encoding="utf-8")
    code=one(code, "        super.onCreate(savedInstanceState)",
        "        super.onCreate(savedInstanceState)\n        GameLingoTVPairing.initialize(this)",
        "initialize signed TV sender")

    def manager():
        return '''GameLingoDeviceCenter.show(this,
                    onXbox = { ukazPripojeniXbox() },
                    onPlayStation = { ukazPripojeni() },
                    onTV = { vyberVystupTv() },
                    onUpdated = { ukazHlavni() })'''

    # Mobile and TV share the PSN source, but the TV has a different
    # dashboard and no phone-only 'druhy' tiles. Never require a mobile
    # anchor inside the Google TV branch.
    mobile_dashboard = 'GameLingoVisualKit.brandHeader(this)' in code
    tv_dashboard = '"Překlad z telefonu"' in code and '"Přímo na TV"' in code
    # P2.1 QR parser shared; scanner dependency restricted to mobile.
    (folder/"GameLingoPairingQR.kt").write_text(Path(__file__).with_name("GameLingoPairingQR.kt").read_text(encoding="utf8"),encoding="utf8")
    scanner = "GameLingoQRScanner.kt" if mobile_dashboard else "GameLingoQRScannerTV.kt"
    (folder/"GameLingoQRScanner.kt").write_text(Path(__file__).with_name(scanner).read_text(encoding="utf8"),encoding="utf8")
    if tv_dashboard and not mobile_dashboard:
        (folder/"GameLingoQRDisplay.kt").write_text(Path(__file__).with_name("GameLingoQRDisplay.kt").read_text(encoding="utf8"),encoding="utf8")
    if mobile_dashboard:
        gradle=upstream/"app/build.gradle.kts"
        g=gradle.read_text(encoding="utf8")
        g=one(g,'dependencies {','dependencies {\n    implementation("com.journeyapps:zxing-android-embedded:4.3.0")',"mobile QR scanner dependency")
        gradle.write_text(g,encoding="utf8")
        code=one(code,'class MainActivity : Activity() {', '''class MainActivity : Activity() {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (GameLingoQRScanner.result(this, requestCode, resultCode, data)) return
        super.onActivityResult(requestCode, resultCode, data)
    }
''',"mobile QR activity result")
    unit_dir=folder.parents[4]/"test/java/cz/titulky/tv"
    unit_dir.mkdir(parents=True,exist_ok=True)
    (unit_dir/"GameLingoPairingQRTest.kt").write_text(Path(__file__).with_name("GameLingoPairingQRTest.kt").read_text(encoding="utf8"),encoding="utf8")
    if mobile_dashboard:
        code=one(
            code,
            '''        prevezmiAccountId(intent)
        ukazUvod()''',
            '''        prevezmiAccountId(intent)
        if (telefon && !GameLingoFirstRun.completed(this) && nast.jazyk == null) {
            // First-run language choices are owned by GameLingo, not upstream.
            nast.jazyk = "cs"
        }
        ukazUvod()
        if (telefon && !GameLingoFirstRun.completed(this)) {
            ui.post {
                GameLingoFirstRun.show(this,
                    connectXbox = { ukazPripojeniXbox() },
                    connectPlayStation = { ukazPripojeni() },
                    connectTV = { vyberVystupTv() },
                    finished = { ukazHlavni() })
            }
        }''',
            "mobile first-run wizard"
        )
        # The native dashboard supplies the same central device action to both
        # the fourth home tile and fifth-row navigation, without string patches.
        if 'fun openDevices() {' not in code or 'GameLingoDeviceCenter.content(this,' not in code:
            raise RuntimeError("Mobile GameLingo device screen missing")
        if 'onDevices = { openDevices() }' not in code:
            raise RuntimeError("Mobile bottom navigation is not wired to devices")

    elif tv_dashboard:
        code=one(code, "        GameLingoTVPairing.initialize(this)",
            "        GameLingoTVPairing.initialize(this)\n        IPhoneTVPairing.initialize(this)",
            "migrate receiver key on TV startup")
        # TV first boot has its own welcome and three real language choices.
        # The upstream beta promotion is not part of GameLingo.
        code=one(code,
            '''                else if (nast.jazyk == null) ukazJazyk()
                else if (maUkazatBetu()) ukazBetu() else pokracuj()''',
            '''                else if (nast.jazyk == null) {
                    GameLingoTVUi.showWelcome(this@MainActivity) { language ->
                        nast.jazyk = language
                        ukazHlavni()
                    }
                } else ukazHlavni()''',
            "TV native welcome instead of PlayCaption beta")
        code=one(
            code,
            '''            obsah.addView(volby, odsazeni(bottom = 5))''',
            '''            obsah.addView(volby, odsazeni(bottom = 5))
            obsah.addView(GameLingoTVUi.action(this, "▭  Spravovat zařízení") {
                '''+manager()+'''
            }, odsazeni(bottom = 9))''',
            "Google TV device center action"
        )
    else:
        raise RuntimeError("Neither Android phone nor Google TV dashboard recognized")

    # P0.5: registration and console selection, shared by phone and TV.
    code=one(code,
        "    private var nalezenaIp: String? = null",
        "    private var nalezenaIp: String? = null\n    private var nalezenaPS: HledacPS.Konzole? = null\n    private var psDiscoveryGeneration = 0L",
        "remember actual PlayStation type")
    code=one(code,
        "            hledaniStav.text = t(\"hledam\")\n            Thread {\n                val seznam = runCatching { HledacPS.hledej() }.getOrDefault(emptyList())",
        "            hledaniStav.text = t(\"hledam\")\n            nalezenaIp = null\n            nalezenaPS = null\n            val generation = ++psDiscoveryGeneration\n            Thread {\n                val seznam = runCatching { HledacPS.hledej() }.getOrDefault(emptyList())",
        "reset stale selection")
    code=one(code,
        "                    val ps = seznam.firstOrNull { it.typ == \"PS5\" } ?: seznam.firstOrNull()\n                    nalezenaIp = ps?.ip\n                    hledaniStav.text = if (ps != null) \"✔ ${t(\"nalezena\")}: ${ps.jmeno} (${ps.ip})\" else \"✘ ${t(\"nenalezena\")}\"\n                    hledaniStav.setTextColor(if (ps != null) Color.parseColor(\"#7DFF9A\") else Color.parseColor(\"#FF8A8A\"))",
        "                    if (isFinishing || obrazovka != \"pripojeni\" || generation != psDiscoveryGeneration) return@runOnUiThread\n                    val supported = seznam.filter { it.typ == \"PS4\" || it.typ == \"PS5\" }\n                    fun selectPS(ps: HledacPS.Konzole) {\n                        if (generation != psDiscoveryGeneration || obrazovka != \"pripojeni\") return\n                        nalezenaPS = ps\n                        nalezenaIp = ps.ip\n                        hledaniStav.text = \"✔ \"+ps.typ+\" · \"+ps.jmeno+\" (\"+ps.ip+\")\"\n                        hledaniStav.setTextColor(Color.parseColor(\"#7DFF9A\"))\n                    }\n                    when (supported.size) {\n                        0 -> {\n                            hledaniStav.text = \"✘ \"+t(\"nenalezena\")\n                            hledaniStav.setTextColor(Color.parseColor(\"#FF8A8A\"))\n                        }\n                        1 -> selectPS(supported.single())\n                        else -> {\n                            hledaniStav.text = \"Vyber konzoli PS4 / PS5\"\n                            android.app.AlertDialog.Builder(this)\n                                .setTitle(\"Vyber konzoli\")\n                                .setItems(supported.map { it.typ+\" · \"+it.jmeno+\" (\"+it.ip+\")\" }.toTypedArray()) { _, i ->\n                                    selectPS(supported[i])\n                                }.setNegativeButton(android.R.string.cancel, null).show()\n                        }\n                    }",
        "explicit PlayStation selection")
    code=one(code,
        "            val ip = nalezenaIp ?: udaje.ipKonzole",
        "            val selectedPS = nalezenaPS\n            val ip = nalezenaIp\n            val target = if (selectedPS?.typ == \"PS4\") com.metallic.chiaki.lib.Target.PS4_10\n                else com.metallic.chiaki.lib.Target.PS5_1",
        "selected console registration type")
    code=one(code,
        "Parovani.sparuj(this, ip, cislo.toInt(), log = {}, hotovo = { ok, zprava ->",
        "Parovani.sparuj(this, ip, cislo.toInt(), target = target, log = {}, hotovo = { ok, zprava ->",
        "pass target to native registration")
    code=one(code,
        '''                            if (ok) {
                                AutoStart.nastavZapnuto(this, true)''',
        '''                            if (ok) {
                                if (ip != null)
                                    GameLingoPlayStationConsoles.capture(this,
                                        selectedPS?.jmeno ?: "PlayStation", ip)
                                AutoStart.nastavZapnuto(this, true)''',
        "PlayStation registration success")

    if mobile_dashboard or tv_dashboard:
        # The Android TV pairing code may select 41990-41993, but the
        # upstream mobile subtitle sender independently used 41989 for ALL
        # PCT1/PCV1 data. Route caption, choices, preview and clear through
        # the asynchronously refreshed GameLingoTVPairing port cache.
        transport = folder/"Propojeni.kt"
        src = transport.read_text(encoding="utf-8")
        sender_marker = "    object Vysilac {"
        if src.count(sender_marker) != 1:
            raise RuntimeError("Android TV subtitle sender anchor changed")
        prefix, sender = src.split(sender_marker, 1)
        for old, new, label in (
            (
                'data.forEach { s.send(DatagramPacket(it, it.size, kam, PORT_TITULKY)) }',
                'data.forEach { GameLingoTVPairing.sendSigned(s, ip, kam, it, GameLingoTVPairing.portFor(ip)) }',
                "Android subtitle clear",
            ),
            (
                's.send(DatagramPacket(cil.third, cil.third.size, kam, PORT_TITULKY))',
                'GameLingoTVPairing.sendSigned(s, cil.first, kam, cil.third, GameLingoTVPairing.portFor(cil.first))',
                "Android caption immediate delivery",
            ),
            (
                's.send(DatagramPacket(data, data.size, kam, PORT_TITULKY))',
                'GameLingoTVPairing.sendSigned(s, ip, kam, data, GameLingoTVPairing.portFor(ip))',
                "Android caption repeat",
            ),
            (
                's.send(DatagramPacket(v, v.size, kam, PORT_TITULKY))',
                'GameLingoTVPairing.sendSigned(s, ip, kam, v, GameLingoTVPairing.portFor(ip))',
                "Android game text/choices repeat",
            ),
        ):
            sender = one(sender, old, new, label)
        # Stop idle heartbeat traffic from retaining the receiver's source lease.
        # Keep the selected TV and its profile so a later Start can reuse them.
        sender = one(sender,
            '        /** Každou sekundu aktuální stav; stará smyčka nesmí po změně cíle ožít. */',
            '''        fun stopGameData() = synchronized(zamek) {
            if (cilIp == null) return@synchronized
            // Invalidate already queued senders, then send a new clear snapshot.
            generace++
            prenos.reset(generace)
            text = ""; napoveda = ""; doba = 0L; barvy = null
            ukazkaText = ""; upravaDo = 0L
            volbyText = ""; vybrana = -1; volbyUpravaDo = 0L
            trvaleOblast = false; pribehRidi = false
            cislo++; cisloVoleb++
            posliTed()
            posliData { zpravaVoleb() }
            oblastTitulku(floatArrayOf(0f, 0f, 1f, 1f), false)
            spustOpakovani(generace)
        }

        /** Každou sekundu aktuální stav; stará smyčka nesmí po změně cíle ožít. */''',
            "stop game data while retaining receiver selection")
        sender = one(sender,
            '''                                    if (!pribehRidi) {
                                        val data = zprava()''',
            '''                                    val live = RemotePlayService.aktivni
                                    val now = System.currentTimeMillis()
                                    val repeatDialogue = !pribehRidi && (live || now < upravaDo)
                                    val repeatChoices = cisloVoleb > 0 && (live || now < volbyUpravaDo)
                                    if (repeatDialogue) {
                                        val data = zprava()''',
            "bound idle dialogue preview repeats")
        sender = one(sender,
            '                                    if (cisloVoleb > 0) {',
            '                                    if (repeatChoices) {',
            "bound idle choices preview repeats")
        sender = one(sender,
            '                                    if (!pribehRidi || cisloVoleb > 0) prenos.vysledek(relace, null)',
            '                                    if (repeatDialogue || repeatChoices) prenos.vysledek(relace, null)',
            "report actual repeats only")
        if mobile_dashboard:
            # PCV1 already accepts these optional fields on GameLingo TV.
            # Keep the legacy yellow ROI, selected item and text positions unchanged.
            module = Path(__file__).with_name("GameLingoOrangeOutput.kt")
            (folder/module.name).write_text(module.read_text(encoding="utf-8"), encoding="utf-8")
            sender = one(sender,
                '        @Volatile private var volbyUpravaDo = 0L',
                '''        private var orangeOutput = GameLingoOrangeOutput.defaults()
        private var orangePreview: String? = null
        private var orangePreviewUntil = 0L
        private val orangePreviewHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private val clearOrangePreview = Runnable {
            synchronized(zamek) {
                if (orangePreview != null && android.os.SystemClock.elapsedRealtime() >= orangePreviewUntil) {
                    orangePreview = null; orangePreviewUntil = 0L; cisloVoleb++
                    posliData { zpravaVoleb() }
                }
            }
        }

        fun nastavOranzovyVystup(rect: FloatArray, preview: String? = null) {
            if (!GameLingoOrangeOutput.valid(rect)) return
            synchronized(zamek) {
                orangeOutput = rect.copyOf()
                orangePreviewHandler.removeCallbacks(clearOrangePreview)
                orangePreview = preview?.takeIf { cilIp != null && volbyText.isBlank() }
                orangePreviewUntil = if (orangePreview != null) android.os.SystemClock.elapsedRealtime() + 3000 else 0L
                cisloVoleb++
                if (orangePreview != null) orangePreviewHandler.postDelayed(clearOrangePreview, 3000)
                posliData { zpravaVoleb() }
            }
        }

        @Volatile private var volbyUpravaDo = 0L''', "orange sender state")
            sender = one(sender,
                '                if (t == volbyText && vyb == vybrana) return',
                '''                if (t.isNotBlank() && orangePreview != null) {
                    orangePreviewHandler.removeCallbacks(clearOrangePreview)
                    orangePreview = null; orangePreviewUntil = 0L
                }
                if (t == volbyText && vyb == vybrana) return''', "real choices take precedence")
            sender = one(sender,
                '            "$VOLBY\\t$cisloVoleb\\t$ramecek\\t${o[0]}\\t${o[1]}\\t${o[2]}\\t${o[3]}\\t$vybrana\\t${escapuj(volbyText)}".toByteArray(Charsets.UTF_8)',
                '''            val preview = orangePreview?.takeIf {
                volbyText.isBlank() && android.os.SystemClock.elapsedRealtime() < orangePreviewUntil
            }
            val displayText = preview ?: volbyText
            val selection = if (preview != null) -2 else vybrana
            val r = orangeOutput
            val suffix = r.joinToString(separator = "\\t", prefix = "\\t")
            "$VOLBY\\t$cisloVoleb\\t$ramecek\\t${o[0]}\\t${o[1]}\\t${o[2]}\\t${o[3]}\\t$selection\\t${escapuj(displayText)}$suffix".toByteArray(Charsets.UTF_8)''',
                "PCV1 orange suffix")
            reset = 'volbyText = ""; vybrana = -1; volbyUpravaDo = 0L'
            if sender.count(reset) != 2:
                raise RuntimeError("Expected target change and stop choice resets")
            sender = sender.replace(reset, reset + '''
                orangePreviewHandler.removeCallbacks(clearOrangePreview)
                orangePreview = null; orangePreviewUntil = 0L''')
            sender = one(sender,
                'val repeatChoices = cisloVoleb > 0 && (live || now < volbyUpravaDo)',
                'val repeatChoices = cisloVoleb > 0 && (live || now < volbyUpravaDo || android.os.SystemClock.elapsedRealtime() < orangePreviewUntil)',
                "bounded orange preview repeats")

            phone = folder/"TelefonActivity.kt"
            phone_code = phone.read_text(encoding="utf-8")
            # Reload selected receiver preferences before publishing to the new target.
            phone_code = one(phone_code,
                '        Propojeni.Vysilac.nastavCil(tv.ip, tv.jmeno)',
                '        GameLingoOrangeOutput.publish(this)\n        Propojeni.Vysilac.nastavCil(tv.ip, tv.jmeno)',
                "restore per-TV orange geometry")
            phone_code = one(phone_code,
                '        // televize musí znát polohu rámečku voleb (český seznam ukazuje vedle něj)',
                '        if (Propojeni.Vysilac.cilIp == null || savedInstanceState != null) GameLingoOrangeOutput.publish(this)\n        // televize musí znát polohu rámečku voleb (český seznam ukazuje vedle něj)',
                "restore initial orange geometry")
            phone_code = one(phone_code,
                '        // ---- volby odpovědí: vypínač + zámeček + velikost žlutého rámečku',
                '''        obal.addView(GameLingoOrangeOutput.editor(this) {},
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        // ---- volby odpovědí: vypínač + zámeček + velikost žlutého rámečku''',
                "orange editor in existing regions screen")
            phone.write_text(phone_code, encoding="utf-8")

        # SEC-02: remove the account migration implementation, including the
        # credential-bearing DATA response and its client. This is independent
        # of the signed subtitle receiver and of console registration/login.
        start = prefix.index('    /**\n     * „+ Přidat Android zařízení“')
        end = prefix.index('    // ================================================================ TELEVIZE', start)
        prefix = prefix[:start] + prefix[end:]
        start = prefix.index('        // převzetí spárování telefonem')
        end = prefix.index('        private fun odpovez(', start)
        prefix = prefix[:start] + prefix[end:]
        start = prefix.index('        private fun sparovani(s: DatagramSocket')
        end = prefix.index('\n        }', start) + len('\n        }')
        prefix = prefix[:start] + '''        private fun sparovani(s: DatagramSocket, p: DatagramPacket, casti: List<String>) {
            // Reject legacy clients without ever accessing console credentials.
            odpovez(s, p, "$SPAROVANI\\tCHYBA\\tzakazano")
        }''' + prefix[end:]
        start = prefix.index('    fun sparovaniDotaz(')
        end = prefix.index('\n    }', start) + len('\n    }')
        prefix = prefix[:start] + prefix[end:]
        # Remove all inherited account-transfer entry points and implementation.
        start = code.index('    private fun prevzitSparovaniZTv() {')
        end = code.index('    private fun vyberVystupTv() {', start)
        code = code[:start] + code[end:]
        start = code.index('    private fun prevzitOd(')
        end = code.index('    private fun zprava(', start)
        code = code[:start] + code[end:]
        lines = code.splitlines(keepends=True)
        code = ''.join(line for line in lines if not (
            'prevzitSparovaniZTv()' in line or 'pridatZarizeni()' in line))
        for forbidden in ('Propojeni.Pridani', 'sparovaniDotaz(', 'XboxUcet.prevezmi(', 'XboxUcet.proPredani('):
            if forbidden in prefix or forbidden in code:
                raise RuntimeError("Legacy account transfer remains: " + forbidden)
        # The old exporter/importer is not merely unreachable: remove it.
        xbox = folder / "XboxUcet.kt"
        xbox_source = xbox.read_text(encoding="utf-8")
        start = xbox_source.index('    /**\n     * Pro „Vyhledat televizi“ v telefonu:')
        end = xbox_source.index('    fun ulozKonzoli(', start)
        xbox_source = xbox_source[:start] + xbox_source[end:]
        if 'fun proPredani(' in xbox_source or 'fun prevezmi(' in xbox_source:
            raise RuntimeError("Obsolete Xbox account migration remains")
        xbox.write_text(xbox_source, encoding="utf-8")

        # In the inherited fallback home screen this code used to hide the
        # only independent PS sign-in when a paired TV was discovered.
        # It is redundant now that accounts are device-local.
        begin = '// telefon: najít televizi a převzít od ní spárování s PS5'
        if begin in code:
            start = code.index(begin)
            end = code.index('// ---------- co překládat', start)
            code = code[:start] + code[end:]
        transport.write_text(prefix + sender_marker + sender, encoding="utf-8")

        registration=folder/"RemotePlay.kt"
        source=registration.read_text(encoding="utf-8")
        source=one(source,
            "    var registKey: ByteArray?",
            "    var consoleTarget: Target\n        get() = Target.values().firstOrNull { it.value == prefs.getInt(\"ps_target\", Target.PS5_1.value) }\n            ?: Target.PS5_1\n        set(value) { prefs.edit().putInt(\"ps_target\", value.value).apply() }\n\n    val isPS5: Boolean get() = consoleTarget.value >= 1_000_000\n    val consoleType: String get() = if (isPS5) \"PS5\" else \"PS4\"\n\n    var registKey: ByteArray?",
            "persist actual target")
        source=one(source,
            "        pin: Int,\n        log: (String) -> Unit,",
            "        pin: Int,\n        target: Target = Target.PS5_1,\n        log: (String) -> Unit,",
            "explicit registration target parameter")
        source=one(source,
            "target = Target.PS5_1,",
            "target = target,",
            "native registration target")
        source=one(source,
            "                        udaje.ipKonzole = ip",
            "                        udaje.consoleTarget = udalost.host.target\n                        udaje.ipKonzole = ip",
            "record successful registration target")
        registration.write_text(source, encoding="utf-8")

        # Story-mode packets bypass Vysilac. Protect the independent socket too.
        story=folder/"PribehSpojeni.kt"
        source=story.read_text(encoding="utf-8")
        source=one(source, 'sit.connect(InetAddress.getByName(ip), port)',
            'sit.connect(InetAddress.getByName(ip), GameLingoTVPairing.storyPort(ip))', "story port")
        source=one(source, 'runCatching { sit.send(DatagramPacket(data, data.size)) }',
            'runCatching { val signed = GameLingoTVPairing.signedPacket(ip, data); sit.send(DatagramPacket(signed, signed.size)) }',
            "signed story messages")
        source=one(source, 'repeat(3) { runCatching { socket?.send(DatagramPacket(data, data.size)) } }',
            'repeat(3) { runCatching { val signed = GameLingoTVPairing.signedPacket(ip, data); socket?.send(DatagramPacket(signed, signed.size)) } }',
            "signed story clear")
        story.write_text(source, encoding="utf-8")
        service=folder/"RemotePlayService.kt"
        source=service.read_text(encoding="utf-8")
        source=one(source, "        super.onCreate()",
            "        super.onCreate()\n        GameLingoTVPairing.initialize(this)", "background sender context")
        source=one(source,
            '        if (pripojeno()) zastav("Remote Play zastaveno.")\n        ukonciStareSpojeni()',
            '        if (pripojeno()) zastav("Remote Play zastaveno.")\n'
            '        if (telefon) Propojeni.Vysilac.stopGameData()\n'
            '        ukonciStareSpojeni()',
            "clear queued TV game data on service destruction")
        source=one(source,
            "                val ps5 = seznam.firstOrNull { it.ip == ip } ?: seznam.firstOrNull { it.typ == \"PS5\" }\n                if (ps5 != null && ps5.ip != ip) udaje.ipKonzole = ps5.ip\n                ps5?.stav == \"zapnutá\"",
            "                val selected = seznam.firstOrNull { it.ip == ip && it.typ == udaje.consoleType }\n                selected?.stav == \"zapnutá\"",
            "do not substitute another console")
        source=one(source,
            "val hevc = telefon && !prefsObraz.getBoolean(\"bezHevc\", false) && umiHevc()",
            "val hevc = udaje.isPS5 && telefon && !prefsObraz.getBoolean(\"bezHevc\", false) && umiHevc()",
            "PS4 H264")
        source=one(source,
            "ConnectInfo(ps5 = true, host = ip,",
            "ConnectInfo(ps5 = udaje.isPS5, host = ip,",
            "native PS4 or PS5 stream")
        source=one(source,
            "HledacPS.hledej(1500, primaIp = udaje.ipKonzole).any { it.typ == \"PS5\" && it.stav == \"zapnutá\" }",
            "HledacPS.hledej(1500, primaIp = udaje.ipKonzole).any { it.ip == udaje.ipKonzole && it.typ == udaje.consoleType && it.stav == \"zapnutá\" }",
            "selected console switch availability")
        service.write_text(source, encoding="utf-8")

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
    if mobile_dashboard:
        text=one(text, 'private val KLICE = setOf("tvX",',
            'private val KLICE = setOf("orangeL", "orangeT", "orangeR", "orangeB", "orangeFont", "orangeLocked", "tvX",',
            "migrate first receiver orange preferences")
    profiles.write_text(text,encoding="utf-8")
    print("GameLingo Android/TV: independent device center + secure multi-PS5 saved console switching installed")

if __name__=="__main__":
    main()
