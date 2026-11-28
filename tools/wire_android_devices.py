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
    for name in ("GameLingoDeviceCenter.kt","GameLingoPlayStationConsoles.kt","GameLingoFirstRun.kt","GameLingoAbout.kt","GameLingoNavigation.kt","GameLingoUIContract.kt","GameLingoVisualKit.kt","GameLingoLanguages.kt","GameLingoTVUi.kt","GameLingoTVPairing.kt","GameLingoWireCrypto.kt","GameLingoPSLoginPIN.kt"):
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
                    onTV = { if (telefon) vyberVystupTv() else if (android.provider.Settings.canDrawOverlays(this)) {
                        if (PrijemService.bezi) ukazPrijem() else hratSTelefonem()
                    } else pozadejOPovoleni() },
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
        # Standalone camera is compiled only into the mobile APK.
        g=one(g,'dependencies {','dependencies {\n    implementation("androidx.activity:activity:1.8.2")\n    implementation("androidx.camera:camera-core:1.3.4")\n    implementation("androidx.camera:camera-camera2:1.3.4")\n    implementation("androidx.camera:camera-lifecycle:1.3.4")\n    implementation("androidx.camera:camera-view:1.3.4")',"mobile camera dependencies")
        gradle.write_text(g,encoding="utf8")
        module=Path(__file__).with_name("GameLingoCameraActivity.kt")
        (folder/module.name).write_text(module.read_text(encoding="utf8"),encoding="utf8")
        manifest=upstream/"app/src/main/AndroidManifest.xml"
        m=manifest.read_text(encoding="utf8")
        if 'android.permission.CAMERA' not in m:
            m=one(m,'    <application','    <uses-permission android:name="android.permission.CAMERA"/>\n    <application',"mobile camera permission")
        m=one(m,'    <application','    <uses-feature android:name="android.hardware.camera" android:required="false"/>\n    <application',"optional camera hardware")
        m=one(m,'    </application>','        <activity android:name=".GameLingoCameraActivity" android:exported="false" android:screenOrientation="portrait"/>\n    </application>',"mobile camera activity")
        manifest.write_text(m,encoding="utf8")
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
        // No delayed upstream splash callback may replace the onboarding page.
        if (nast.jazyk == null) nast.jazyk = GameLingoLanguages.systemApp(this)
        ukazHlavni()''',
            "mobile first-run wizard"
        )
        code=one(code, "    private fun ukazHlavni() {",
            '''    private fun ukazHlavni() {
        if (telefon && !GameLingoFirstRun.completed(this)) ui.post {
            if (isFinishing || isDestroyed || obrazovka != "hlavni") return@post
            GameLingoFirstRun.show(this,
                connectXbox = { ukazPripojeniXbox() },
                connectPlayStation = { ukazPripojeni() },
                connectTV = { vyberVystupTv() },
                finished = { ukazHlavni() })
        }''', "resume incomplete onboarding on dashboard")
        # The native dashboard supplies the same central device action to both
        # the fourth home tile and fifth-row navigation, without string patches.
        if 'fun openDevices() {' not in code or 'GameLingoDeviceCenter.content(this,' not in code:
            raise RuntimeError("Mobile GameLingo device screen missing")
        if 'onDevices = { openDevices() }' not in code:
            raise RuntimeError("Mobile bottom navigation is not wired to devices")

    elif tv_dashboard:
        code=one(code, "        GameLingoTVPairing.initialize(this)",
            "        GameLingoTVPairing.initialize(this)\n        IPhoneTVPairing.initialize(this)\n        GameLingoLanguages.directTVBusy = { GameLingoScreenTranslationService.running }",
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
            // The two actions are aligned with the two source cards, not one full-width button.
            val stahnoutMobil = GameLingoTVUi.action(this, GameLingoLanguages.ui(this, "Stáhnout aplikaci")) {
                GameLingoMobileDownloads.show(this)
            }.apply {
                id = View.generateViewId()
                setCompoundDrawablesWithIntrinsicBounds(GameLingoVisualKit.iconDrawable("download",Color.WHITE,dp(23)),null,null,null)
            }
            val spravaZarizeni = GameLingoTVUi.action(this, GameLingoLanguages.ui(this, "Zařízení")) {
                '''+manager()+'''
            }.apply {
                id = View.generateViewId()
                setCompoundDrawablesWithIntrinsicBounds(GameLingoVisualKit.iconDrawable("display.2",Color.WHITE,dp(23)),null,null,null)
            }
            val sourceActions = vodorovne().apply {
                clipChildren=false;clipToPadding=false
                setPadding(dp(8),dp(2),dp(8),dp(8))
                addView(stahnoutMobil,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{rightMargin=dp(20)})
                addView(spravaZarizeni,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            }
            dlazdiceTel.nextFocusDownId=stahnoutMobil.id
            dlazdiceTv.nextFocusDownId=spravaZarizeni.id
            stahnoutMobil.nextFocusUpId=dlazdiceTel.id
            spravaZarizeni.nextFocusUpId=dlazdiceTv.id
            stahnoutMobil.nextFocusRightId=spravaZarizeni.id
            spravaZarizeni.nextFocusLeftId=stahnoutMobil.id
            obsah.addView(sourceActions, odsazeni(bottom = 9))''',
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

    # Advanced account-ID fallback, equivalent to iOS; no passwords or tokens.
    code=one(code, "        sloupec.addView(k1, odsazeni(bottom = 12))\n\n        // 2) hledání PS5",
        '''        k1.addView(tlacitko(GameLingoLanguages.ui(this, "ID účtu (Base64)"), 16f) {
            val input = EditText(this).apply {
                hint = GameLingoLanguages.ui(this@MainActivity, "Base64 ID účtu")
                isSaveEnabled = false
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                setSingleLine()
            }
            val dlg = android.app.AlertDialog.Builder(this)
                .setTitle(GameLingoLanguages.ui(this, "ID účtu (Base64)"))
                .setView(input)
                .setPositiveButton(GameLingoLanguages.ui(this, "Ověřit účet"), null)
                .setNegativeButton(GameLingoLanguages.ui(this, "Zrušit"), null)
                .create()
            dlg.show()
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val bytes = runCatching { android.util.Base64.decode(input.text.toString().trim(), android.util.Base64.DEFAULT) }.getOrNull()
                if (bytes == null || bytes.size != 8) {
                    input.error = GameLingoLanguages.ui(this, "Base64 ID účtu")
                } else {
                    zastavServer()
                    udaje.accountId = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    input.text.clear()
                    dlg.dismiss()
                    ukazPripojeni()
                }
            }
        }, obal(top = 8))
        sloupec.addView(k1, odsazeni(bottom = 12))

        // 2) hledání PS5''', "manual PlayStation account ID")

    # Explicit PS4/PS5 type, host and name, also when discovery fails.
    code=one(code, "        fun hledej() {\n            hledaniStav.text = t(\"hledam\")\n            nalezenaIp = null",
        '''        val psType = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("PS5", "PS4"))
        }
        val psHost = EditText(this).apply {
            hint = GameLingoLanguages.ui(this@MainActivity, "IP adresa konzole")
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.WHITE)
            setHintTextColor(TEXT2)
        }
        val psName = EditText(this).apply {
            hint = GameLingoLanguages.ui(this@MainActivity, "Název konzole")
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(TEXT2)
        }
        k2.addView(psType, LinearLayout.LayoutParams(-1, -2))
        k2.addView(psHost, LinearLayout.LayoutParams(-1, -2))
        k2.addView(psName, LinearLayout.LayoutParams(-1, -2))
        fun hledej() {
            hledaniStav.text = t("hledam")
            nalezenaIp = null''', "manual PlayStation host and type")
    code=one(code, "                        nalezenaIp = ps.ip\n",
        '''                        nalezenaIp = ps.ip
                        psHost.setText(ps.ip)
                        psType.setSelection(if (ps.typ == "PS4") 1 else 0)
                        psName.setText(ps.jmeno)
''', "populate manual PlayStation form")
    code=one(code, '''            val selectedPS = nalezenaPS
            val ip = nalezenaIp
            val target = if (selectedPS?.typ == "PS4")''',
        '''            val ip = psHost.text.toString().trim().takeIf { android.util.Patterns.IP_ADDRESS.matcher(it).matches() }
            val consoleName = psName.text.toString().trim().ifBlank { if (psType.selectedItemPosition == 1) "PS4" else "PS5" }
            val target = if (psType.selectedItemPosition == 1)''', "use explicit manual PlayStation target")
    code=one(code, "                        1 -> selectPS(supported.single())",
        "                        1 -> if (psHost.text.isBlank()) selectPS(supported.single()) else { hledaniStav.text = psHost.text.toString() }", "do not overwrite a manually entered host")
    code=one(code, 'selectedPS?.jmeno ?: "PlayStation", ip)', 'consoleName, ip)', "save explicit console name")
    code=one(code, '            hint = "12345678"\n            isEnabled = lzeParovat',
        '''            hint = GameLingoLanguages.ui(this@MainActivity, "8místný PIN z PlayStation")
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
            isSaveEnabled = false
            isEnabled = true''', "visible registration PIN")
    code=one(code, 'k3.addView(pin, LinearLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT)',
        'k3.addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)', "responsive registration PIN")
    code=one(code, 'val cislo = pin.text.toString().filter { it.isDigit() }',
        "val cislo = pin.text.toString().filter { it in '0'..'9' }", "ASCII registration PIN")
    code=one(code, '}.apply { isEnabled = lzeParovat; alpha = if (lzeParovat) 1f else 0.5f }, obal(top = 10))',
        '}, obal(top = 10))', "show account validation on pairing action")
    code=one(code, "                            if (ok) {\n                                if (ip != null)",
        "                            if (ok) {\n                                pin.text.clear()\n                                RemotePlayService.prepni(this, false)\n                                getSharedPreferences(\"gamelingo_devices\", MODE_PRIVATE).edit().putBoolean(\"cameraSourceSelected\", false).apply()\n                                if (ip != null)", "clear one-time PIN")

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
        # Do not replay an expired subtitle when the TV receiver restarts.
        # Keep the expiry monotonic, and send an explicit clear with a new sequence.
        sender = one(sender,
            '        @Volatile private var doba = 0L',
            '        @Volatile private var doba = 0L\n        private var captionDeadline = 0L',
            "caption monotonic deadline")
        sender = one(sender,
            'text = t; ukazkaText = t; doba = 15_000;',
            'text = t; ukazkaText = t; doba = 15_000; captionDeadline = android.os.SystemClock.elapsedRealtime() + 15_000L;',
            "preview caption deadline")
        sender = one(sender,
            '                text = t; doba = d; napoveda = n; barvy = b',
            '''                if (t != text) {
                    captionDeadline = if (t.isEmpty()) 0L else android.os.SystemClock.elapsedRealtime() +
                        (if (d > 0L) d.coerceAtMost(15_000L) else 6_000L)
                }
                text = t; doba = d; napoveda = n; barvy = b''',
            "caption deadline only for new text")
        sender = one(sender,
            '        private fun zprava(): ByteArray = synchronized(zamek) {',
            '''        private fun zprava(): ByteArray = synchronized(zamek) {
            val remaining = (captionDeadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (text.isNotEmpty() && remaining == 0L) {
                text = ""; doba = 0L; barvy = null; captionDeadline = 0L
                cislo++
            } else if (text.isNotEmpty()) {
                doba = remaining
            } else {
                doba = 0L; captionDeadline = 0L
            }''',
            "expire caption before serializing")
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
                                    if (live) GameLingoTVPairing.verifyActive(ip, relace)
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
            # Camera caption refresh uses the existing authenticated sender,
            # but never overrides an active console or receiver session.
            sender = one(sender, '        fun stopGameData() = synchronized(zamek) {',
                '''        fun cameraCaption(value: String, duration: Long) = synchronized(zamek) {
            val ip = cilIp ?: return@synchronized
            if (RemotePlayService.aktivni || PrijemService.bezi) return@synchronized
            if (value.isNotEmpty() && !GameLingoTVPairing.connected(ip)) return@synchronized
            text = value; doba = duration.coerceIn(0L, 6000L); napoveda = ""; barvy = null
            captionDeadline = if (value.isEmpty()) 0L else android.os.SystemClock.elapsedRealtime() + doba
            cislo++
            posliTed()
        }

        fun stopGameData() = synchronized(zamek) {''',
                "authenticated mobile camera caption refresh")
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
            phone_code = one(phone_code,
                "    private fun postavUpravy(): LinearLayout {",
                "    private var gamelingoSelectedRegion = 1\n\n    private fun postavUpravy(): LinearLayout {",
                "selected region persists across editor rebuilds")
            phone_code = one(phone_code,
                "        obrazovka.addView(modry, FrameLayout.LayoutParams(10, 10))\n        fun prekresli() {",
                "        obrazovka.addView(modry, FrameLayout.LayoutParams(10, 10))\n        val orangeFrame = SrafovanyRamecek(this,\n            GameLingoLanguages.ui(this, \"Oranžová\"), Color.parseColor(\"#FF9E34\"))\n        obrazovka.addView(orangeFrame, FrameLayout.LayoutParams(10, 10))\n        modry.visibility = View.VISIBLE\n        ramecek.visibility = View.VISIBLE\n        val selection = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }\n        val regionFrames = listOf(modry, box, ramecek, orangeFrame)\n        val tabs = mutableListOf<TextView>()\n        val regionLabels = listOf(\"Modrá\", \"Zelená\", \"Žlutá\", \"Oranžová\")\n        val regionColors = listOf(OblastTitulku.MODRA, zelena, Color.parseColor(\"#FFD54A\"), Color.parseColor(\"#FF9E34\"))\n        fun renderSelection() {\n            tabs.forEachIndexed { index, tab ->\n                tab.text = (if (gamelingoSelectedRegion == index) \"✓ \" else \"\") + GameLingoLanguages.ui(this, regionLabels[index])\n                tab.isSelected = gamelingoSelectedRegion == index\n                tab.background = GameLingoVisualKit.card(this, selected = tab.isSelected)\n            }\n            regionFrames[gamelingoSelectedRegion].bringToFront()\n        }\n        regionLabels.forEachIndexed { index, label ->\n            val tab = TextView(this).apply {\n                text = GameLingoLanguages.ui(this@TelefonActivity, label)\n                textSize = GameLingoVisualKit.sp(this@TelefonActivity, 12f)\n                gravity = Gravity.CENTER\n                minHeight = dp(44)\n                setTextColor(regionColors[index])\n                isFocusable = true\n                setOnClickListener { gamelingoSelectedRegion = index; renderSelection() }\n            }\n            tabs.add(tab)\n            selection.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) })\n        }\n        obal.addView(selection)\n        renderSelection()\n        fun prekresli() {",
                "four explicitly selected regions on one preview")
            phone_code = one(phone_code,
                "            val m = OblastTitulku.oblast\n            modry.layoutParams",
                "            val orange = GameLingoOrangeOutput.read(this)\n            orangeFrame.layoutParams = (orangeFrame.layoutParams as FrameLayout.LayoutParams).apply {\n                width = ((orange[2]-orange[0])*w).toInt().coerceAtLeast(1)\n                height = ((orange[3]-orange[1])*h).toInt().coerceAtLeast(1)\n                leftMargin = (orange[0]*w).toInt(); topMargin = (orange[1]*h).toInt()\n            }\n            modry.alpha = if (OblastTitulku.rucne) 1f else .46f\n            ramecek.alpha = if (Volby.zapnuto) 1f else .46f\n            orangeFrame.alpha = if (Volby.zapnuto) 1f else .46f\n            val m = OblastTitulku.oblast\n            modry.layoutParams",
                "show inactive regions dimmed and orange geometry")
            phone_code = one(phone_code,
                "        var zamcenyDotyk = false\n        var chytX",
                "        var zamcenyDotyk = false\n        var orangeDragging = false\n        val regionReceiverId = TvProfily.aktivniId\n        val regionReceiverIp = Propojeni.Vysilac.cilIp\n        var chytX",
                "capture region receiver identity")
            phone_code = one(phone_code,
                "            if (e.action == android.view.MotionEvent.ACTION_DOWN) ui.removeCallbacks(konecUprav)",
                "            if (e.action == android.view.MotionEvent.ACTION_DOWN) ui.removeCallbacks(konecUprav)\n            if (gamelingoSelectedRegion == 3) {\n                val sameReceiver = TvProfily.aktivniId == regionReceiverId && Propojeni.Vysilac.cilIp == regionReceiverIp\n                if (e.action == android.view.MotionEvent.ACTION_DOWN)\n                    orangeDragging = sameReceiver && Volby.zapnuto && !GameLingoOrangeOutput.locked(this)\n                if (orangeDragging && sameReceiver && Volby.zapnuto && !GameLingoOrangeOutput.locked(this) &&\n                    w > 0 && h > 0 && e.action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP)) {\n                    val moved = GameLingoOrangeOutput.moveCenter(GameLingoOrangeOutput.read(this), e.x/w, e.y/h)\n                    GameLingoOrangeOutput.save(this, moved, false)\n                    prekresli()\n                    val now = android.os.SystemClock.elapsedRealtime()\n                    if (e.action == android.view.MotionEvent.ACTION_UP || now-lastOrangePublish > 150L) {\n                        lastOrangePublish = now\n                        GameLingoOrangeOutput.publish(this)\n                    }\n                }\n                if (e.action == android.view.MotionEvent.ACTION_UP || e.action == android.view.MotionEvent.ACTION_CANCEL) {\n                    val changed = orangeDragging\n                    orangeDragging = false\n                    v.parent?.requestDisallowInterceptTouchEvent(false)\n                    if (changed && sameReceiver) {\n                        GameLingoOrangeOutput.publish(this)\n                        v.post { if (v.isAttachedToWindow) obnovUpravy() }\n                    }\n                }\n                return@setOnTouchListener true\n            }",
                "orange drag independent of blue and yellow")
            phone_code = one(phone_code,
                "        var posledniPoslani = 0L\n",
                "        var posledniPoslani = 0L\n        var lastOrangePublish = 0L\n",
                "throttle orange preview")
            phone_code = one(phone_code,
                "                val vModrem = OblastTitulku.rucne && fx in m[0]..m[2] && fy in m[1]..m[3]\n                val vZlutem = !vModrem && Volby.zapnuto && fx in o[0]..o[2] && fy in o[1]..o[3]\n                // zamčený rámeček se nehne (a prst v něm nepohne ani ničím jiným)\n                zamcenyDotyk = (vModrem && OblastTitulku.zamceno) || (vZlutem && Volby.zamceno) ||\n                    (!vModrem && !vZlutem && zelenaZamcena)",
                "                val vModrem = gamelingoSelectedRegion == 0\n                val vZlutem = gamelingoSelectedRegion == 2\n                // Only the explicitly selected region may move, regardless of overlap.\n                zamcenyDotyk = (vModrem && (!OblastTitulku.rucne || OblastTitulku.zamceno)) ||\n                    (vZlutem && (!Volby.zapnuto || Volby.zamceno)) ||\n                    (gamelingoSelectedRegion == 1 && zelenaZamcena)",
                "explicit selection instead of overlap hit test")
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
                '''        obal.addView(GameLingoOrangeOutput.editor(this, includePreview = false) { prekresli() },
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
        # Scope PS4/PS5 delayed work to the native session that scheduled it.
        # The existing event guard alone cannot protect delayed warmups.
        start = source.index("    private fun onUdalost(udalost: Event) {")
        end = source.index("    /** Ukončit všechna spojení", start)
        ps_events = source[start:end]
        ps_events = one(ps_events,
            '                hlidaniPs5.pripojeno(session)',
            '                val connectedSession = session ?: return\n                val connectedGeneration = relaceCteni.get()\n                hlidaniPs5.pripojeno(session)',
            "PS session identity for delayed tasks")
        ps_events = one(ps_events,
            '                ui.postDelayed({ if (session != null) runCatching { AutoStart.uspech(this) } }, 60_000)',
            '                ui.postDelayed({\n                    if (session === connectedSession && connectedGeneration == relaceCteni.get() && bezi)\n                        runCatching { AutoStart.uspech(this) }\n                }, 60_000)',
            "PS delayed success belongs to current session")
        ps_events = one(ps_events,
            '                ui.postDelayed({ zahrejCtecku() }, 1500)',
            '                ui.postDelayed({\n                    if (session === connectedSession && connectedGeneration == relaceCteni.get() && bezi) zahrejCtecku()\n                }, 1500)',
            "PS delayed OCR warmup belongs to current session")
        ps_events = one(ps_events,
            '''            is LoginPinRequestEvent -> {
                automat = false
                val duvod = "PS5 chce PIN pro přihlášení – zatím nepodporováno ✘"
                zastav(duvod)
                obrazStav.ukonciChybou(duvod)
                stopSelf()
            }''',
            '''            is LoginPinRequestEvent -> {
                val requesting = session ?: return
                stav = GameLingoLanguages.ui(this, "Zadej přihlašovací PIN PlayStation")
                GameLingoPSLoginPIN.request(this,
                    submit = { pin ->
                        if (session === requesting && !zanikla) {
                            runCatching { requesting.setLoginPin(pin) }.onFailure {
                                automat = false
                                zastav(GameLingoLanguages.ui(this, "PlayStation odpojen"))
                                stopSelf()
                            }
                        }
                    },
                    cancel = {
                        if (session === requesting && !zanikla) {
                            automat = false
                            zastav(GameLingoLanguages.ui(this, "PlayStation odpojen"))
                            stopSelf()
                        }
                    })
            }''', "support native PlayStation login PIN")
        ps_events = one(ps_events, "                val connectedSession = session ?: return",
            "                GameLingoPSLoginPIN.clear(this)\n                val connectedSession = session ?: return", "clear login PIN after connection")
        source = source[:start] + ps_events + source[end:]
        source = one(source, "    private fun zastav(zprava: String, ukazat: Boolean = true) {",
            "    private fun zastav(zprava: String, ukazat: Boolean = true) {\n        GameLingoPSLoginPIN.clear(this)", "clear login PIN on stop")
        source = one(source, "            session = null\n            ukonciStareSpojeni()\n            val s = Session(info, logSoubor, false)",
            "            GameLingoPSLoginPIN.clear(this)\n            session = null\n            ukonciStareSpojeni()\n            val s = Session(info, logSoubor, false)", "clear PIN before new native session")
        source = one(source, "        zanikla = true\n        ui.removeCallbacks(pribehHlidac)",
            "        zanikla = true\n        GameLingoPSLoginPIN.clear(this)\n        ui.removeCallbacks(pribehHlidac)", "clear login PIN on destroy")
        service.write_text(source, encoding="utf-8")

    if mobile_dashboard:
        phone = folder / "TelefonActivity.kt"
        phone_code = phone.read_text(encoding="utf8")
        # Reuse proven drag/slider geometry without opening a translation session.
        phone_code = one(phone_code,
            "    override fun onCreate(savedInstanceState: Bundle?) {",
            """    private val editorOnly: Boolean
        get() = intent.getBooleanExtra("gamelingo_editor_only", false)

    private fun showStandaloneEditor() {
        OblastTitulku.nacti(this)
        Volby.nacti(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.pageBackground(this@TelefonActivity)
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        val back = GameLingoTVUi.action(this, GameLingoLanguages.ui(this, "Zpět")) { finish() }
        root.addView(GameLingoTVUi.header(this,
            GameLingoTVUi.title(this, GameLingoLanguages.ui(this, "Oblasti obrazovky"), 22f),
            back, GameLingoHelp.button(this, "regions")))
        upravy = postavUpravy().apply { visibility = View.VISIBLE }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(upravy, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(container)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {""",
            "separate standard region editor")
        phone_code = one(phone_code,
            "        super.onCreate(savedInstanceState)",
            """        super.onCreate(savedInstanceState)
        if (editorOnly) {
            showStandaloneEditor()
            return
        }""", "editor never starts remote play")
        phone_code = one(phone_code, "        super.onResume()\n        ui.removeCallbacks(obnova)",
            "        super.onResume()\n        if (editorOnly) return\n        ui.removeCallbacks(obnova)", "editor no live refresh")
        phone_code = one(phone_code, "        super.onStart()\n        vPozadi = false",
            "        super.onStart()\n        if (editorOnly) return\n        vPozadi = false", "editor no background translation lifecycle")
        phone_code = one(phone_code, "    override fun onStop() {\n        if (!isChangingConfigurations",
            "    override fun onStop() {\n        if (editorOnly) { super.onStop(); return }\n        if (!isChangingConfigurations", "editor no delayed stop")
        phone_code = one(phone_code, "    override fun onBackPressed() {\n        if (RemotePlayService.aktivni)",
            "    override fun onBackPressed() {\n        if (editorOnly) { finish(); return }\n        if (RemotePlayService.aktivni)", "editor back returns to settings")
        phone_code = one(phone_code, "        if (isFinishing) zastav()",
            "        if (editorOnly) ui.removeCallbacksAndMessages(null)\n        else if (isFinishing) zastav()", "editor cannot stop live service")
        phone_code = one(phone_code, "        super.onConfigurationChanged(newConfig)\n        prepniSirku(",
            "        super.onConfigurationChanged(newConfig)\n        if (editorOnly) return\n        prepniSirku(", "rotation stays in editor")
        # No inherited TV selection or LG developer menu inside offline geometry.
        phone_code = one(phone_code,
            '        obal.addView(TextView(this).apply {\n            text = "▣  Oblasti na obrazovce · "',
            '        if (!editorOnly) obal.addView(TextView(this).apply {\n            text = "▣  Oblasti na obrazovce · "', "hide legacy TV control in editor")
        phone_code = one(phone_code, "        obal.addView(LgDevModeView(this) {",
            "        if (!editorOnly) obal.addView(LgDevModeView(this) {", "hide legacy LG setup in editor")
        phone_code = one(phone_code, "        if (editorOnly) return\n        ui.removeCallbacks(obnova)",
            "        if (editorOnly) return\n        GameLingoPSLoginPIN.resume(this)\n        ui.removeCallbacks(obnova)", "login PIN on phone player")
        phone_code = one(phone_code, "    override fun onPause() {",
            "    override fun onPause() {\n        GameLingoPSLoginPIN.pause(this)", "dismiss PIN prompt on phone pause")
        phone.write_text(phone_code, encoding="utf8")

    # Remove the remaining legacy welcome paths on both variants, including
    # language selection. The native onboarding is the only welcome.
    code = code.replace("else if (maUkazatBetu()) ukazBetu() else pokracuj()", "else pokracuj()")
    code = one(code,
        'if (obrazovka == "jazyk" && maUkazatBetu()) ukazBetu() else ukazHlavni()',
        'ukazHlavni()', "remove legacy post-language promotion")
    start = code.index("    private fun ukazBetu() {")
    end = code.index("    /** Tlačítko OK na uvítání", start)
    code = code[:start] + code[end:]
    if "ukazBetu()" in code:
        raise RuntimeError("Legacy PlayCaption welcome is still reachable")
    code = one(code, "    override fun onResume() {",
        "    override fun onResume() {\n        GameLingoPSLoginPIN.resume(this)", "login PIN on main activity")
    code = one(code, "    override fun onPause() {",
        "    override fun onPause() {\n        GameLingoPSLoginPIN.pause(this)", "dismiss PIN prompt on main pause")
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
