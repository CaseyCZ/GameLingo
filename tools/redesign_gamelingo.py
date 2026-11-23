#!/usr/bin/env python3
"""Build the reference-oriented GameLingo Android/Google TV UI.

Apply AFTER brand_android.py, polish_gamelingo.py and the Orange V5 receiver
patch. All RemotePlayService/Propojeni/PrijemService/OCR are left unchanged.
"""
from pathlib import Path
import sys

SRC = "app/src/main/java/cz/titulky/tv"

def splice_once(text: str, first: str, last: str, replacement: str) -> str:
    if text.count(first) != 1 or text.count(last) != 1:
        raise RuntimeError("UI source anchors not unique: " + first[:70] + ", " + last[:70])
    return text[:text.index(first)] + replacement + text[text.index(last):]

def redesign_mobile(s: str) -> str:
    start = '        if (telefon) {\n            obsah.addView(logoRadek, odsazeni(bottom = 14))'
    end = '        } else {\n            // televize:'
    replacement = r'''        if (telefon) {
            // Own GameLingo phone dashboard, preserving actual PlayStation
            // pairing, Xbox authentication, translation & profile operations.
            obsah.addView(logoRadek, odsazeni(bottom = 20))
            obsah.addView(sekce("Herní konzole"))
            obsah.addView(konzole, odsazeni(bottom = 13))
            obsah.addView(sekce("Kam zobrazit titulky"))
            obsah.addView(kartaVystupuTv(), odsazeni(bottom = 14))

            val spustit = tlacitko("▶   Spustit překlad", 24f) {
                if (nejakaKonzole) prepniPreklad()
                else android.app.AlertDialog.Builder(this)
                    .setTitle("Kterou konzoli používáš?")
                    .setItems(arrayOf("PlayStation 5 / 4", "Xbox Series / One")) { _, vyber ->
                        if (vyber == 0) ukazPripojeni() else ukazPripojeniXbox()
                    }.show()
            }.apply {
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minHeight = dp(72)
                setPadding(dp(20), dp(17), dp(20), dp(17))
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#7848E8"),
                        Color.parseColor("#465DFF"), Color.parseColor("#03BFF6"))
                ).apply { cornerRadius = dp(18).toFloat() }
            }
            obsah.addView(spustit, odsazeni(top = 8, bottom = 19))

            fun akce(ikona: String, nazev: String, dej: () -> Unit): TextView =
                tlacitko("$ikona\n$nazev", 18f, dej).apply {
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    minHeight = dp(108)
                    setPadding(dp(10), dp(15), dp(10), dp(15))
                    background = vyber(Color.parseColor("#111827"),
                        Color.parseColor("#2A3850"), radius = 19f)
                }
            val prvni = vodorovne()
            prvni.addView(akce("▣", "Oblasti na obrazovce") {
                if (nejakaKonzole)
                    startActivity(Intent(this, TelefonActivity::class.java))
                else android.app.AlertDialog.Builder(this)
                    .setMessage("Pro nastavení oblastí nejdřív připoj Xbox nebo PlayStation.")
                    .setPositiveButton("Xbox") { _, _ -> ukazPripojeniXbox() }
                    .setNeutralButton("PlayStation") { _, _ -> ukazPripojeni() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(10)
            })
            prvni.addView(akce("♧", "Profily her") {
                GameLingoProfiles.show(this, nast) { ukazHlavni() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            obsah.addView(prvni, odsazeni(bottom = 10))

            val druhy = vodorovne()
            druhy.addView(akce("☷", "Nastavení překladu") {
                GameLingoSettings.show(this, nast,
                    onAreas = {
                        if (nejakaKonzole)
                            startActivity(Intent(this, TelefonActivity::class.java))
                        else ukazPripojeni()
                    },
                    onLanguage = { ukazJazyk() })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(10)
            })
            druhy.addView(akce("▭", "Zařízení") {
                vyberVystupTv()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            obsah.addView(druhy, odsazeni(bottom = 13))

            obsah.addView(stavRadek, odsazeni(bottom = 15))
            obsah.addView(text("GameLingo · CaseyCZ · Android",
                13f, Color.parseColor("#94A3B8")).apply {
                gravity = Gravity.CENTER
            }, odsazeni(bottom = 12))
            // Bottom navigation uses the same actions as the iPhone dashboard.
            val navigace = vodorovne().apply {
                gravity = Gravity.CENTER
                background = zaobleni(Color.parseColor("#111827"), 17f)
                setPadding(dp(7), dp(5), dp(7), dp(5))
            }
            listOf(
                Triple("⌂\nDomů", 0, { ukazHlavni() }),
                Triple("▣\nOblasti", 1, {
                    if (nejakaKonzole) startActivity(Intent(this, TelefonActivity::class.java))
                    else ukazPripojeni()
                }),
                Triple("♧\nProfily", 2, {
                    GameLingoProfiles.show(this, nast) { ukazHlavni() }
                }),
                Triple("▭\nTV", 3, { vyberVystupTv() })
            ).forEach { (label, _, action) ->
                navigace.addView(tlacitko(label, 15f, action).apply {
                    gravity = Gravity.CENTER
                    background = zaobleni(Color.parseColor("#172033"), 10f)
                    setPadding(dp(5), dp(11), dp(5), dp(11))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            obsah.addView(navigace, odsazeni(top = 12))
'''
    s = splice_once(s,start,end,replacement)
    # The shared title replaces PlayCaption's logo on PSN, Xbox, language and
    # setup screens. Do not touch the brand's transport strings.
    old = '''    private fun nadpisLogo() = ImageView(this).apply {
        setImageResource(R.drawable.logo)
        adjustViewBounds = true
        layoutParams = LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    }
'''
    new = '''    private fun nadpisLogo(): View = vodorovne().apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(72), dp(72)))
        addView(svisle().apply {
            addView(text("GameLingo", 30f, Color.WHITE, tucne = true))
            addView(text("Hraj. Rozuměj.", 15f,
                Color.parseColor("#94A3B8")))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(13) })
        setPadding(dp(4), dp(8), dp(4), dp(12))
    }
'''
    if s.count(old)!=1:raise RuntimeError("missing Android brand top-bar method")
    s=s.replace(old,new,1)
    return s

def redesign_tv(s: str) -> str:
    start = '    private fun ukazPrijem() {\n'
    end = '    private val obnova: Runnable = object : Runnable {\n'
    new = r'''    private fun ukazPrijem() {
        obrazovka = "prijem"
        val pozadi = Color.parseColor("#070B14")
        val karta = Color.parseColor("#111827")
        val obrys = Color.parseColor("#33476B")
        val akcent = Color.parseColor("#38BDF8")
        val fialova = Color.parseColor("#A15CFF")
        val zelena = Color.parseColor("#35E57D")
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val ipTv = cm.activeNetwork?.let { cm.getLinkProperties(it) }
            ?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }
            ?.address?.hostAddress ?: "Adresa se zjišťuje"

        fun modraKarta(): LinearLayout = svisle().apply {
            background = zaobleni(karta, 24f, obrys)
            setPadding(dp(24), dp(19), dp(24), dp(19))
        }

        val vrsek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        vrsek.addView(ImageView(this).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(100), dp(100)).apply { rightMargin = dp(12) })
        val logo = svisle()
        logo.addView(text("GameLingo", 36f, Color.WHITE, tucne = true))
        logo.addView(text("TV", 16f, fialova, tucne = true))
        vrsek.addView(logo)
        vrsek.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        vrsek.addView(text("Google TV / Android TV", 17f,
            Color.parseColor("#94A3B8")))

        val levá = modraKarta().apply { gravity = Gravity.CENTER }
        levá.addView(ImageView(this).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(200), dp(200)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        levá.addView(text("Překlady přímo přes obraz hry", 23f,
            Color.WHITE, tucne = true).apply {
            gravity = Gravity.CENTER
        }, odsazeni(top = 14))
        levá.addView(text("Xbox · PlayStation · Google TV", 16f,
            Color.parseColor("#94A3B8")).apply {
            gravity = Gravity.CENTER
        }, odsazeni(top = 10))

        val pravá = modraKarta()
        pravá.addView(text("Připojte svůj telefon", 32f,
            Color.WHITE, tucne = true))
        pravá.addView(text(
            "Otevřete GameLingo na telefonu a zadejte tento šestimístný kód.",
            19f, Color.parseColor("#CBD5E1")), odsazeni(top = 9, bottom = 13))

        var shownCode: String? = if (!IPhoneTVPairing.paired(this))
            IPhoneTVPairing.startCode() else null
        val kodRadek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        val policka = (0 until 6).map {
            text("•", 44f, Color.WHITE, tucne = true).apply {
                gravity = Gravity.CENTER
                background = zaobleni(Color.parseColor("#10182C"), 14f, fialova)
                setPadding(dp(7), dp(9), dp(7), dp(9))
                minWidth = dp(61)
                kodRadek.addView(this,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { rightMargin = dp(if (it == 5) 0 else 8) })
            }
        }
        pravá.addView(kodRadek, odsazeni(top = 10, bottom = 12))

        val stavParovani = text("", 18f, Color.parseColor("#94A3B8"))
        pravá.addView(stavParovani, odsazeni(top = 12))
        pravá.addView(text("IP televizoru: $ipTv", 17f,
            akcent), odsazeni(top = 9))
        val akce = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        akce.addView(tlacitko("Nový párovací kód", 19f) {
            shownCode = IPhoneTVPairing.startCode()
        }.apply {
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#7548E8"), Color.parseColor("#13BBF6"))
            ).apply { cornerRadius = dp(13).toFloat() }
            setPadding(dp(17), dp(13), dp(17), dp(13))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { rightMargin = dp(8) })
        akce.addView(tlacitko("Zrušit spárování", 16f) {
            IPhoneTVPairing.clear(this)
            shownCode = IPhoneTVPairing.startCode()
        }.apply {
            gravity = Gravity.CENTER
            background = zaobleni(Color.parseColor("#172033"), 13f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pravá.addView(akce, odsazeni(top = 17))

        val hlavni = vodorovne().apply {
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        hlavni.addView(levá, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 0.80f).apply {
            rightMargin = dp(25)
        })
        hlavni.addView(pravá, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1.45f))

        val prijemDiag = text("", 14f, Color.parseColor("#94A3B8")).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }
        fun stavItem(ikonka: String, popisek: String, vysledek: String): LinearLayout =
            svisle().apply {
                gravity = Gravity.CENTER
                addView(text(ikonka, 28f, akcent).apply {
                    gravity = Gravity.CENTER
                })
                addView(text(popisek, 19f, Color.WHITE, tucne = true).apply {
                    gravity = Gravity.CENTER
                }, odsazeni(top = 3))
                addView(text(vysledek, 16f, zelena).apply {
                    gravity = Gravity.CENTER
                }, odsazeni(top = 3))
            }
        val statusy = vodorovne().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = zaobleni(Color.parseColor("#101C32"), 20f, obrys)
            setPadding(dp(20), dp(15), dp(20), dp(15))
        }
        statusy.addView(stavItem("⌁", "Síť", "Připojeno"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusy.addView(stavItem("▣", "Přijímač", "Aktivní"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val stavTitulku = text("Čeká na telefon", 16f,
            Color.parseColor("#94A3B8")).apply {
            gravity = Gravity.CENTER
        }
        statusy.addView(svisle().apply {
            gravity = Gravity.CENTER
            addView(text("▤", 28f, akcent).apply {
                gravity = Gravity.CENTER
            })
            addView(text("Titulky", 19f, Color.WHITE, tucne = true).apply {
                gravity = Gravity.CENTER
            }, odsazeni(top = 3))
            addView(stavTitulku, odsazeni(top = 3))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val test = tlacitko("▶  Vyzkoušet titulky", 17f) {
            OverlayService.posli(this, Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_SET_TEXT)
                .putExtra(OverlayService.EXTRA_TEXT, "GameLingo · České titulky fungují")
                .putExtra(OverlayService.EXTRA_DOBA, 8000L)
                .putExtra(OverlayService.EXTRA_VLASTNI,
                    floatArrayOf(0.5f, 0.84f, 0.78f, 1.10f, 1f)))
        }.apply {
            gravity = Gravity.CENTER
            background = vyber(Color.parseColor("#172033"), obrys, radius = 13f)
            setPadding(dp(17), dp(12), dp(17), dp(12))
        }
        val zastav = tlacitko("Zastavit přijímač", 17f) {
            PrijemService.zastav(this)
            finish()
        }.apply {
            gravity = Gravity.CENTER
            background = vyber(Color.parseColor("#172033"), obrys, radius = 13f)
            setPadding(dp(17), dp(12), dp(17), dp(12))
        }
        val paticka = vodorovne().apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        paticka.addView(test)
        paticka.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        paticka.addView(zastav)

        val obsah = svisle().apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(pozadi, Color.parseColor("#0B1637"),
                    Color.parseColor("#281353"))
            )
            setPadding(dp(36), dp(18), dp(36), dp(23))
            addView(vrsek, odsazeni(bottom = 22))
            addView(hlavni, odsazeni(bottom = 22))
            addView(statusy, odsazeni(bottom = 12))
            addView(paticka, odsazeni(bottom = 8))
            addView(prijemDiag)
        }
        nastavObsah(obsah, sirka = ViewGroup.LayoutParams.MATCH_PARENT)
        test.requestFocus()

        val obnovaPrijmu = object : Runnable {
            override fun run() {
                if (obrazovka != "prijem" || isFinishing) return
                val zbyva = IPhoneTVPairing.remainingSeconds()
                if (zbyva == 0) shownCode = null
                val code = shownCode?.takeIf { zbyva > 0 }
                policka.forEachIndexed { index, policko ->
                    policko.text = code?.getOrNull(index)?.toString() ?: "•"
                }
                val spojeno = IPhoneTVPairing.connectedRecently()
                stavTitulku.text = if (spojeno) "Připojeno ✓"
                    else "Čeká na telefon"
                stavTitulku.setTextColor(if (spojeno) zelena
                    else Color.parseColor("#94A3B8"))
                stavParovani.text = when {
                    spojeno -> "✓ Telefon je spárovaný a připojený"
                    code != null -> "Kód platí ještě $zbyva s"
                    IPhoneTVPairing.paired(this@MainActivity) ->
                        "Telefon je spárovaný · čekám na spojení"
                    else -> "Zvol Nový párovací kód"
                }
                prijemDiag.text = PrijemService.statistikaPrijmu()
                ui.postDelayed(this, 1000)
            }
        }
        ui.post(obnovaPrijmu)
    }

'''
    return splice_once(s,start,end,new)

def main():
    if len(sys.argv)!=3 or sys.argv[1] not in ("mobile","tv"):
        raise SystemExit("Usage: redesign_gamelingo.py mobile|tv <upstream_checkout>")
    mode,base=sys.argv[1],Path(sys.argv[2])
    p=base/SRC/"MainActivity.kt"
    original=p.read_text(encoding="utf8")
    transformed=redesign_mobile(original) if mode=="mobile" else redesign_tv(original)
    p.write_text(transformed,encoding="utf8")
    if mode=="mobile":
        # Independent profile manager is checked into GameLingo, not into upstream.
        for module in ("GameLingoProfiles.kt", "GameLingoSettings.kt"):
            source=Path(__file__).with_name(module)
            (base/SRC/source.name).write_text(source.read_text(encoding="utf8"),encoding="utf8")
    print("GameLingo",mode,"reference dashboard installed; network and OCR unchanged")

if __name__=="__main__":
    main()
