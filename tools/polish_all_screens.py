#!/usr/bin/env python3
"""Final GameLingo UI pass: compact screens, optional help on Android and TV.

Runs after brand_android, polish_gamelingo and redesign_gamelingo.
Changes activity layouts ONLY. Does not alter PSN/Microsoft auth, Remote Play,
OCR, signed UDP, consent, overlay service, receiver, or security logic.
"""
from pathlib import Path
import re
import sys

ROOT = Path("app/src/main/java/cz/titulky/tv")

def one(s, old, new, area):
    n=s.count(old)
    if n != 1:
        raise RuntimeError(f"{area}: expected unique UI anchor {old[:90]!r}, found {n}")
    return s.replace(old,new,1)

def mobile(s):
    # Every account, Xbox/PS5 pairing and translation setup page reuses
    # nadpisLogo(), so its (?) is contextual to setup, not a verbose paragraph.
    s = one(s,
        '        setPadding(dp(4), dp(8), dp(4), dp(12))\n    }\n\n    private fun tlacitko',
        '''        addView(View(this@MainActivity),
            LinearLayout.LayoutParams(0, 1, 1f))
        addView(GameLingoHelp.button(this@MainActivity, "console"),
            GameLingoHelp.layout(this@MainActivity, dp(55), dp(55)))
        setPadding(dp(4), dp(8), dp(4), dp(12))
    }

    private fun tlacitko''',
        "shared setup header")
    # Main dashboard: label no longer repeated in three separate paragraphs.
    s = one(s,
        '        if (!telefon) {\n            // po najetí na logo se vedle ukáže',
        '''        if (telefon) {
            logoRadek.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            logoRadek.addView(GameLingoHelp.button(this, "home"),
                GameLingoHelp.layout(this, dp(51), dp(51)))
        }
        if (!telefon) {
            // po najetí na logo se vedle ukáže''',
        "home header")
    # Keep account, search, and pairing PIN. Move prerequisite paragraphs
    # into the context help opened from the brand header.
    for old,new in (
        ('sloupec.addView(k0, odsazeni(bottom = 12))',
         '// Setup prerequisites are available under the ? Help button.'),
        ('k1.addView(text(t(if (telefon) "krok1Telefon" else "krok1"), 18f, Color.WHITE))',
         'k1.addView(text(GameLingoLanguages.ui(this, "Účet PlayStation"), 19f, Color.WHITE, tucne = true))'),
        ('k2.addView(text(t("krok2"), 18f, Color.WHITE))',
         'k2.addView(text(GameLingoLanguages.ui(this, "Najít PlayStation"), 19f, Color.WHITE, tucne = true))'),
        ('k3.addView(text(t(if (telefon) "krok3Telefon" else "krok3"), 18f, Color.WHITE))',
         'k3.addView(text(GameLingoLanguages.ui(this, "Párovací PIN"), 19f, Color.WHITE, tucne = true))'),
        ('k1.addView(text(t("xboxKrok1"), 18f, Color.WHITE))',
         'k1.addView(text(GameLingoLanguages.ui(this, "Účet Microsoft"), 19f, Color.WHITE, tucne = true))'),
        ('k2.addView(text(t("xboxKrok2"), 18f, Color.WHITE))',
         'k2.addView(text(GameLingoLanguages.ui(this, "Najít Xbox"), 19f, Color.WHITE, tucne = true))'),
        ('k3.addView(text(t("xboxKrok3"), 18f, Color.WHITE))',
         'k3.addView(text(GameLingoLanguages.ui(this, "Připojení Xbox"), 19f, Color.WHITE, tucne = true))')
    ):
        if old == 'sloupec.addView(k0, odsazeni(bottom = 12))':
            n=s.count(old)
            if n!=2: raise RuntimeError("PSN and Xbox precondition cards expected twice, found "+str(n))
            s=s.replace(old,new)
        else:
            s=one(s,old,new,"Android setup card")
    # Existing Android PSN/Xbox buttons and services must remain unchanged.
    return s

def tv(s):
    start = '        } else {\n            // televize: mřížka'
    end = '        }\n        nastavObsah(obsah, sirka = ViewGroup.LayoutParams.MATCH_PARENT)'
    if s.count(start)!=1 or end not in s[s.index(start):]:
        raise RuntimeError("Google TV landing screen UI anchors missing")
    new = '''        } else {
            // Original GameLingo ten-foot UI, never a recoloured upstream menu.
            obsah.background = GameLingoVisualKit.pageBackground(this)
            val hlava = vodorovne().apply {
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
            }
            hlava.addView(GameLingoTVUi.brand(this),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            hlava.addView(GameLingoTVUi.action(this, "⚙  " + GameLingoLanguages.ui(this, "Nastavení")) {
                GameLingoTVUi.showSettings(this) { ukazHlavni() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(14)
            })
            hlava.addView(GameLingoHelp.button(this, "home"),
                GameLingoHelp.layout(this, dp(48), dp(48)))
            obsah.addView(hlava, odsazeni(bottom = 12))
            povoleni?.let { obsah.addView(it, odsazeni(bottom = 13)) }

            obsah.addView(GameLingoTVUi.title(this,
                GameLingoLanguages.ui(this, "Hraj. Rozuměj."), 28f), odsazeni(bottom = 4))
            obsah.addView(GameLingoTVUi.title(this,
                GameLingoLanguages.ui(this, "Vyber způsob překladu nebo spravuj svá zařízení."),
                16f, muted = true), odsazeni(bottom = 12))

            obsah.addView(GameLingoTVUi.title(this, GameLingoLanguages.ui(this, "ZPŮSOB PŘEKLADU"), 16f).apply {
                setTextColor(Color.parseColor("#02D6FF"))
            }, odsazeni(bottom = 10))
            dlazdiceTel = GameLingoTVUi.tile(
                this, "▣", GameLingoLanguages.ui(this, "Překlad z telefonu"),
                if (PrijemService.bezi) GameLingoLanguages.ui(this, "Příjem běží · otevřít párování a stav") else GameLingoLanguages.ui(this, "Spustit příjem a párování telefonu.")
            ) {
                if (!android.provider.Settings.canDrawOverlays(this)) pozadejOPovoleni()
                else if (PrijemService.bezi) ukazPrijem() else hratSTelefonem()
            }
            dlazdiceTv = GameLingoTVUi.tile(
                this, "▤", GameLingoLanguages.ui(this, "Přímo na TV"),
                GameLingoLanguages.ui(this, "Xbox, PlayStation nebo aplikace přímo na televizoru.")
            ) {
                if (!android.provider.Settings.canDrawOverlays(this)) pozadejOPovoleni()
                else if (jeSlabaTv()) varovaniSlabaTv() else hratTelevizi()
            }
            dlazdiceTel.id = View.generateViewId()
            dlazdiceTv.id = View.generateViewId()
            dlazdiceTel.nextFocusRightId = dlazdiceTv.id
            dlazdiceTv.nextFocusLeftId = dlazdiceTel.id
            val volby = vodorovne().apply {
                clipChildren = false
                clipToPadding = false
                setPadding(dp(8), dp(7), dp(8), dp(8))
            }
            volby.addView(dlazdiceTel,
                LinearLayout.LayoutParams(0, dp(130), 1f).apply {
                rightMargin = dp(20)
            })
            volby.addView(dlazdiceTv,
                LinearLayout.LayoutParams(0, dp(130), 1f))
            obsah.addView(volby, odsazeni(bottom = 5))
            // The device centre is inserted here by wire_android_devices.py.

            // Only real saved devices, never setup placeholders under "Moje konzole".
            val savedPS = GameLingoPlayStationConsoles.list(this)
            val devicePrefs = getSharedPreferences("gamelingo_devices", MODE_PRIVATE)
            val hiddenXboxIds = devicePrefs.getStringSet("hiddenXboxIds", emptySet()).orEmpty()
            val savedXbox = XboxUcet.konzole(this)?.takeIf {
                XboxUcet.prihlasen(this) && it.first !in hiddenXboxIds
            }
            val hasLegacyPS = savedPS.isEmpty() && udaje.sparovano
            val consoleCards = mutableListOf<View>()
            for (ps in savedPS) {
                consoleCards.add(GameLingoTVUi.tile(this,"PS",GameLingoDeviceCenter.consoleName(this,"ps",ps.ip,ps.name),ps.type+" · "+GameLingoLanguages.ui(this, "Uložená konzole")) {
                    vyberPrimouPS()
                })
            }
            if (hasLegacyPS) consoleCards.add(GameLingoTVUi.tile(this,"PS","PlayStation",GameLingoLanguages.ui(this, "Uložená konzole")) {
                vyberPrimouPS()
            })
            savedXbox?.let { xbox ->
                consoleCards.add(GameLingoTVUi.tile(this,"ⓧ",GameLingoDeviceCenter.consoleName(this,"xbox",xbox.first,xbox.second),"Xbox · "+GameLingoLanguages.ui(this, "Uložená konzole")) {
                    vyberPrimyXbox()
                })
            }
            if (consoleCards.isNotEmpty()) {
                obsah.addView(GameLingoTVUi.title(this,GameLingoLanguages.ui(this, "MOJE KONZOLE"),16f).apply {
                    setTextColor(Color.parseColor("#02D6FF"))
                },odsazeni(top=10,bottom=7))
                consoleCards.chunked(2).forEach { group ->
                    val row=vodorovne().apply {
                        clipChildren=false;clipToPadding=false
                        setPadding(dp(8),dp(5),dp(8),dp(7))
                    }
                    group.forEachIndexed { i, card ->
                        row.addView(card,LinearLayout.LayoutParams(0,dp(123),1f).apply {
                            if(i<group.lastIndex) rightMargin=dp(20)
                        })
                    }
                    obsah.addView(row,odsazeni(bottom=6))
                }
            } else {
                obsah.addView(GameLingoTVUi.title(this,GameLingoLanguages.ui(this, "Zatím nemáš přidanou žádnou konzoli."),17f,muted=true),
                    odsazeni(top=9,bottom=12))
            }

            // Genuine state, never imply that a saved device is connected.
            val statusy = vodorovne().apply {
                setPadding(dp(7), dp(5), dp(7), dp(5))
            }
            statusy.addView(GameLingoTVUi.statusPill(this, GameLingoLanguages.ui(this, "Konzole"),
                if (RemotePlayService.aktivni) GameLingoLanguages.ui(this, "Remote Play běží")
                else if (consoleCards.isNotEmpty()) GameLingoLanguages.ui(this, "Uložená · nepřipojena") else GameLingoLanguages.ui(this, "Nepřidána"),
                RemotePlayService.aktivni),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(11) })
            statusy.addView(GameLingoTVUi.statusPill(this, GameLingoLanguages.ui(this, "Překryv titulků"),
                if (android.provider.Settings.canDrawOverlays(this)) GameLingoLanguages.ui(this, "Povolen")
                else GameLingoLanguages.ui(this, "Vyžaduje povolení"),
                android.provider.Settings.canDrawOverlays(this)),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            obsah.addView(statusy, odsazeni(bottom = 11))
            obsah.addView(GameLingoTVUi.title(this,
                GameLingoLanguages.ui(this, "Ovládání: šipky pro výběr • OK potvrdit • Zpět pro návrat"),
                14f, muted = true).apply { gravity = Gravity.CENTER })

            volby.post {
                if (!dlazdiceTel.hasFocus() && !dlazdiceTv.hasFocus())
                    dlazdiceTel.requestFocus()
            }

'''
    s=s[:s.index(start)]+new+s[s.index(end, s.index(start)):]
    # Receiver already uses the shared fixed Back/title/Help header.
    s=s.replace(
        '"Otevřete GameLingo na telefonu a zadejte tento šestimístný kód."',
        '"Zadejte kód do telefonu"')
    # Remove a technical-sounding banner subtitle while retaining receiver states.
    s=s.replace('"Překlady přímo přes obraz hry"', '"České titulky"')
    # Console, PSN and Xbox setup screens share the new GameLingo title +
    # optional Help, instead of the old giant PlayCaption image.
    old = '''    private fun nadpisLogo() = ImageView(this).apply {
        setImageResource(R.drawable.logo)
        adjustViewBounds = true
        layoutParams = LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    }
'''
    replacement = '''    private fun nadpisLogo(): View = vodorovne().apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(73), dp(73)))
        addView(text("GameLingo", 30f, Color.WHITE, tucne = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(12) })
        addView(GameLingoHelp.button(this@MainActivity, "console"),
            GameLingoHelp.layout(this@MainActivity, dp(56), dp(56)))
        setPadding(dp(4), dp(8), dp(4), dp(10))
    }
'''
    s=one(s,old,replacement,"Google TV console screen header")
    # Keep Sony/Microsoft account, console discovery and PIN steps visible.
    # Hide only duplicated prerequisite paragraphs, available in Help.
    prerequisite='sloupec.addView(k0, odsazeni(bottom = 12))'
    if s.count(prerequisite)!=2:
        raise RuntimeError("Expected both TV console setup introductions")
    s=s.replace(prerequisite, '// See ? for setup prerequisites')
    for before,after in (
        ('k1.addView(text(t(if (telefon) "krok1Telefon" else "krok1"), 18f, Color.WHITE))',
         'k1.addView(text(GameLingoLanguages.ui(this, "Účet PlayStation"), 19f, Color.WHITE, tucne = true))'),
        ('k2.addView(text(t("krok2"), 18f, Color.WHITE))',
         'k2.addView(text(GameLingoLanguages.ui(this, "Najít PlayStation"), 19f, Color.WHITE, tucne = true))'),
        ('k3.addView(text(t(if (telefon) "krok3Telefon" else "krok3"), 18f, Color.WHITE))',
         'k3.addView(text(GameLingoLanguages.ui(this, "Párovací PIN"), 19f, Color.WHITE, tucne = true))'),
        ('k1.addView(text(t("xboxKrok1"), 18f, Color.WHITE))',
         'k1.addView(text(GameLingoLanguages.ui(this, "Účet Microsoft"), 19f, Color.WHITE, tucne = true))'),
        ('k2.addView(text(t("xboxKrok2"), 18f, Color.WHITE))',
         'k2.addView(text(GameLingoLanguages.ui(this, "Najít Xbox"), 19f, Color.WHITE, tucne = true))'),
        ('k3.addView(text(t("xboxKrok3"), 18f, Color.WHITE))',
         'k3.addView(text(GameLingoLanguages.ui(this, "Připojit Xbox"), 19f, Color.WHITE, tucne = true))')
    ):
        s=one(s,before,after,"Google TV console step")
    return s

def polish_phone_activity(s):
    # This second phone screen was originally a black captions viewer.
    # Keep its running subtitle area, mode switching and all four regions,
    # but move instructional text to optional Help.
    s=one(s,'        val horni = LinearLayout(this).apply {',
      '''        lista.addView(GameLingoHelp.button(this, "phone", 20f),
            GameLingoHelp.layout(this, dp(48), dp(48)).apply {
                leftMargin = dp(9)
            })
        val horni = LinearLayout(this).apply {''',"phone caption header")
    s=one(s, '            text = t("telefonTip")',
          '            text = ""', "old help caption")
    s=s.replace('text = "100 % = výchozí čas · méně % = kratší zobrazení. Platí od další stránky."',
                'text = ""')
    # The actual Android OCR editor follows the iPhone's four-zone palette.
    # Do not replace its drag coordinates, slider values, locks or UDP output.
    s=one(s,
        '                setImageResource(R.drawable.logo)\n                adjustViewBounds = true',
        '                setImageResource(R.drawable.gamelingo_ikona)\n                adjustViewBounds = true',
        "GameLingo live OCR header emblem")
    s=one(s,
        '            setBackgroundColor(Color.parseColor("#070B14"))\n'
        '            addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))',
        '            background = GameLingoVisualKit.pageBackground(this@TelefonActivity)\n'
        '            addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))',
        "GameLingo native OCR toolbar")
    s=one(s,
        '            text = "⚙ " + t("tvNastaveni") + " · " + Propojeni.Vysilac.cilJmeno',
        '            text = "▣  Oblasti na obrazovce · " + Propojeni.Vysilac.cilJmeno',
        "GameLingo OCR section heading")
    s=one(s,
        '            background = zaobleni(Color.parseColor("#172033"))\n'
        '            setPadding(dp(12), dp(10), dp(12), dp(10))\n'
        '            setOnClickListener { spravaTelevize() }',
        '            background = GameLingoVisualKit.card(this@TelefonActivity)\n'
        '            setPadding(dp(12), dp(10), dp(12), dp(10))\n'
        '            setOnClickListener { spravaTelevize() }',
        "GameLingo OCR header card")
    s=one(s,
        '                setColor(Color.parseColor("#111827"))',
        '                setColor(Color.parseColor("#111A33"))',
        "GameLingo OCR preview")
    s=one(s,
        '                setStroke(dp(2), Color.parseColor("#2A3850"))',
        '                setStroke(dp(2), Color.parseColor("#A329FA"))',
        "GameLingo OCR preview border")
    s=one(s,
        '                    button.setTextColor(Color.WHITE)\n'
        '                    button.background = zaobleni(Color.parseColor(if (vybrano) "#0284C7" else "#172033")).apply {\n'
        '                        setStroke(dp(2), Color.parseColor(if (vybrano) "#38BDF8" else "#2A3850"))\n'
        '                    }',
        '                    button.setTextColor(Color.WHITE)\n'
        '                    button.background = GameLingoVisualKit.card(this@TelefonActivity, selected = vybrano)',
        "GameLingo translation modes")
    # Match iPhone: four meaningfully colored, individually configured regions.
    legend = '''        val glRegions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(13), 0, dp(12))
        }
        listOf(
            "Modrá" to Color.parseColor("#3557FF"),
            "Zelená" to Color.parseColor("#3CFF6E"),
            "Žlutá" to Color.parseColor("#FFD54A"),
            "Oranžová" to Color.parseColor("#FF9E34")
        ).forEach { (name, color) ->
            glRegions.addView(TextView(this).apply {
                text = name
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(color)
                setTypeface(null, Typeface.BOLD)
                minHeight = dp(43)
                background = GameLingoVisualKit.card(this@TelefonActivity)
                contentDescription = "Oblast " + name
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { rightMargin = dp(4) })
        }
        obal.addView(glRegions)
'''
    s=one(s,
        '        obal.addView(LgDevModeView(this) {',
        legend + '        obal.addView(LgDevModeView(this) {',
        "GameLingo four OCR zone legend")
    # Dynamic errors and actual status indicators remain in napoveda.
    return s

def main():
    if len(sys.argv)!=3 or sys.argv[1] not in ("mobile","tv"):
        raise SystemExit("Usage: polish_all_screens.py mobile|tv <checkout>")
    mode,root=sys.argv[1],Path(sys.argv[2])
    loc=root/ROOT
    p=loc/"MainActivity.kt"
    s=p.read_text(encoding="utf-8")
    s=mobile(s) if mode=="mobile" else tv(s)
    # Add the same platform panels as GameLingoPlayStationSetup and GameLingoXboxScreen.
    s=one(s, '        sloupec.addView(text(t("pripojitKonzoli"), 28f, Color.WHITE, tucne = true), odsazeni(bottom = 16))',
        '''        sloupec.addView(text("PlayStation", 28f, Color.WHITE, tucne = true), odsazeni(bottom = 8))
        sloupec.addView(GameLingoVisualKit.platformPanel(this,"PlayStation","Moje konzole","PlayStation 4 / 5 Remote Play"),odsazeni(bottom = 16))''',
        "SwiftUI PlayStation platform panel")
    s=one(s, '        sloupec.addView(text(t("xboxNadpis"), 28f, Color.WHITE, tucne = true), odsazeni(bottom = 16))',
        '''        sloupec.addView(text("Můj Xbox", 28f, Color.WHITE, tucne = true), odsazeni(bottom = 8))
        sloupec.addView(GameLingoVisualKit.platformPanel(this,"Xbox","Xbox Remote Play","Microsoft účet a vlastní konzole"),odsazeni(bottom = 16))''',
        "SwiftUI Xbox platform panel")
    p.write_text(s,encoding="utf-8")
    h=Path(__file__).with_name("GameLingoHelp.kt")
    (loc/h.name).write_text(h.read_text(encoding="utf-8"),encoding="utf-8")
    if mode=="mobile":
        q=loc/"TelefonActivity.kt"
        q.write_text(polish_phone_activity(q.read_text(encoding="utf-8")),encoding="utf-8")
    print("GameLingo",mode,"compact page layouts and optional help: OK")

if __name__=="__main__":
    main()
