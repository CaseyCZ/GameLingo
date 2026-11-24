#!/usr/bin/env python3
"""Build-only TCL C835 iPhone receiver test patch on pinned PlayCaption v0.22.

Never modifies the original upstream repository. Does not touch Xbox/RemotePlay.
The test APK installs with a separate application ID. It prefers UDP 41989;
if already occupied it selects UDP 41990–41993 without sharing a socket.
The iPhone and Android clients query the available receiver's port.
"""
from __future__ import annotations
import pathlib
import sys


def once(path: pathlib.Path, before: str, after: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(before) != 1:
        raise RuntimeError(f"{path}: expected exactly one anchor, got {text.count(before)}: {before[:100]!r}")
    path.write_text(text.replace(before, after, 1), encoding="utf-8")
    print(f"Patched {path}")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python patch_tv_iphone.py <pinned-upstream-root>")
    root = pathlib.Path(sys.argv[1]).resolve()
    gradle = root / "app/build.gradle.kts"
    assert 'versionName = "0.22"' in gradle.read_text(encoding="utf-8"), "Unexpected upstream version"

    # Fully independent TV test installation; signed locally with debug key by CI.
    once(gradle,
         '    buildTypes {\n        release { isMinifyEnabled = false }\n    }',
         '    buildTypes {\n'
         '        getByName("debug") {\n'
         '            applicationIdSuffix = ".iphonebridge"\n'
         '            versionNameSuffix = "-iphone-tv-test"\n'
         '        }\n'
         '        release { isMinifyEnabled = false }\n'
         '    }')
    once(root / "app/src/main/res/values/strings.xml",
         '<string name="app_name">PlayCaption</string>',
         '<string name="app_name">PlayCaption iPhone TV TEST</string>')

    # Dedicated pairing manager, separate from original PCS1 Xbox-account transfer.
    pairing_source = pathlib.Path(__file__).with_name("IPhoneTVPairing.kt")
    (root / "app/src/main/java/cz/titulky/tv/IPhoneTVPairing.kt").write_text(
        pairing_source.read_text(encoding="utf-8"), encoding="utf-8")

    # Device-only instrumentation tests; never included in the app APK.
    pairing_test = pathlib.Path(__file__).with_name("IPhoneTVPairingKeystoreTest.kt")
    test_target = root / "app/src/androidTest/java/cz/titulky/tv/IPhoneTVPairingKeystoreTest.kt"
    test_target.parent.mkdir(parents=True, exist_ok=True)
    test_target.write_text(pairing_test.read_text(encoding="utf-8"), encoding="utf-8")

    style_source = pathlib.Path(__file__).with_name("GameLingoTVCaptionStyle.kt")
    (root / "app/src/main/java/cz/titulky/tv/GameLingoTVCaptionStyle.kt").write_text(
        style_source.read_text(encoding="utf-8"), encoding="utf-8")

    networking = root / "app/src/main/java/cz/titulky/tv/Propojeni.kt"
    once(networking,
         '        @Volatile var posledniZprava = 0L\n            private set',
         '        @Volatile var posledniZprava = 0L\n            private set\n'
         '        /** Diagnostic counters; never persist or log game text. */\n'
         '        @Volatile var pocetTitulku = 0L\n            private set\n'
         '        @Volatile var posledniIP = ""\n            private set\n'
         '        @Volatile var posledniCisloTitulku = -1L\n            private set')
    # Pairing receipt/result diagnostics (no PIN or secret logged).
    once(networking,
         '        @Volatile var posledniCisloTitulku = -1L\n            private set',
         '        @Volatile var posledniCisloTitulku = -1L\n            private set\n'
         '        @Volatile var posledniParovaciStav = "žádný požadavek"\n            private set\n'
         '        /** Actual bound UDP receiver port (zero until ready). */\n'
         '        @Volatile var prijimaciPort = 0\n            private set')
    # Prevent multiple apps sharing the same UDP port. Select a backup port
    # if the original PlayCaption is already receiving.
    once(networking, '                        reuseAddress = true\n                        soTimeout = 1000',
         '                        reuseAddress = false\n                        soTimeout = 1000')
    once(networking,
         '                    val s = DatagramSocket(null).apply {\n'
         '                        reuseAddress = false\n'
         '                        soTimeout = 1000\n'
         '                        bind(InetSocketAddress(PORT_TITULKY))\n'
         '                    }',
         '                    IPhoneTVPairing.initialize(c)\n'
         '                    val s = listOf(PORT_TITULKY, 41990, 41991, 41992, 41993)\n'
         '                        .firstNotNullOfOrNull { port ->\n'
         '                            runCatching {\n'
         '                                DatagramSocket(null).apply {\n'
         '                                    reuseAddress = false\n'
         '                                    soTimeout = 1000\n'
         '                                    bind(InetSocketAddress(port))\n'
         '                                }.also { prijimaciPort = port }\n'
         '                            }.getOrNull()\n'
         '                        } ?: throw java.net.BindException("UDP 41989–41993 obsazeny")')
    once(networking, '                        posledniCislo = cislo\n                        val doba =',
         '                        posledniCislo = cislo\n'
         '                        pocetTitulku += 1\n'
         '                        posledniIP = p.address.hostAddress ?: ""\n'
         '                        posledniCisloTitulku = cislo\n'
         '                        val doba =')
    # In the single listening socket, recognize PIN pairing, signed heartbeats
    # and signed subtitles. Legacy unsigned PCT1 is retained temporarily for the
    # user's already-installed IPA 0.2.0, with explicit diagnostics.
    once(networking,
         '                        val casti = String(p.data, 0, p.length, Charsets.UTF_8).split("\\t")\n'
         '                        if (pribeh.zpracuj(p)) {\n',
         '                        var casti = String(p.data, 0, p.length, Charsets.UTF_8).split("\\t")\n'
         '                        if (casti.size == 2 && casti[0] == "GLPORT1" && casti[1] == "QUERY") {\n'
         '                            odpovez(s, p, "GLPORT1\\tPORT\\t$prijimaciPort")\n'
         '                            continue\n'
         '                        }\n'
         '                        if (casti.size >= 2 && casti[0] == "PCPAIR1") {\n'
         '                            if (casti[1] == "PAIR") {\n'
         '                                posledniParovaciStav = "požadavek dorazil na TV"\n'
         '                                posledniZprava = System.currentTimeMillis()\n'
         '                            }\n'
         '                            when (casti[1]) {\n'
         '                                "PAIR" -> {\n'
         '                                    val code = casti.getOrNull(2).orEmpty()\n'
         '                                    val id = casti.getOrNull(3).orEmpty()\n'
         '                                    val secret = IPhoneTVPairing.confirm(c, code, id, casti.getOrNull(4).orEmpty(), p.address.hostAddress.orEmpty())\n'
         '                                    posledniParovaciStav = if (secret != null)\n'
         '                                        "kód přijat; odpověď OK odeslána" else "kód odmítnut nebo vypršel"\n'
         '                                    if (secret != null)\n'
         '                                        odpovez(s, p, "PCPAIR1\\tOK\\t$id\\t$secret")\n'
         '                                    else odpovez(s, p, "PCPAIR1\\tERROR\\tKód je nesprávný nebo vypršel")\n'
         '                                }\n'
         '                                "STYLE" -> {\n'
         '                                    val id = casti.getOrNull(2).orEmpty()\n'
         '                                    val textColor = casti.getOrNull(3).orEmpty()\n'
         '                                    val background = casti.getOrNull(4).orEmpty()\n'
         '                                    val signature = casti.getOrNull(7).orEmpty()\n'
         '                                    if (casti.size == 8 && IPhoneTVPairing.verifyFresh(c, id,\n'
         '                                        "STYLE\\t$id\\t$textColor\\t$background", casti.getOrNull(5), casti.getOrNull(6), signature, "STYLE") &&\n'
         '                                        IPhoneTVPairing.permitsStyle(id) && GameLingoTVCaptionStyle.save(c, textColor, background)) {\n'
         '                                        odpovez(s, p, "PCPAIR1\\tSTYLE_OK")\n'
         '                                    }\n'
         '                                }\n'
         '                                "PING" -> {\n'
         '                                    val id = casti.getOrNull(2).orEmpty()\n'
         '                                    val nonce = casti.getOrNull(3).orEmpty()\n'
         '                                    val sig = casti.getOrNull(6).orEmpty()\n'
         '                                    if (casti.size == 7 && IPhoneTVPairing.verifyFresh(c, id, "PING\\t$id\\t$nonce", casti.getOrNull(4), casti.getOrNull(5), sig, "PING")) {\n'
         '                                        val pong = IPhoneTVPairing.pongSignature(c, id, nonce) ?: continue\n'
         '                                        if (IPhoneTVPairing.permitsStyle(id)) posledniZprava = System.currentTimeMillis()\n'
         '                                        odpovez(s, p, "PCPAIR1\\tPONG\\t$nonce\\t$pong")\n'
         '                                    }\n'
         '                                }\n'
         '                                "TEXT" -> {\n'
         '                                    val id = casti.getOrNull(2).orEmpty()\n'
         '                                    val encoded = casti.getOrNull(3).orEmpty()\n'
         '                                    val sig = casti.getOrNull(6).orEmpty()\n'
         '                                    val lane = IPhoneTVPairing.textLane(encoded) ?: continue\n'
         '                                    if (casti.size == 7 && encoded.length < 11000 &&\n'
         '                                        IPhoneTVPairing.verifyFresh(c, id, "TEXT\\t$id\\t$encoded", casti.getOrNull(4), casti.getOrNull(5), sig, lane)) {\n'
         '                                        val decoded = runCatching {\n'
         '                                            String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)\n'
         '                                        }.getOrNull() ?: continue\n'
         '                                        if (!decoded.startsWith("PCT1\\t")) continue\n'
         '                                        casti = decoded.split("\\t")\n'
         '                                    } else continue\n'
         '                                }\n'
         '                            }\n'
         '                            if (casti.getOrNull(0) == "PCPAIR1") continue\n'
         '                        }\n'
         '                        if (pribeh.zpracuj(p)) {\n')
    # Acknowledges the UDP receiver only. No false "displayed" claim.
    # Official sender layout starts at field 5. Older PlayCaption iOS builds
    # accidentally include a second tab before X, moving those coordinates to 6.
    # Accept both without affecting the official PCT1 text/heartbeat format.
    once(networking,
         '                        val rozvrh = if (casti.size >= 9 && !casti[5].startsWith("B:")) runCatching {\n'
         '                            val uprava = if (casti.getOrNull(9)?.trim() == "1") 1f else 0f\n'
         '                            floatArrayOf(casti[5].toFloat(), casti[6].toFloat(), casti[7].toFloat(), casti[8].toFloat(), uprava)\n'
         '                        }.getOrNull() else null\n',
         '                        // iOS v0.2.0 may have an extra empty field after hint.\n'
         '                        val offset = if (casti.getOrNull(5) == "" &&\n'
         '                            casti.getOrNull(6)?.toFloatOrNull() != null) 6 else 5\n'
         '                        val rozvrh = if (casti.size >= offset + 4 &&\n'
         '                            !casti[offset].startsWith("B:")) runCatching {\n'
         '                            val uprava = if (casti.getOrNull(offset + 4)?.trim() == "1") 1f else 0f\n'
         '                            floatArrayOf(casti[offset].toFloat(), casti[offset + 1].toFloat(),\n'
         '                                casti[offset + 2].toFloat(), casti[offset + 3].toFloat(), uprava)\n'
         '                        }.getOrNull() else null\n')
    once(networking,
         '                        priTitulku(odEscapuj(casti[3]), doba, odEscapuj(casti[4]), rozvrh, barvy)\n',
         '                        priTitulku(odEscapuj(casti[3]), doba, odEscapuj(casti[4]), rozvrh, barvy)\n'
         '                        // Optional iOS confirmation: PCT1 parsed and queued, not proof of overlay.\n'
         '                        odpovez(s, p, "PCA1\\t$cislo\\tRECEIVED")\n')
    once(networking,
         '                }.onFailure { if (bezi) chyba = it.javaClass.simpleName }\n',
         '                }.onFailure {\n'
         '                    if (bezi) {\n'
         '                        chyba = it.javaClass.simpleName + ": " + (it.message ?: "network error")\n'
         '                        android.util.Log.e("PlayCaptionIPhone", "UDP receiver failed", it)\n'
         '                    }\n'
         '                }\n')

    service = root / "app/src/main/java/cz/titulky/tv/PrijemService.kt"
    before = '        /** Běží režim "titulky z telefonu"? */\n'
    after = '''        /** TV-only diagnostics: first packet, source and live port errors. */
        fun prijimaciPort(): Int = instance?.prijimac?.prijimaciPort ?: 0

        fun statistikaPrijmu(): String {
            val sluzba = this.instance ?: return "Příjem TV: služba ještě neběží."
            val p = sluzba.prijimac ?: return "Příjem TV: čekám na spuštění UDP."
            val error = p.chyba
            if (error != null) return "Chyba UDP: $error."
            if (p.prijimaciPort == 0) return "Příjem TV: hledám volný UDP port."
            val age = if (p.posledniZprava > 0L)
                ((System.currentTimeMillis() - p.posledniZprava).coerceAtLeast(0L) / 1000L).toString() + " s"
                else "dosud žádná"
            val overlays = if (OverlayService.pripravena) "připraven" else "nepřipraven"
            val hidden = if (sluzba.skrytyMimoVstup) " / mimo TV vstup" else ""
            return "UDP ${p.prijimaciPort} · zpráva před $age\\n" +
                "Párování: ${p.posledniParovaciStav}\\n" +
                "Přijaté titulky: ${p.pocetTitulku} · z ${p.posledniIP.ifEmpty { "—" }}\\n" +
                "Překryv: $overlays$hidden"
        }

''' + before
    once(service, before, after)
    # In this dedicated receiver, captions must also work over TCL's HDMI input.
    # The upstream foreground-app detection may misclassify HDMI as non-TV.
    once(service,
         '        val vstup = uprava || runCatching { popredi.jeVstupTv() }.getOrDefault(true)\n',
         '        val vstup = true // iPhone-only TV receiver; show captions on HDMI\n')
    once(service,
         '        val vstup = ramecek > 0 || runCatching { popredi.jeVstupTv() }.getOrDefault(true)\n',
         '        val vstup = true // iPhone-only TV receiver; show choices on HDMI\n')

    # Receiver story has the same HDMI policy as ordinary phone subtitles.
    # Preserve the direct-on-TV foreground heuristic.
    once(service,
         '                        val vstup = runCatching { popredi.jeVstupTv() }.getOrDefault(true)\n',
         '                        val vstup = true // explicit receiver mode, same as standard captions\n')
    story_overlay = root / "app/src/main/java/cz/titulky/tv/OverlayService.kt"
    once(story_overlay,
         '            val vstup = runCatching { poprediPribehu.jeVstupTv() }.getOrDefault(true)\n',
         '            val vstup = PrijemService.bezi || runCatching { poprediPribehu.jeVstupTv() }.getOrDefault(true)\n')

    activity = root / "app/src/main/java/cz/titulky/tv/MainActivity.kt"
    # Replace upstream PlayCaption's oversized legacy overlay help with a
    # concise TV-specific guide and the actual installed GameLingo package ID.
    # Overlay permission does not diagnose a UDP pairing timeout when already
    # granted; it is only necessary to draw translated subtitles over HDMI.
    once(activity,
         '.setMessage(t("povoleniNavod").replace("%IP%", ip))',
         '.setMessage(if (GameLingoLanguages.app(this) == "cs") ' +
         '"Titulky přes hru potřebují povolení\\n\\n" + ' +
         '"Nastavení TV → Aplikace → Zvláštní přístup → " + ' +
         '"Zobrazovat přes jiné aplikace → GameLingo → Povolit.\\n\\n" + ' +
         '"Pokud TV tuto volbu skrývá, použij ADB z počítače:\\n" + ' +
         '"adb connect $ip:5555\\n" + ' +
         '"adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow\\n\\n" + ' +
         '"Po změně aplikaci vynuceně ukonči a spusť znovu." ' +
         'else t("povoleniNavod").replace("%IP%", ip)' +
         '.replace("cz.titulky.tv", packageName)' +
         '.replace("PlayCaption", "GameLingo"))')
    text = activity.read_text(encoding="utf-8")
    begin = text.index('        // tři kroky s obrázky\n', text.index('    private fun ukazPrijem() {'))
    end = text.index('        val stav = text("", 24f', begin)
    # Clear instructions: no pairing code is needed for Xbox captions on iPhone.
    slim = '''        sloupec.addView(text("iPhone + Xbox · diagnostický přijímač", 22f, Color.WHITE, tucne = true)
            .apply { gravity = Gravity.CENTER }, odsazeni(top = 14))
        sloupec.addView(text(
            "Na iPhonu otevři PlayCaption iOS a vyber IP této televize.\\n" +
            "Pro titulky z Xboxu není potřeba čtyřmístný párovací kód.", 19f, Color.WHITE
        ).apply { gravity = Gravity.CENTER }, odsazeni(top = 12))

'''
    text = text[:begin] + slim + text[end:]
    activity.write_text(text, encoding="utf-8")

    once(activity,
         '        sloupec.addView(text(t("prijemSam"), 16f, TEXT2).apply { gravity = Gravity.CENTER }, odsazeni(top = 16))\n',
         '        sloupec.addView(text("Přepni na HDMI Xboxu. Titulky z iPhonu zůstanou viditelné.", 16f, TEXT2)\n'
         '            .apply { gravity = Gravity.CENTER }, odsazeni(top = 16))\n')
    once(activity,
         '        val zpet = tlacitko(t("prijemZpetKratce"), 20f)',
         '''        val prijemDiag = text("UDP: čekám na první zprávu", 18f, Color.WHITE)
            .apply { gravity = Gravity.CENTER }
        sloupec.addView(prijemDiag, odsazeni(top = 14, bottom = 10))

        // Self-test bypasses UDP; proves local OverlayService can show captions.
        val testPrekryvu = tlacitko("▶ Test překryvu TV", 21f) {
            OverlayService.posli(this, Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_SET_TEXT)
                .putExtra(OverlayService.EXTRA_TEXT, "TCL C835 · TEST PŘEKRYVU TV")
                .putExtra(OverlayService.EXTRA_DOBA, 8000L)
                .putExtra(OverlayService.EXTRA_VLASTNI,
                    floatArrayOf(0.5f, 0.84f, 0.78f, 1.10f, 1f)))
        }
        sloupec.addView(testPrekryvu, odsazeni(top = 8, bottom = 8))

        val zpet = tlacitko(t("prijemZpetKratce"), 20f)''')
    once(activity, '        zpet.requestFocus()\n\n        var bylPripojen = ',
         '        testPrekryvu.requestFocus()\n\n        var bylPripojen = ')
    once(activity,
         '                stav.text = Texty.spojeni(nast.jazykNeboCs, prijem)\n',
         '                stav.text = Texty.spojeni(nast.jazykNeboCs, prijem)\n'
         '                prijemDiag.text = PrijemService.statistikaPrijmu()\n')
    once(activity,
         '                if (prijem.uroven == UrovenSpojeni.OK && !bylPripojen) moveTaskToBack(true)\n',
         '                // Keep this diagnostic waiting screen visible even after first packet.\n')
    # This TEST has ONE user-facing mode: S mobilem (iPhone).
    # Keep original unmodified modules compiled for compatibility, but bypass
    # all Xbox login, PS5, RemotePlay, language selection and beta menu.
    once(activity,
         '        prevezmiAccountId(intent)\n        ukazUvod()\n',
         '        // This independent APK acts only as an iPhone subtitle receiver.\n'
         '        if (Settings.canDrawOverlays(this)) hratSTelefonem()\n'
         '        else ukazNavodPovoleni()\n')
    once(activity,
         '        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)\n'
         '        if (obrazovka == "hlavni") ukazHlavni()\n',
         '        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)\n'
         '        if (Settings.canDrawOverlays(this) && obrazovka != "prijem") {\n'
         '            hratSTelefonem()\n'
         '        }\n')
    once(activity,
         '            "prijem" -> zpetNaVyber()\n',
         '            "prijem" -> { PrijemService.zastav(this); finish() }\n')
    once(activity,
         '        val zpet = tlacitko(t("prijemZpetKratce"), 20f) { zpetNaVyber() }.apply {\n',
         '        val zpet = tlacitko("Zastavit příjem", 20f) {\n'
         '            PrijemService.zastav(this); finish()\n'
         '        }.apply {\n')
    # Display the actual current address. iOS auto-discovery does not require pairing.
    once(activity,
         '        sloupec.addView(text("iPhone + Xbox · diagnostický přijímač", 22f, Color.WHITE, tucne = true)\n',
         '        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager\n'
         '        val ipTv = cm.activeNetwork?.let { cm.getLinkProperties(it) }\n'
         '            ?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }\n'
         '            ?.address?.hostAddress ?: "Zjisti IP v nastavení TV"\n'
         '        sloupec.addView(text("IP televize: $ipTv", 25f, Color.parseColor("#46D9F6"), tucne = true)\n'
         '            .apply { gravity = Gravity.CENTER }, odsazeni(top = 12))\n'
         '        sloupec.addView(text("PlayCaption iPhone · ORANGE V5 · iOS 0.3.2", 22f, Color.WHITE, tucne = true)\n')

    # Pairing controls on the TV (no Microsoft, Xbox, PS5 credentials).
    once(activity,
         '        val prijemDiag = text("UDP: čekám na první zprávu", 18f, Color.WHITE)\n',
         '''        val pairingStatus = text("iPhone není spárovaný", 22f, Color.WHITE)
            .apply { gravity = Gravity.CENTER }
        sloupec.addView(pairingStatus, odsazeni(top = 14, bottom = 8))
        var shownCode: String? = null
        sloupec.addView(tlacitko("🔗 Spárovat iPhone", 20f) {
            shownCode = IPhoneTVPairing.startCode()
            pairingStatus.text = "Párovací kód: $shownCode (3 minuty)"
        }, odsazeni(top = 6, bottom = 8))
        sloupec.addView(tlacitko("Zrušit spárování", 16f) {
            IPhoneTVPairing.clear(this)
            shownCode = null
            pairingStatus.text = "iPhone není spárovaný"
        }, odsazeni(top = 4, bottom = 8))
        val prijemDiag = text("UDP: čekám na první zprávu", 18f, Color.WHITE)
''')
    once(activity,
         '                prijemDiag.text = PrijemService.statistikaPrijmu()\n',
         '                prijemDiag.text = PrijemService.statistikaPrijmu()\n'
         '                pairingStatus.text = when {\n'
         '                    IPhoneTVPairing.connectedRecently() -> "✔ iPhone spárován a připojen"\n'
         '                    shownCode != null && IPhoneTVPairing.remainingSeconds() > 0 ->\n'
         '                        "Párovací kód: $shownCode · zbývá ${IPhoneTVPairing.remainingSeconds()} s"\n'
         '                    IPhoneTVPairing.paired(this@MainActivity) -> "iPhone spárován · čekám na připojení"\n'
         '                    else -> "iPhone není spárovaný"\n'
         '                }\n'
         '                if (IPhoneTVPairing.remainingSeconds() == 0) shownCode = null\n')
    # PCV1 optional output rectangle: left, top, right, bottom (0..1).
    # Legacy PCV1 still behaves like the original receiver if fields are absent.
    once(networking,
         '        private val priVolbach: ((List<String>, Int, FloatArray, Int) -> Unit)? = null,\n',
         '        private val priVolbach: ((List<String>, Int, FloatArray, Int, FloatArray?) -> Unit)? = null,\n')
    once(networking,
         '                                priVolbach?.invoke(seznam, casti[7].toIntOrNull() ?: -1, oblast, casti[2].toIntOrNull() ?: 0)\n',
         '                                val vystup = if (casti.size >= 13) runCatching {\n'
         '                                    val font = casti.getOrNull(13)?.toFloatOrNull()\n'
         '                                        ?.takeIf { it.isFinite() && it in 0.6f..2.0f } ?: 1f\n'
         '                                    floatArrayOf(casti[9].toFloat(), casti[10].toFloat(),\n'
         '                                        casti[11].toFloat(), casti[12].toFloat(), font)\n'
         '                                }.getOrNull()?.takeIf { r ->\n'
         '                                    r.take(4).all { it.isFinite() && it in 0f..1f } &&\n'
         '                                    r[2] > r[0] + 0.1f && r[3] > r[1] + 0.05f\n'
         '                                } else null\n'
         '                                priVolbach?.invoke(seznam, casti[7].toIntOrNull() ?: -1,\n'
         '                                    oblast, casti[2].toIntOrNull() ?: 0, vystup)\n')
    once(networking,
         '                                        if (!decoded.startsWith("PCT1\\t")) continue\n',
         '                                        if (!decoded.startsWith("PCT1\\t") &&\n'
         '                                            !decoded.startsWith("PCV1\\t")) continue\n')

    once(service,
         '                priVolbach = { volby, vybrana, oblast, ramecek -> ui.post { zobrazVolby(volby, vybrana, oblast, ramecek) } },\n',
         '                priVolbach = { volby, vybrana, oblast, ramecek, vystup ->\n'
         '                    ui.post { zobrazVolby(volby, vybrana, oblast, ramecek, vystup) }\n'
         '                },\n')
    once(service,
         '    private fun zobrazVolby(volby: List<String>, vybrana: Int, oblast: FloatArray, ramecek: Int) {\n',
         '    private fun zobrazVolby(volby: List<String>, vybrana: Int, oblast: FloatArray,\n'
         '                            ramecek: Int, vystup: FloatArray?) {\n')
    once(service,
         '            .putExtra(OverlayService.EXTRA_OBLAST, oblast)\n'
         '            .putExtra(OverlayService.EXTRA_RAMECEK, ramecek > 0)\n',
         '            .putExtra(OverlayService.EXTRA_OBLAST, oblast)\n'
         '            .apply { if (vystup != null)\n'
         '                putExtra(OverlayService.EXTRA_VYSTUP_VOLEB, vystup) }\n'
         '            .putExtra(OverlayService.EXTRA_RAMECEK, ramecek > 0)\n')

    overlay = root / "app/src/main/java/cz/titulky/tv/OverlayService.kt"
    once(overlay,
         '    private fun ukazVolby(volby: Array<String>, vybrana: Int, oblast: FloatArray?, ramecek: Boolean, trvale: Boolean = false) {\n',
         '    private fun ukazVolby(volby: Array<String>, vybrana: Int, oblast: FloatArray?, ramecek: Boolean,\n'
         '                           trvale: Boolean = false, vystup: FloatArray? = null) {\n')
    once(overlay,
         '                intent.getFloatArrayExtra(EXTRA_OBLAST),\n'
         '                intent.getBooleanExtra(EXTRA_RAMECEK, false),\n'
         '                intent.getBooleanExtra(EXTRA_RAMECEK_TRVALE, false))\n',
         '                intent.getFloatArrayExtra(EXTRA_OBLAST),\n'
         '                intent.getBooleanExtra(EXTRA_RAMECEK, false),\n'
         '                intent.getBooleanExtra(EXTRA_RAMECEK_TRVALE, false),\n'
         '                intent.getFloatArrayExtra(EXTRA_VYSTUP_VOLEB))\n')
    # Only our TV build gets the new extra. Do not modify external upstream.
    once(overlay,
         '        const val ACTION_SET_NAPOVEDA = "cz.titulky.tv.SET_NAPOVEDA"\n',
         '        const val EXTRA_VYSTUP_VOLEB = "vystup_voleb"\n'
         '        const val ACTION_SET_NAPOVEDA = "cz.titulky.tv.SET_NAPOVEDA"\n')
    # Explicit output geometry takes priority over automatic "next to OCR"
    # placement. Fallback remains exactly the original behavior.
    once(overlay,
         '        lp.y = (o[1] * h).toInt()\n'
         '        okno("volby") { wm.updateViewLayout(seznam, lp) }\n',
         '        lp.y = (o[1] * h).toInt()\n'
         '        if (vystup != null && vystup.size == 5 &&\n'
         '            vystup.take(4).all { it.isFinite() && it in 0f..1f } &&\n'
         '            vystup[4].isFinite() && vystup[4] in 0.6f..2f &&\n'
         '            vystup[2] > vystup[0] + 0.1f && vystup[3] > vystup[1] + 0.05f) {\n'
         '            val sirkaVystupu = ((vystup[2] - vystup[0]) * w).toInt().coerceAtLeast(80)\n'
         '            lp.gravity = Gravity.TOP or Gravity.START\n'
         '            lp.width = sirkaVystupu\n'
         '            // Fit text within the orange area, not the legacy 42% max width.\n'
         '            val textMax = (sirkaVystupu - (44 * d).toInt()).coerceAtLeast(40)\n'
         '            for (index in 0 until seznam.childCount) {\n'
         '                (seznam.getChildAt(index) as? TextView)?.apply {\n'
         '                    maxWidth = textMax\n'
         '                    textSize = VELIKOST_PISMA * 0.9f * vystup[4]\n'
         '                }\n'
         '            }\n'
         '            // Height is the maximum bounds set by the orange rectangle.\n'
         '            // Keep wrap-to-text style, but never paint a huge empty black box.\n'
         '            val maximalniVyska = ((vystup[3] - vystup[1]) * h).toInt().coerceAtLeast(48)\n'
         '            seznam.measure(\n'
         '                android.view.View.MeasureSpec.makeMeasureSpec(sirkaVystupu, android.view.View.MeasureSpec.EXACTLY),\n'
         '                android.view.View.MeasureSpec.makeMeasureSpec(maximalniVyska, android.view.View.MeasureSpec.AT_MOST))\n'
         '            lp.height = seznam.measuredHeight.coerceIn(1, maximalniVyska)\n'
         '            lp.x = (vystup[0] * w).toInt().coerceIn(0, (w - sirkaVystupu).toInt().coerceAtLeast(0))\n'
         '            lp.y = (vystup[1] * h).toInt().coerceIn(0, (h.toInt() - lp.height).coerceAtLeast(0))\n'
         '        }\n'
         '        okno("volby") { wm.updateViewLayout(seznam, lp) }\n')
    # Style settings arrive in authenticated PCPAIR1 STYLE messages and are
    # applied to both the green dialogue output and orange game text output.
    # Do not alter the yellow OCR input region color or non-GameLingo apps.
    once(overlay,
         '            PixelFormat.OPAQUE\n        )\n        lp.alpha = 1f',
         '            PixelFormat.TRANSLUCENT\n        )\n        lp.alpha = 1f')
    once(overlay,
         '    private val konecUpravy = Runnable { textView?.setBackgroundColor(POZADI) }',
         '    private val konecUpravy = Runnable { textView?.setBackgroundColor(\n'
         '        GameLingoTVCaptionStyle.background(this)) }')
    once(overlay,
         '                setColor(POZADI)\n'
         '                setStroke((3 * d).toInt(), ZELENA, 14 * d, 8 * d)',
         '                setColor(GameLingoTVCaptionStyle.background(this@OverlayService))\n'
         '                setStroke((3 * d).toInt(), ZELENA, 14 * d, 8 * d)')
    once(overlay,
         '        pod.visibility = tv.visibility\n',
         '        // Legacy opaque underlay must not hide a translucent/none style.\n'
         '        pod.visibility = if (GameLingoTVCaptionStyle.isDark(this))\n'
         '            tv.visibility else android.view.View.INVISIBLE\n')
    once(overlay,
         '        textView?.apply {\n'
         '            this.text = text\n',
         '        textView?.apply {\n'
         '            setTextColor(GameLingoTVCaptionStyle.textColor(this@OverlayService))\n'
         '            this.text = text\n')
    once(overlay,
         '        if (volby.isEmpty()) { seznam.visibility = android.view.View.INVISIBLE; return }\n',
         '        // The orange translated game-text overlay uses the same style\n'
         '        // as dialogue captions, independently from the yellow OCR box.\n'
         '        seznam.background = android.graphics.drawable.GradientDrawable().apply {\n'
         '            setColor(GameLingoTVCaptionStyle.background(this@OverlayService))\n'
         '            cornerRadius = 10 * d\n'
         '        }\n'
         '        if (volby.isEmpty()) { seznam.visibility = android.view.View.INVISIBLE; return }\n')
    once(overlay,
         '                } else setTextColor(ZLUTA)\n',
         '                } else setTextColor(GameLingoTVCaptionStyle.textColor(this@OverlayService))\n')

    # GameLingo audit P0.1: authenticate every game-data packet before any renderer.
    # Legacy compatibility is explicit, receiver-only and disabled by default.
    once(networking,
         '                    val buf = ByteArray(8192)\n',
         '                    val buf = ByteArray(16384)\n')
    once(networking,
         '                        if (casti.size >= 2 && casti[0] == "PCPAIR1") {\n',
         '                        var authenticatedGameData = false\n'
         '                        if (casti.size >= 2 && casti[0] == "PCPAIR1") {\n')
    once(networking,
         '                                        if (!decoded.startsWith("PCT1\\t") &&\n'
         '                                            !decoded.startsWith("PCV1\\t")) continue\n'
         '                                        casti = decoded.split("\\t")\n',
         '                                        val inner = decoded.split("\\t")\n'
         '                                        if (inner.firstOrNull() !in listOf("PCT1", "PCV1", "PCO1", "PCQ1", "PCS1")) continue\n'
         '                                        if (inner.firstOrNull() == "PCS1" && inner.getOrNull(1) in listOf("ZADOST", "KOD")) continue\n'
         '                                        casti = inner\n'
         '                                        if (inner.firstOrNull() == "PCQ1") {\n'
         '                                            if (!IPhoneTVPairing.permitsStoryReadiness(id)) continue\n'
         '                                        } else if (!IPhoneTVPairing.claimGameData(id, IPhoneTVPairing.extendsSourceLease(inner))) continue\n'
         '                                        resetSourceIfChanged()\n'
         '                                        authenticatedGameData = true\n'
         '                                        p.setData(decoded.toByteArray(Charsets.UTF_8))\n')
    once(networking,
         '                        if (pribeh.zpracuj(p)) {\n',
         '                        // Account transfer is disabled even in legacy subtitle mode.\n'
         '                        if (casti.firstOrNull() == "PCS1" && casti.getOrNull(1) in listOf("ZADOST", "KOD")) {\n'
         '                            odpovez(s, p, "PCS1\\tCHYBA\\tzakazano")\n'
         '                            continue\n'
         '                        }\n'
         '                        if (!authenticatedGameData && !GameLingoTVPairing.legacyEnabled(c)) continue\n'
         '                        if (!authenticatedGameData && IPhoneTVPairing.hasActiveSource()) continue\n'
         '                        if (pribeh.zpracuj(p)) {\n')

    # Isolate the existing renderer's per-source deduplication and story state.
    once(networking,
         '                    val pribeh = PribehPrijem(priPribehu?.let { prijmi -> { titulek, ack ->\n',
         '                    fun newStoryReceiver() = PribehPrijem(priPribehu?.let { prijmi -> { titulek, ack ->\n')
    once(networking,
         '                    val buf = ByteArray(16384)\n',
         '                    var pribeh = newStoryReceiver()\n'
         '                    var renderedSourceRevision = IPhoneTVPairing.overlayRevision()\n'
         '                    fun resetSourceIfChanged() {\n'
         '                        val revision = IPhoneTVPairing.overlayRevision()\n'
         '                        if (revision == renderedSourceRevision) return\n'
         '                        renderedSourceRevision = revision\n'
         '                        posledniCislo = -1L\n'
         '                        posledniCisloVoleb = -1L\n'
         '                        pribeh = newStoryReceiver()\n'
         '                        android.os.Handler(android.os.Looper.getMainLooper()).post {\n'
         '                            OverlayService.posli(c, android.content.Intent(c, OverlayService::class.java).setAction(OverlayService.ACTION_ZRUS_PRIBEH))\n'
         '                        }\n'
         '                        priTitulku("", 0L, "", null, null)\n'
         '                        priVolbach?.invoke(emptyList(), -1, floatArrayOf(0f, 0f, 1f, 1f), 0, null)\n'
         '                        priOblasti?.invoke(floatArrayOf(0f, 0f, 1f, 1f), false)\n'
         '                    }\n'
         '                    val buf = ByteArray(16384)\n')
    once(networking,
         '                    while (bezi) {\n                        pribeh.odesliOdpovedi(s)\n',
         '                    while (bezi) {\n                        resetSourceIfChanged()\n                        pribeh.odesliOdpovedi(s)\n')

    print("iPhone TV PCV1 output geometry enabled: yellow OCR and orange output independent.")
    print("PlayCaption iPhone-only TV receiver + six-digit device pairing ready.")



if __name__ == "__main__":
    main()
