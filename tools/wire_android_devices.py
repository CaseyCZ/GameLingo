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

    code=one(code,
        '''                            if (ok) {
                                AutoStart.nastavZapnuto(this, true)''',
        '''                            if (ok) {
                                if (ip != null)
                                    GameLingoPlayStationConsoles.capture(this,
                                        "PlayStation", ip)
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
        transport.write_text(prefix + sender_marker + sender, encoding="utf-8")

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
    profiles.write_text(text,encoding="utf-8")
    print("GameLingo Android/TV: independent device center + secure multi-PS5 saved console switching installed")

if __name__=="__main__":
    main()
