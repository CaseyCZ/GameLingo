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
            LinearLayout.LayoutParams(dp(55), dp(55)))
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
                LinearLayout.LayoutParams(dp(51), dp(51)))
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
         'k1.addView(text("Účet PlayStation", 19f, Color.WHITE, tucne = true))'),
        ('k2.addView(text(t("krok2"), 18f, Color.WHITE))',
         'k2.addView(text("Najít PlayStation", 19f, Color.WHITE, tucne = true))'),
        ('k3.addView(text(t(if (telefon) "krok3Telefon" else "krok3"), 18f, Color.WHITE))',
         'k3.addView(text("Párovací PIN", 19f, Color.WHITE, tucne = true))'),
        ('k1.addView(text(t("xboxKrok1"), 18f, Color.WHITE))',
         'k1.addView(text("Účet Microsoft", 19f, Color.WHITE, tucne = true))'),
        ('k2.addView(text(t("xboxKrok2"), 18f, Color.WHITE))',
         'k2.addView(text("Najít Xbox", 19f, Color.WHITE, tucne = true))'),
        ('k3.addView(text(t("xboxKrok3"), 18f, Color.WHITE))',
         'k3.addView(text("Připojení Xbox", 19f, Color.WHITE, tucne = true))')
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
            // Own minimal GameLingo TV landing screen. Both previous modes
            // remain available: use phone receiver or console Remote Play.
            val titulek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
            titulek.addView(logoRadek, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            titulek.addView(GameLingoHelp.button(this, "tv"),
                LinearLayout.LayoutParams(dp(58), dp(58)))
            obsah.addView(titulek, odsazeni(bottom = 22))
            povoleni?.let { obsah.addView(it, odsazeni(bottom = 12)) }

            obsah.addView(sekce("Konzole"), odsazeni(bottom = 6))
            obsah.addView(konzole, odsazeni(bottom = 15))
            obsah.addView(stavRadek, odsazeni(bottom = 20))

            obsah.addView(sekce("Zobrazování"), odsazeni(bottom = 6))
            dlazdiceTel = dlazdice("▣", "Překlad z telefonu", "",
                Color.parseColor("#172033")) { hratSTelefonem() }
            dlazdiceTv = dlazdice("▤", "Přímo na TV", "",
                Color.parseColor("#172033")) {
                if (jeSlabaTv()) varovaniSlabaTv() else hratTelevizi()
            }
            val volby = vodorovne().apply {
                clipChildren = false; clipToPadding = false
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            volby.addView(dlazdiceTel,
                LinearLayout.LayoutParams(0, dp(132), 1f).apply {
                    rightMargin = dp(18)
                })
            volby.addView(dlazdiceTv,
                LinearLayout.LayoutParams(0, dp(132), 1f))
            obsah.addView(volby, odsazeni(bottom = 9))
            obsah.addView(text("GameLingo TV", 14f,
                Color.parseColor("#94A3B8")).apply { gravity = Gravity.CENTER })
'''
    s=s[:s.index(start)]+new+s[s.index(end, s.index(start)):]
    # TV receiver help is optional. Do not occupy extra width or remove the code.
    s=one(s,
        '        vrsek.addView(logo)\n',
        '''        vrsek.addView(logo)
        vrsek.addView(GameLingoHelp.button(this, "tv"),
            LinearLayout.LayoutParams(dp(55), dp(55)).apply {
                leftMargin = dp(15)
            })
''', "Google TV receiver header")
    s=s.replace(
        '"Otevřete GameLingo na telefonu a zadejte tento šestimístný kód."',
        '"Zadejte kód do telefonu"')
    # Remove a technical-sounding banner subtitle while retaining receiver states.
    s=s.replace('"Překlady přímo přes obraz hry"', '"České titulky"')
    # Shorter explanations on older connection options.
    return s

def polish_phone_activity(s):
    # This second phone screen was originally a black captions viewer.
    # Keep its running subtitle area, mode switching and all four regions,
    # but move instructional text to optional Help.
    s=one(s,'        val horni = LinearLayout(this).apply {',
      '''        lista.addView(GameLingoHelp.button(this, "phone", 20f),
            LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(9)
            })
        val horni = LinearLayout(this).apply {''',"phone caption header")
    s=one(s, '            text = t("telefonTip")',
          '            text = ""', "old help caption")
    s=s.replace('text = "100 % = výchozí čas · méně % = kratší zobrazení. Platí od další stránky."',
                'text = ""')
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
    p.write_text(s,encoding="utf-8")
    h=Path(__file__).with_name("GameLingoHelp.kt")
    (loc/h.name).write_text(h.read_text(encoding="utf-8"),encoding="utf-8")
    if mode=="mobile":
        q=loc/"TelefonActivity.kt"
        q.write_text(polish_phone_activity(q.read_text(encoding="utf-8")),encoding="utf-8")
    print("GameLingo",mode,"compact page layouts and optional help: OK")

if __name__=="__main__":
    main()
