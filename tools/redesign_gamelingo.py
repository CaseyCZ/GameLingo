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
            // Native GameLingo layout matches iPhone: header -> hero -> saved
            // console/TV -> single CTA -> four shortcuts -> readiness -> tabs.
            // Console authentication, OCR and Remote Play are untouched.
            obsah.background = GameLingoVisualKit.pageBackground(this)
            obsah.setPadding(dp(14), dp(10), dp(14), dp(20))

            fun openDevices() {
                GameLingoDeviceCenter.show(this,
                    onXbox = { ukazPripojeniXbox() },
                    onPlayStation = { ukazPripojeni() },
                    onTV = { vyberVystupTv() },
                    onUpdated = { ukazHlavni() })
            }
            fun openSettings() {
                GameLingoSettings.show(this, nast,
                    onAreas = {
                        if (nejakaKonzole)
                            startActivity(Intent(this, TelefonActivity::class.java))
                        else ukazPripojeni()
                    },
                    onLanguage = { GameLingoLanguages.show(this) { ukazHlavni() } },
                    onDevices = { openDevices() })
            }
            fun openAreas() {
                if (nejakaKonzole)
                    startActivity(Intent(this, TelefonActivity::class.java))
                else android.app.AlertDialog.Builder(this)
                    .setMessage("Nejprve vyber a připoj konzoli.")
                    .setPositiveButton("Xbox") { _, _ -> ukazPripojeniXbox() }
                    .setNeutralButton("PlayStation") { _, _ -> ukazPripojeni() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }

            obsah.addView(GameLingoVisualKit.brandHeader(this) { openSettings() },
                odsazeni(bottom = 14))
            obsah.addView(GameLingoVisualKit.hero(this), odsazeni(bottom = 18))
            obsah.addView(GameLingoVisualKit.section(this, "Moje zařízení"))

            val psSelected = GameLingoPlayStationConsoles.list(this)
                .firstOrNull { it.selected }
            val selectedXbox = getSharedPreferences("gamelingo_devices", MODE_PRIVATE)
                .getString("selectedXboxId", null)
            val primaryFamily = when {
                selectedXbox != null -> "Xbox"
                psSelected != null -> "PlayStation"
                xbox != null -> "Xbox"
                udaje.sparovano -> "PlayStation"
                else -> "GameLingo"
            }
            val primaryName = when (primaryFamily) {
                "Xbox" -> "Xbox"
                "PlayStation" -> psSelected?.name ?: "PlayStation"
                else -> "Vybrat konzoli"
            }
            obsah.addView(GameLingoVisualKit.deviceCard(
                this, primaryFamily, primaryName,
                if (nejakaKonzole) "Nastaveno · spojení ověřím při spuštění"
                else "Přidat Xbox nebo PlayStation",
                false
            ) { openDevices() }, odsazeni(bottom = 10))

            val tvStore = TvProfily(this)
            val savedTv = tvStore.posledniId?.let { tvStore.najdi(it) }
            obsah.addView(GameLingoVisualKit.deviceCard(
                this, "TV",
                if (tvStore.pouzeTelefon) "Telefon"
                else savedTv?.jmeno ?: "Google TV / Android TV",
                if (tvStore.pouzeTelefon) "Titulky na telefonu"
                else if (savedTv == null) "Vyber nebo spáruj televizi"
                else "Televize uložena · spojení se ověří",
                false
            ) { openDevices() }, odsazeni(bottom = 18))

            obsah.addView(GameLingoVisualKit.cta(
                this,
                if (RemotePlayService.aktivni) "Zastavit překlad" else "Spustit překlad"
            ) {
                if (nejakaKonzole) prepniPreklad()
                else android.app.AlertDialog.Builder(this)
                    .setTitle("Kterou konzoli používáš?")
                    .setItems(arrayOf("PlayStation 5 / 4", "Xbox Series / One")) { _, vyber ->
                        if (vyber == 0) ukazPripojeni() else ukazPripojeniXbox()
                    }.show()
            }, odsazeni(bottom = 17))

            val prvni = vodorovne()
            prvni.addView(GameLingoVisualKit.navigationTile(this,
                "▣", "Oblasti na obrazovce") { openAreas() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(10) })
            prvni.addView(GameLingoVisualKit.navigationTile(this,
                "♧", "Profily her") {
                GameLingoProfiles.show(this, nast) { ukazHlavni() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            obsah.addView(prvni, odsazeni(bottom = 10))

            val druhy = vodorovne()
            druhy.addView(GameLingoVisualKit.navigationTile(this,
                "☷", "Nastavení překladu") { openSettings() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(10) })
            druhy.addView(GameLingoVisualKit.navigationTile(this,
                "▭", "Zařízení") { openDevices() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            obsah.addView(druhy, odsazeni(bottom = 15))

            // Show genuine connection state, not fabricated green indicators.
            obsah.addView(GameLingoVisualKit.status(this,
                if (RemotePlayService.aktivni) "Probíhá připojení / překlad"
                else if (nejakaKonzole) "Připraveno ke spuštění"
                else "Připoj konzoli",
                if (nejakaKonzole) "Aktuální stav připojení je zobrazen níže."
                else "Připojení Xbox / PlayStation otevři v Zařízeních.",
                nejakaKonzole
            ), odsazeni(bottom = 9))
            obsah.addView(stavRadek, odsazeni(bottom = 12))

            val navigace = GameLingoNavigation.create(
                this,
                onHome = { ukazHlavni() },
                onAreas = { openAreas() },
                onDevices = { openDevices() },
                onSettings = { openSettings() },
                onAbout = { GameLingoAbout.show(this) }
            )
            obsah.addView(navigace, odsazeni(top = 7))

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
    # Match iOS: the five-tab navigation stays fixed outside the scrolling page.
    # Other Android screens (pairing, registration, help) keep their own layouts.
    old_root = '''        setContentView(ScrollView(this).apply {
            setBackgroundColor(CERNA)
            isFillViewport = true
            addView(ram)
        })'''
    new_root = '''        val fixedNavigation = if (telefon) (obsah as? ViewGroup)
            ?.findViewWithTag<View>("gamelingo-bottom-nav") else null
        if (fixedNavigation != null) {
            (fixedNavigation.parent as? ViewGroup)?.removeView(fixedNavigation)
            ram.setBackgroundColor(Color.TRANSPARENT)
            val container = FrameLayout(this).apply {
                background = GameLingoVisualKit.pageBackground(this@MainActivity)
            }
            val scrolling = ScrollView(this).apply {
                background = GameLingoVisualKit.pageBackground(this@MainActivity)
                isFillViewport = true
                clipToPadding = false
                setPadding(0, 0, 0, dp(94))
                addView(ram)
            }
            container.addView(scrolling, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            container.addView(fixedNavigation, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            ).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
                bottomMargin = dp(5)
            })
            setContentView(container)
        } else {
            setContentView(ScrollView(this).apply {
                setBackgroundColor(CERNA)
                isFillViewport = true
                addView(ram)
            })
        }'''
    if s.count(old_root) != 1:
        raise RuntimeError("Android native fixed navigation root missing")
    s = s.replace(old_root, new_root, 1)
    return s

def redesign_tv(s: str) -> str:
    start = '    private fun ukazPrijem() {\n'
    end = '    private val obnova: Runnable = object : Runnable {\n'
    new = r'''    private fun ukazPrijem() {
        obrazovka = "prijem"
        val pozadi = Color.parseColor("#070B19")
        val karta = Color.parseColor("#111A33")
        val obrys = Color.parseColor("#554078")
        val akcent = Color.parseColor("#02D6FF")
        val fialova = Color.parseColor("#A329FA")
        val zelena = Color.parseColor("#35E57D")
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val ipTv = cm.activeNetwork?.let { cm.getLinkProperties(it) }
            ?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }
            ?.address?.hostAddress ?: "Adresa se zjišťuje"

        fun modraKarta(): LinearLayout = svisle().apply {
            background = GameLingoTVUi.panel(this@MainActivity)
            setPadding(dp(23), dp(21), dp(23), dp(21))
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

        val levá = modraKarta()
        levá.addView(GameLingoTVUi.title(this,
            "Překlad z telefonu", 26f), odsazeni(bottom = 13))
        levá.addView(GameLingoTVUi.title(this,
            "Získej titulky z iPhonu nebo Androidu.", 17f, muted = true),
            odsazeni(bottom = 18))
        listOf(
            "1" to "Otevři GameLingo na telefonu",
            "2" to "Spáruj TV šestimístným kódem",
            "3" to "Spusť překlad a vrať se ke hře"
        ).forEach { (index, title) ->
            val krok = vodorovne().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            krok.addView(GameLingoTVUi.title(this, index, 25f).apply {
                gravity = Gravity.CENTER
                setTextColor(akcent)
                background = GameLingoTVUi.panel(this@MainActivity)
            }, LinearLayout.LayoutParams(dp(49), dp(49)).apply {
                rightMargin = dp(13)
            })
            krok.addView(GameLingoTVUi.title(this, title, 17f),
                LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            levá.addView(krok)
        }
        levá.addView(GameLingoTVUi.title(this,
            "Titulky se zobrazí nad hrou, bez přepínání vstupu.",
            15f, muted = true), odsazeni(top = 17))

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
                background = zaobleni(Color.parseColor("#151B39"), 14f, fialova)
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
        akce.addView(GameLingoTVUi.action(
            this, "Nový párovací kód", primary = true) {
            shownCode = IPhoneTVPairing.startCode()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { rightMargin = dp(9) })
        akce.addView(GameLingoTVUi.action(
            this, "Zrušit spárování") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Zrušit párování?")
                .setMessage("Telefon bude nutné spárovat znovu.")
                .setPositiveButton("Zrušit párování") { _, _ ->
                    IPhoneTVPairing.clear(this)
                    shownCode = IPhoneTVPairing.startCode()
                }
                .setNegativeButton("Zpět", null)
                .show()
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

        val prijemDiag = text("", 15f, Color.parseColor("#94A3B8")).apply {
            gravity = Gravity.CENTER
            maxLines = 5
            visibility = View.GONE
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
            background = zaobleni(Color.parseColor("#151B39"), 20f, obrys)
            setPadding(dp(20), dp(15), dp(20), dp(15))
        }
        val sitStav = stavItem("⌁", "Síť", "Zjišťuji")
        val sitLabel = sitStav.getChildAt(2) as TextView
        statusy.addView(sitStav,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val prijimacStav = stavItem("▣", "Přijímač", "Čeká")
        val prijimacLabel = prijimacStav.getChildAt(2) as TextView
        statusy.addView(prijimacStav,
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

        val test = GameLingoTVUi.action(this, "▶  Vyzkoušet titulky") {
            if (!android.provider.Settings.canDrawOverlays(this))
                pozadejOPovoleni()
            else OverlayService.posli(this, Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_SET_TEXT)
                .putExtra(OverlayService.EXTRA_TEXT, "GameLingo · Ukázka titulků")
                .putExtra(OverlayService.EXTRA_DOBA, 8000L)
                .putExtra(OverlayService.EXTRA_VLASTNI,
                    floatArrayOf(0.5f, 0.84f, 0.78f, 1.10f, 1f)))
        }
        val zastav = GameLingoTVUi.action(this, "←  Zpět do nabídky") {
            zpetNaVyber()
        }
        val paticka = vodorovne().apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        paticka.addView(test)
        paticka.addView(View(this), LinearLayout.LayoutParams(dp(14), 1))
        paticka.addView(GameLingoTVUi.action(this, "Podrobnosti") {
            prijemDiag.visibility =
                if (prijemDiag.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        paticka.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        paticka.addView(zastav)

        val obsah = svisle().apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(pozadi, Color.parseColor("#111A33"),
                    Color.parseColor("#211047"))
            )
            setPadding(dp(36), dp(18), dp(36), dp(23))
            addView(vrsek, odsazeni(bottom = 22))
            if (!android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                addView(GameLingoTVUi.action(this@MainActivity,
                    "⚠  Povolit titulky přes obraz hry", primary = true) {
                    pozadejOPovoleni()
                }, odsazeni(bottom = 12))
            }
            addView(hlavni, odsazeni(bottom = 22))
            addView(statusy, odsazeni(bottom = 12))
            addView(paticka, odsazeni(bottom = 8))
            addView(prijemDiag)
        }
        nastavObsah(obsah, sirka = ViewGroup.LayoutParams.MATCH_PARENT)
        akce.getChildAt(0)?.requestFocus()

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
                val networkReady = ipTv != "Adresa se zjišťuje"
                val receiverReady = PrijemService.bezi
                val overlayReady = android.provider.Settings.canDrawOverlays(this@MainActivity)
                sitLabel.text = if (networkReady) "IP: $ipTv" else "Nepřipojeno"
                sitLabel.setTextColor(if (networkReady) zelena else Color.parseColor("#94A3B8"))
                prijimacLabel.text = if (receiverReady) "Aktivní" else "Zastaven"
                prijimacLabel.setTextColor(if (receiverReady) zelena else Color.parseColor("#94A3B8"))
                stavTitulku.text = when {
                    !overlayReady -> "Povol překryv"
                    spojeno && receiverReady -> "Telefon připojen"
                    else -> "Čeká na telefon"
                }
                stavTitulku.setTextColor(if (spojeno && receiverReady && overlayReady)
                    zelena else Color.parseColor("#94A3B8"))
                stavParovani.text = when {
                    spojeno && receiverReady -> "✓ Telefon je spárovaný a připojený"
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
        for module in ("GameLingoProfiles.kt", "GameLingoSettings.kt", "GameLingoSubtitleAppearance.kt", "GameLingoDiagnostics.kt"):
            source=Path(__file__).with_name(module)
            (base/SRC/source.name).write_text(source.read_text(encoding="utf8"),encoding="utf8")
    print("GameLingo",mode,"reference dashboard installed; network and OCR unchanged")

if __name__=="__main__":
    main()
