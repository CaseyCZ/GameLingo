#!/usr/bin/env python3
"""CaseyCZ GameLingo native Android presentation pass.

Only UI, strings and non-functional branding are touched. Does NOT alter any
Remote Play, PS5 pairing, Xbox authentication, transport, caption packet or OCR
logic. Applied after pinned upstream + Orange V5 patch + app-id branding.
"""
from pathlib import Path
import sys

ROOT = "app/src/main/java/cz/titulky/tv"
BLUE = "#0284C7"
CYAN = "#38BDF8"
NAVY = "#070B14"
CARD = "#111827"
CARD2 = "#172033"
MUTED = "#94A3B8"

# Use verified CaseyCZ CSS palette. Preserve the semantic green/yellow/orange
# subtitles and connection statuses. Status "red" stays red on actual errors.
palette = {
    "#0B0B0E": NAVY,
    "#111114": NAVY,
    "#101016": NAVY,
    "#1A1A21": CARD,
    "#1A1C26": CARD,
    "#1C1D28": CARD,
    "#161722": CARD,
    "#22232C": CARD2,
    "#26262E": CARD2,
    "#2A2C3E": CARD2,
    "#303038": CARD2,
    "#333338": CARD2,
    "#3A3C50": CARD2,
    "#3A3A44": "#2A3850",
    "#33334A": "#2A3850",
    "#3A3D55": "#2A3850",
    "#555A78": "#2A3850",
    "#A8A8B3": MUTED,
    "#B6B6C2": MUTED,
    "#BBBBBB": MUTED,
    "#E5091A": BLUE,        # original brand red -> GameLingo action blue
    "#F51627": BLUE,
    "#FF1129": BLUE,
}

def replace_one(src, before, after, *, filename):
    count = src.count(before)
    if count != 1:
        raise RuntimeError(f"{filename}: expected one anchor, found {count}: {before[:100]!r}")
    return src.replace(before,after,1)

def theme_text(src):
    # Escape-free plain literal colors only; no protocol or data changes.
    for a,b in palette.items():
        src=src.replace(a,b)
    return src

def write(path, result):
    path.write_text(result,encoding="utf-8")
    print("GameLingo UI:",path)

def main():
    if len(sys.argv)!=2:
        raise SystemExit("Usage: polish_gamelingo.py <upstream_checkout>")
    root=Path(sys.argv[1])
    directory=root/ROOT

    main_file=directory/"MainActivity.kt"
    main_src=main_file.read_text(encoding="utf-8")
    # Avoid advertising a specific TV manufacturer or displaying an abandoned
    # brand name, while keeping functional legacy services intact.
    main_src=main_src.replace("TCL C835","Google TV").replace("TCL Android TV","Google TV")
    main_src=main_src.replace('"PlayCaption"', '"GameLingo"')
    main_src=replace_one(
        main_src,
        "FrameLayout.LayoutParams(dp(if (telefon) 230 else 140), ViewGroup.LayoutParams.WRAP_CONTENT)",
        "FrameLayout.LayoutParams(dp(if (telefon) 78 else 100), ViewGroup.LayoutParams.WRAP_CONTENT)",
        filename=main_file.name,
    )
    main_src=replace_one(
        main_src,
        "        logoRadek.addView(logoBox)\n",
        """        logoRadek.addView(logoBox)
        // Product identity is shared between Android phone and Google TV.
        // The icon still opens diagnostics on OK / long press.
        logoRadek.addView(svisle().apply {
            addView(text("GameLingo", pismo(31f, 28f), Color.WHITE, tucne = true))
            addView(text("Hraj. Rozuměj.", pismo(13f, 15f),
                Color.parseColor("#94A3B8")), odsazeni(top = 3))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(10) })
""",
        filename=main_file.name,
    )
    for key,new_label in {
        'sekce(t("hlTel1"))':'sekce("Herní konzole")',
        'sekce(t("hlTel2"))':'sekce("Jazyk překladu")',
        'sekce(t("hlTel3"))':'sekce("Kam zobrazit titulky")',
        'sekce(t("hlTel4"))':'sekce("Co překládat")',
        'bunka(t("hlJazyk"), jazykKarta)':'bunka("Jazyk", jazykKarta)',
        'bunka(t("hlStav"), stavRadek)':'bunka("Stav překladu", stavRadek)',
        'bunka(t("hlKonzole"), konzole)':'bunka("PlayStation a Xbox", konzole)',
        'bunka(t("coPrekladat"), co)':'bunka("Texty ve hře", co)',
        'sekceMala(t("hratJak"))':'sekceMala("Způsob překladu")',
    }.items():
        if key not in main_src: raise RuntimeError("Missing navigation label "+key)
        main_src=main_src.replace(key,new_label)
    # Specific clear TV mode names rather than brand-dependent translations.
    if 'dlazdice("📱➜📺", t("hratTel"),' not in main_src:
        raise RuntimeError("TV receiver tile missing")
    main_src=main_src.replace('dlazdice("📱➜📺", t("hratTel"),',
                             'dlazdice("📱➜📺", "Překlad z telefonu",')
    main_src=main_src.replace('dlazdice("📺", t("hratTv"),',
                             'dlazdice("📺", "Překlad přímo v TV",')
    main_src=main_src.replace('t("prijemNadpis"), 32f','"GameLingo TV • Připojit telefon", 32f')
    main_src=main_src.replace('k.addView(text(nadpisKroku, 15f, CERVENA, tucne = true)', 
                             'k.addView(text(nadpisKroku, 15f, Color.parseColor("#38BDF8"), tucne = true)')
    main_src=main_src.replace('val CERNA = Color.parseColor("#0B0B0E")','val CERNA = Color.parseColor("#070B14")')
    main_src=theme_text(main_src)
    # Make active TV card focus clearly blue with bright cyan outline.
    main_src=main_src.replace('zaobleni(CERVENA, 26f, Color.WHITE)',
                              'zaobleni(CERVENA, 26f, Color.parseColor("#38BDF8"))')
    main_src=main_src.replace('sekce(s: String) = text(s, 22f, Color.WHITE',
                              'sekce(s: String) = text(s, 22f, Color.WHITE')
    main_src=main_src.replace('setBackgroundColor(Color.BLACK)\n            addView(ImageView',
                              'setBackgroundColor(CERNA)\n            addView(ImageView')
    write(main_file,main_src)

    phone_file=directory/"TelefonActivity.kt"
    phone=phone_file.read_text(encoding="utf-8")
    phone=theme_text(phone)
    phone=replace_one(
        phone,
        "}, LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT))",
        """}, LinearLayout.LayoutParams(dp(55), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(this@TelefonActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@TelefonActivity).apply {
                    text = "GameLingo"
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                })
                addView(TextView(this@TelefonActivity).apply {
                    text = "České titulky"
                    textSize = 11f
                    setTextColor(Color.parseColor("#94A3B8"))
                })
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(7) })""",
        filename=phone_file.name,
    )
    # UI mode selection is now brand blue. Green remains a pairing indicator.
    phone=phone.replace('Color.parseColor(if (vybrano) "#3CFF6E" else "#303038")',
                        'Color.parseColor(if (vybrano) "#0284C7" else "#172033")')
    phone=phone.replace('Color.parseColor(if (vybrano) "#3CFF6E" else "#737380")',
                        'Color.parseColor(if (vybrano) "#38BDF8" else "#2A3850")')
    phone=phone.replace('button.setTextColor(if (vybrano) Color.BLACK else Color.WHITE)',
                        'button.setTextColor(Color.WHITE)')
    phone=phone.replace('setBackgroundColor(Color.BLACK)',
                        'setBackgroundColor(Color.parseColor("#070B14"))')
    write(phone_file,phone)

    # Secondary screens: matching navy surfaces and blue focus/action states.
    # Do not touch transport, security and recognizer service files.
    for name in (
        "VyvojActivity.kt","TvVyber.kt","VyberTitulku.kt","LgDevDialog.kt",
        "LgPruvodce.kt","SamsungPruvodce.kt","Zaznam.kt",
    ):
        p=directory/name
        if p.exists():
            s=theme_text(p.read_text(encoding="utf-8"))
            # User-facing product identity only (not protocol magic).
            s=s.replace('"PlayCaption"','"GameLingo"')
            write(p,s)
    # Source language/translation table contains only display strings.
    translations=directory/"Texty.kt"
    s=translations.read_text(encoding="utf-8").replace("PlayCaption","GameLingo")
    write(translations,s)

    # The Android and TV app both keep PS5 and Xbox capabilities from the
    # pinned upstream. No fake iOS PS5 mode is introduced.
    print("GameLingo shared UI skin applied without changes to PS5/Xbox/TV engine")

if __name__ == "__main__":
    main()
