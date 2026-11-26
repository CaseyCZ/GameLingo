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

            // The five tabs share one persistent page host, just like SwiftUI.
            // Source setup/pairing remains an explicit native action in each tab.
            lateinit var openTab: (Int) -> Unit
            fun openDevices() { openTab(2) }
            fun openSettings() { openTab(3) }
            fun editAreas() {
                if (nejakaKonzole)
                    startActivity(Intent(this, TelefonActivity::class.java))
                else android.app.AlertDialog.Builder(this)
                    .setMessage(GameLingoLanguages.ui(this, "Nejprve vyber a připoj konzoli."))
                    .setPositiveButton("Xbox") { _, _ -> ukazPripojeniXbox() }
                    .setNeutralButton("PlayStation") { _, _ -> ukazPripojeni() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
            fun openAreas() { openTab(1) }

            obsah.addView(GameLingoVisualKit.brandHeader(this) { openSettings() },
                odsazeni(bottom = 14))
            obsah.addView(GameLingoVisualKit.hero(this), odsazeni(bottom = 18))
            obsah.addView(GameLingoVisualKit.section(this, GameLingoLanguages.ui(this, "Moje zařízení")))

            val psSelected = GameLingoPlayStationConsoles.list(this)
                .firstOrNull { it.selected }
            val selectedXbox = getSharedPreferences("gamelingo_devices", MODE_PRIVATE)
                .getString("selectedXboxId", null)
            val liveImage = RemotePlayService.aktivni &&
                RemotePlayService.prehledSpojeni().uroven == UrovenSpojeni.OK
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
                else -> GameLingoLanguages.ui(this, "Vybrat konzoli")
            }
            obsah.addView(GameLingoVisualKit.deviceCard(
                this, primaryFamily, primaryName,
                if (liveImage) GameLingoLanguages.ui(this, "Snímání aktivní")
                else if (nejakaKonzole) GameLingoLanguages.ui(this, "Nastaveno · spojení ověřím při spuštění")
                else GameLingoLanguages.ui(this, "Přidat Xbox nebo PlayStation"),
                liveImage
            ) { openDevices() }, odsazeni(bottom = 10))

            val tvStore = TvProfily(this)
            val savedTv = tvStore.posledniId?.let { tvStore.najdi(it) }
            obsah.addView(GameLingoVisualKit.deviceCard(
                this, "TV",
                if (tvStore.pouzeTelefon) GameLingoLanguages.ui(this, "Telefon")
                else savedTv?.jmeno ?: "Google TV / Android TV",
                if (tvStore.pouzeTelefon) GameLingoLanguages.ui(this, "Titulky na telefonu")
                else if (savedTv == null) GameLingoLanguages.ui(this, "Vyber nebo spáruj televizi")
                else GameLingoLanguages.ui(this, "Televize uložena · spojení se ověří"),
                tvStore.pouzeTelefon
            ) { openDevices() }, odsazeni(bottom = 18))

            obsah.addView(GameLingoVisualKit.cta(
                this,
                if (RemotePlayService.aktivni) GameLingoLanguages.ui(this, "Zastavit překlad") else GameLingoLanguages.ui(this, "Spustit překlad")
            ) {
                if (nejakaKonzole) prepniPreklad()
                else android.app.AlertDialog.Builder(this)
                    .setTitle(GameLingoLanguages.ui(this, "Kterou konzoli používáš?"))
                    .setItems(arrayOf("PlayStation 5 / 4", "Xbox Series / One")) { _, vyber ->
                        if (vyber == 0) ukazPripojeni() else ukazPripojeniXbox()
                    }.show()
            }, odsazeni(bottom = 17))

            val prvni = vodorovne()
            prvni.addView(GameLingoVisualKit.navigationTile(this,
                "▣", GameLingoLanguages.ui(this, "Oblasti na obrazovce")) { openAreas() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(10) })
            prvni.addView(GameLingoVisualKit.navigationTile(this,
                "♧", GameLingoLanguages.ui(this, "Profily her")) {
                GameLingoProfiles.show(this, nast) { ukazHlavni() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            obsah.addView(prvni, odsazeni(bottom = 10))

            val druhy = vodorovne()
            druhy.addView(GameLingoVisualKit.navigationTile(this,
                "☷", GameLingoLanguages.ui(this, "Nastavení překladu")) { openSettings() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(10) })
            druhy.addView(GameLingoVisualKit.navigationTile(this,
                "▭", GameLingoLanguages.ui(this, "Zařízení")) { openDevices() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            obsah.addView(druhy, odsazeni(bottom = 15))

            // Show genuine connection state, not fabricated green indicators.
            obsah.addView(GameLingoVisualKit.status(this,
                if (liveImage && tvStore.pouzeTelefon) GameLingoLanguages.ui(this, "Snímání aktivní")
                else if (RemotePlayService.aktivni && !tvStore.pouzeTelefon)
                    GameLingoLanguages.ui(this, "Remote Play běží · TV spojení neověřeno")
                else if (RemotePlayService.aktivni) GameLingoLanguages.ui(this, "Připojuji / načítám snímky")
                else if (!nejakaKonzole) GameLingoLanguages.ui(this, "Nejprve připoj konzoli")
                else if (!tvStore.pouzeTelefon && savedTv == null)
                    GameLingoLanguages.ui(this, "Vyber nebo spáruj televizi")
                else GameLingoLanguages.ui(this, "Konzole je nastavená · připojení neověřeno"),
                if (liveImage && tvStore.pouzeTelefon)
                    GameLingoLanguages.ui(this, "Video snímky se přijímají, titulky zpracovává OCR.")
                else if (!tvStore.pouzeTelefon)
                    GameLingoLanguages.ui(this, "Uložený televizor nepotvrzuje živé přijetí titulků.")
                else GameLingoLanguages.ui(this, "Uložené zařízení samo o sobě nepotvrzuje živé spojení."),
                // Green status only with an actual live video source and
                // local phone output. The TV has no verified ACK in this lane.
                liveImage && tvStore.pouzeTelefon
            ), odsazeni(bottom = 9))
            obsah.addView(stavRadek, odsazeni(bottom = 12))

            // Preserve all native, working home widgets; only swap the page
            // inside the same scroll container when the bottom tab is tapped.
            val homePage = svisle()
            while (obsah.childCount > 0) {
                val view = obsah.getChildAt(0)
                obsah.removeViewAt(0)
                homePage.addView(view)
            }
            val pageHost = svisle()
            obsah.addView(pageHost, odsazeni(bottom = 12))
            openTab = { index ->
                selectedMobileTab = index.coerceIn(0, 4)
                pageHost.removeAllViews()
                val page = when (selectedMobileTab) {
                    1 -> GameLingoRegions.content(this, onEdit = { editAreas() })
                    2 -> GameLingoDeviceCenter.content(this,
                        onXbox = { ukazPripojeniXbox() },
                        onPlayStation = { ukazPripojeni() },
                        onTV = { vyberVystupTv() },
                        onUpdated = { ukazHlavni() })
                    3 -> GameLingoSettings.content(this, nast,
                        onAreas = { editAreas() },
                        onLanguage = { GameLingoLanguages.show(this) { ukazHlavni() } },
                        onDevices = { openDevices() },
                        onRefresh = { openTab(3) })
                    4 -> GameLingoAbout.content(this)
                    else -> homePage
                }
                pageHost.addView(page, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val navigace = GameLingoNavigation.create(
                this,
                onHome = { openTab(0) },
                onAreas = { openTab(1) },
                onDevices = { openTab(2) },
                onSettings = { openTab(3) },
                onAbout = { openTab(4) },
                initialIndex = selectedMobileTab
            )
            obsah.addView(navigace, odsazeni(top = 7))
            openTab(selectedMobileTab)

'''
    s = splice_once(s,start,end,replacement)
    # Remember the tab through live status refreshes, pairing screens and
    # returns from native editors. The initial app launch remains on Home.
    marker = "    private fun ukazHlavni() {"
    if s.count(marker) != 1:
        raise RuntimeError("MainActivity native dashboard method missing")
    s = s.replace(marker,
        "    private var selectedMobileTab = 0\n" + marker, 1)
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
            addView(text(GameLingoLanguages.ui(this@MainActivity, "Hraj. Rozuměj."), 15f,
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
            setPadding(dp(13), dp(10), dp(13), dp(10))
        }

        val zastav = GameLingoTVUi.action(this, GameLingoLanguages.ui(this, "Zpět")) {
            zpetNaVyber()
        }
        val logo = svisle().apply { gravity = Gravity.CENTER }
        logo.addView(GameLingoVisualKit.wordmark(this@MainActivity, compact = true))
        logo.addView(text(packageManager.getPackageInfo(packageName, 0).versionName ?: "GameLingo TV", 14f,
            Color.parseColor("#94A3B8")).apply { gravity = Gravity.CENTER })
        val vrsek = GameLingoTVUi.header(this, logo, zastav, GameLingoHelp.button(this, "tv"))

        val levá = modraKarta()
        levá.addView(GameLingoTVUi.title(this,
            GameLingoLanguages.ui(this@MainActivity, "Překlad z telefonu"), 26f), odsazeni(bottom = 5))
        levá.addView(GameLingoTVUi.title(this,
            GameLingoLanguages.ui(this@MainActivity, "Získej titulky z iPhonu nebo Androidu."), 17f, muted = true),
            odsazeni(bottom = 5))
        listOf(
            "1" to GameLingoLanguages.ui(this@MainActivity, "Otevři GameLingo na telefonu"),
            "2" to GameLingoLanguages.ui(this@MainActivity, "Spáruj TV šestimístným kódem"),
            "3" to GameLingoLanguages.ui(this@MainActivity, "Spusť překlad a vrať se ke hře")
        ).forEach { (index, title) ->
            val krok = vodorovne().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            krok.addView(GameLingoTVUi.title(this, index, 25f).apply {
                gravity = Gravity.CENTER
                setTextColor(akcent)
                background = GameLingoTVUi.panel(this@MainActivity)
            }, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                rightMargin = dp(13)
            })
            krok.addView(GameLingoTVUi.title(this, title, 17f),
                LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            levá.addView(krok)
        }
        levá.addView(GameLingoTVUi.title(this,
            GameLingoLanguages.ui(this@MainActivity, "Titulky se zobrazí nad hrou, bez přepínání vstupu."),
            15f, muted = true), odsazeni(top = 6))

        val pravá = modraKarta()
        pravá.addView(text(GameLingoLanguages.ui(this@MainActivity, "Připojte svůj telefon"), 25f,
            Color.WHITE, tucne = true))
        pravá.addView(text(
            GameLingoLanguages.ui(this@MainActivity, "Otevřete GameLingo na telefonu a zadejte tento šestimístný kód."),
            19f, Color.parseColor("#CBD5E1")), odsazeni(top = 4, bottom = 5))

        // Always show a fresh six-digit code when opening the receiver.
        // Existing phones remain paired when another phone confirms a new code.
        var shownCode: String? = IPhoneTVPairing.startCode()
        val kodRadek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        val policka = (0 until 6).map {
            text("•", 30f, Color.WHITE, tucne = true).apply {
                gravity = Gravity.CENTER
                background = zaobleni(Color.parseColor("#151B39"), 14f, fialova)
                setPadding(dp(3), dp(5), dp(3), dp(5))
                minWidth = dp(28)
                kodRadek.addView(this,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { rightMargin = dp(if (it == 5) 0 else 4) })
            }
        }
        val qrImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = GameLingoLanguages.ui(this@MainActivity, "Párovací QR kód")
        }
        var displayedQR: String? = null
        val pinAndQr = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        pinAndQr.addView(kodRadek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pinAndQr.addView(qrImage, LinearLayout.LayoutParams(dp(115), dp(115)).apply { leftMargin = dp(9) })
        pravá.addView(pinAndQr, odsazeni(top = 4, bottom = 4))
        pravá.addView(GameLingoTVUi.action(this, GameLingoLanguages.ui(this@MainActivity, "Zobrazit QR kód")) {
            val pin = IPhoneTVPairing.activeCode()
            val address = cm.activeNetwork?.let { cm.getLinkProperties(it) }
                ?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress
            if (pin != null && address != null) GameLingoQRDisplay.show(this, address, pin)
            else android.app.AlertDialog.Builder(this)
                .setMessage(GameLingoLanguages.ui(this@MainActivity, "Vygeneruj nový PIN a ověř připojení k místní síti."))
                .setPositiveButton("OK", null).show()
        }, odsazeni(bottom = 4))

        val stavParovani = text("", 18f, Color.parseColor("#94A3B8"))
        pravá.addView(stavParovani, odsazeni(top = 12))
        pravá.addView(text(GameLingoLanguages.ui(this@MainActivity, "IP televizoru:") + " " + (if (ipTv == "Adresa se zjišťuje") GameLingoLanguages.ui(this@MainActivity, "Adresa se zjišťuje") else ipTv), 17f,
            akcent), odsazeni(top = 9))
        val akce = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        akce.addView(GameLingoTVUi.action(
            this, GameLingoLanguages.ui(this@MainActivity, "Nový párovací kód"), primary = true) {
            shownCode = IPhoneTVPairing.startCode()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { rightMargin = dp(9) })
        akce.addView(GameLingoTVUi.action(
            this, GameLingoLanguages.ui(this@MainActivity, "Spárované telefony")) {
            val phones = IPhoneTVPairing.phones(this)
            if (phones.isEmpty()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle(GameLingoLanguages.ui(this@MainActivity, "Spárované telefony"))
                    .setMessage(GameLingoLanguages.ui(this@MainActivity, "Zatím žádný telefon."))
                    .setPositiveButton("OK", null).show()
            } else {
                val labels = phones.map { it.label + " · " + GameLingoLanguages.ui(this@MainActivity, if (it.connected) "Připojen" else "Spárován") }.toTypedArray()
                GameLingoTVUi.select(this,GameLingoLanguages.ui(this@MainActivity, "Spárované telefony"),labels) { index ->
                        val phone = phones[index]
                        android.app.AlertDialog.Builder(this)
                            .setTitle(GameLingoLanguages.ui(this@MainActivity, "Odpárovat") + " " + phone.label + "?")
                            .setMessage(GameLingoLanguages.ui(this@MainActivity, "Ostatní telefony zůstanou spárované."))
                            .setPositiveButton(GameLingoLanguages.ui(this@MainActivity, "Odpárovat")) { _, _ ->
                                if (!IPhoneTVPairing.unpair(this, phone.id)) {
                                    android.app.AlertDialog.Builder(this)
                                        .setMessage(GameLingoLanguages.ui(this@MainActivity, "Odpárování se nepodařilo uložit."))
                                        .setPositiveButton("OK", null).show()
                                }
                            }
                            .setNegativeButton(GameLingoLanguages.ui(this@MainActivity, "Zpět"), null).show()
                    }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pravá.addView(akce, odsazeni(top = 6))

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
            visibility = View.INVISIBLE
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
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }
        val sitStav = stavItem("⌁", GameLingoLanguages.ui(this@MainActivity, "Síť"), GameLingoLanguages.ui(this@MainActivity, "Zjišťuji"))
        val sitLabel = sitStav.getChildAt(2) as TextView
        statusy.addView(sitStav,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val prijimacStav = stavItem("▣", GameLingoLanguages.ui(this@MainActivity, "Přijímač"), GameLingoLanguages.ui(this@MainActivity, "Čeká"))
        val prijimacLabel = prijimacStav.getChildAt(2) as TextView
        statusy.addView(prijimacStav,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val stavTitulku = text(GameLingoLanguages.ui(this@MainActivity, "Čeká na telefon"), 16f,
            Color.parseColor("#94A3B8")).apply {
            gravity = Gravity.CENTER
        }
        statusy.addView(svisle().apply {
            gravity = Gravity.CENTER
            addView(text("▤", 28f, akcent).apply {
                gravity = Gravity.CENTER
            })
            addView(text(GameLingoLanguages.ui(this@MainActivity, "Titulky"), 19f, Color.WHITE, tucne = true).apply {
                gravity = Gravity.CENTER
            }, odsazeni(top = 3))
            addView(stavTitulku, odsazeni(top = 3))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val test = GameLingoTVUi.action(this, "▶  " + GameLingoLanguages.ui(this@MainActivity, "Vyzkoušet titulky")) {
            if (!android.provider.Settings.canDrawOverlays(this))
                pozadejOPovoleni()
            else OverlayService.posli(this, Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_SET_TEXT)
                .putExtra(OverlayService.EXTRA_TEXT, GameLingoLanguages.ui(this@MainActivity, "GameLingo · Ukázka titulků"))
                .putExtra(OverlayService.EXTRA_DOBA, 8000L)
                .putExtra(OverlayService.EXTRA_VLASTNI,
                    floatArrayOf(0.5f, 0.84f, 0.78f, 1.10f, 1f)))
        }
        val paticka = vodorovne().apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        paticka.addView(test)
        paticka.addView(View(this), LinearLayout.LayoutParams(dp(14), 1))
        paticka.addView(GameLingoTVUi.action(this, GameLingoLanguages.ui(this@MainActivity, "Podrobnosti")) {
            prijemDiag.visibility =
                if (prijemDiag.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
        })
        paticka.addView(prijemDiag, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(5); rightMargin = dp(5)
        })
        // TV_RECEIVER_STOP_ACTION

        val obsah = svisle().apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(pozadi, Color.parseColor("#111A33"),
                    Color.parseColor("#211047"))
            )
            setPadding(dp(22), dp(10), dp(22), dp(10))
            addView(vrsek, odsazeni(bottom = 8))
            if (!android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                addView(GameLingoTVUi.action(this@MainActivity,
                    "⚠  " + GameLingoLanguages.ui(this@MainActivity, "Povolit titulky přes obraz hry"), primary = true) {
                    pozadejOPovoleni()
                }, odsazeni(bottom = 12))
            }
            addView(hlavni, odsazeni(bottom = 8))
            addView(statusy, odsazeni(bottom = 6))
            addView(paticka, odsazeni(bottom = 8))
        }
        setContentView(GameLingoTVFit(this, obsah))
        akce.getChildAt(0)?.requestFocus()

        val obnovaPrijmu = object : Runnable {
            override fun run() {
                if (obrazovka != "prijem" || isFinishing) return
                val zbyva = IPhoneTVPairing.remainingSeconds()
                if (zbyva == 0) shownCode = null
                val code = IPhoneTVPairing.activeCode()?.takeIf { zbyva > 0 }
                val qrKey = code?.let { "$ipTv:$it" }
                if (qrKey != displayedQR) {
                    displayedQR = qrKey
                    qrImage.setImageBitmap(code?.let { GameLingoQRDisplay.bitmap(ipTv, it) })
                }
                policka.forEachIndexed { index, policko ->
                    policko.text = code?.getOrNull(index)?.toString() ?: "•"
                }
                val phones = IPhoneTVPairing.phones(this@MainActivity)
                val connectedCount = phones.count { it.connected }
                val spojeno = connectedCount > 0
                val networkReady = ipTv != "Adresa se zjišťuje"
                val receiverInfo = PrijemService.statistikaPrijmu()
                val receiverError = receiverInfo.startsWith("Chyba UDP")
                val receiverReady = PrijemService.bezi && !receiverError
                val overlayReady = android.provider.Settings.canDrawOverlays(this@MainActivity)
                sitLabel.text = if (networkReady) "IP: $ipTv" else GameLingoLanguages.ui(this@MainActivity, "Nepřipojeno")
                sitLabel.setTextColor(if (networkReady) zelena else Color.parseColor("#94A3B8"))
                prijimacLabel.text = when {
                    receiverError -> GameLingoLanguages.ui(this@MainActivity, "Port UDP obsazen")
                    receiverReady -> GameLingoLanguages.ui(this@MainActivity, "Aktivní · UDP") + " ${PrijemService.prijimaciPort()}"
                    else -> GameLingoLanguages.ui(this@MainActivity, "Zastaven")
                }
                prijimacLabel.setTextColor(if (receiverReady) zelena else Color.parseColor("#94A3B8"))
                stavTitulku.text = when {
                    !overlayReady -> GameLingoLanguages.ui(this@MainActivity, "Povol překryv")
                    spojeno && receiverReady -> GameLingoLanguages.ui(this@MainActivity, "Telefon připojen")
                    else -> GameLingoLanguages.ui(this@MainActivity, "Čeká na telefon")
                }
                stavTitulku.setTextColor(if (spojeno && receiverReady && overlayReady)
                    zelena else Color.parseColor("#94A3B8"))
                stavParovani.text = when {
                    receiverError -> GameLingoLanguages.ui(this@MainActivity, "Nelze přijímat: všechny UDP porty 41989–41993 jsou obsazené")
                    code != null -> GameLingoLanguages.ui(this@MainActivity, "Kód platí ještě:") + " $zbyva s · " + GameLingoLanguages.ui(this@MainActivity, "Spárováno:") + " " + phones.size
                    spojeno && receiverReady -> "✓ " + GameLingoLanguages.ui(this@MainActivity, "Připojeno:") + " $connectedCount / " + phones.size
                    phones.isNotEmpty() -> GameLingoLanguages.ui(this@MainActivity, "Spárováno:") + " " + phones.size + " · " + GameLingoLanguages.ui(this@MainActivity, "Čekám na spojení")
                    else -> GameLingoLanguages.ui(this@MainActivity, "Zvol Nový párovací kód")
                }
                // Show bind failures immediately: the user should not have to
                // find a hidden developer panel to diagnose an iPhone timeout.
                if (receiverError) prijemDiag.visibility = View.VISIBLE
                prijemDiag.text = receiverInfo
                ui.postDelayed(this, 1000)
            }
        }
        ui.post(obnovaPrijmu)
    }

'''
    return splice_once(s,start,end,new)

def wire_dialogue_menu_filter(source: str) -> str:
    # The user-facing blue-area menu switch also controls the upstream
    # short-row column rejection. Keep hint extraction and OCR quality
    # checks separate, and never modify prectiVolby (yellow area).
    before = "if (vybrane.size >= 3 && vybrane.count { VyberTitulku.pocetSlov(it.text) <= 2 } >= vybrane.size - 1) {"
    after = "if (GameLingoOCRConfig.filterMenuText && vybrane.size >= 3 && vybrane.count { VyberTitulku.pocetSlov(it.text) <= 2 } >= vybrane.size - 1) {"
    if source.count(before) != 1:
        raise RuntimeError("Android dialogue menu column anchor changed")
    return source.replace(before, after, 1)

def wire_yellow_control_filter(source: str) -> str:
    # Filter the paired text/rectangle result before either consumer computes
    # the highlighted index. Dialogue OCR and its existing filters are untouched.
    start = source.index("    fun prectiVolby(")
    end = source.index("    class Titulek(", start)
    before = "                })\n            }\n            .addOnFailureListener"
    after = "                }.filter { GameLingoYellowOCRFilter.keeps(it.first, GameLingoOCRConfig.filterChoiceControls) && GameLingoIgnoredLines.keeps(it.first, GameLingoOCRConfig.ignoredYellowEntries) && (GameLingoOCRConfig.minYellowHeight == 0 || it.second.height().toLong() * 100 >= obraz.height.toLong() * GameLingoOCRConfig.minYellowHeight) })\n            }\n            .addOnFailureListener"
    lane = source[start:end]
    if lane.count(before) != 1:
        raise RuntimeError("Android yellow OCR result anchor changed")
    return source[:start] + lane.replace(before, after, 1) + source[end:]

def wire_blue_minimum_height(source: str) -> str:
    before = "OcrKus(l.text.trim(), it.left, it.top, it.right, it.bottom, blok)"
    after = "if (GameLingoOCRConfig.minBlueHeight == 0 || (it.bottom - it.top).toLong() * 100 >= obraz.height.toLong() * GameLingoOCRConfig.minBlueHeight) OcrKus(l.text.trim(), it.left, it.top, it.right, it.bottom, blok) else null"
    if source.count(before) != 1:
        raise RuntimeError("Android blue OCR height anchor changed")
    return source.replace(before, after, 1)

def main():
    if len(sys.argv)!=3 or sys.argv[1] not in ("mobile","tv"):
        raise SystemExit("Usage: redesign_gamelingo.py mobile|tv <upstream_checkout>")
    mode,base=sys.argv[1],Path(sys.argv[2])
    p=base/SRC/"MainActivity.kt"
    original=p.read_text(encoding="utf8")
    transformed=redesign_mobile(original) if mode=="mobile" else redesign_tv(original)
    p.write_text(transformed,encoding="utf8")
    # Shared direct OCR settings; never change receiver-only TV transport.
    select=base/SRC/"VyberTitulku.kt"
    code=select.read_text(encoding="utf8")
    before='''        }.filter { it.text.count(Char::isLetter) >= 3 }
            .filter { (it.left + it.right) / 2f / sirka in 0.3f..0.7f }
            .filterNot { jeMenu(it.text) }'''
    after='''        }.filter { it.text.count(Char::isLetter) >=
                (if (GameLingoOCRConfig.mode == "street") 2 else 3) }
            .filter { (it.left + it.right) / 2f / sirka in
                (if (GameLingoOCRConfig.expandSearch &&
                     GameLingoOCRConfig.mode != "classic")
                    0.15f..0.85f else 0.3f..0.7f) }
            .filterNot { GameLingoOCRConfig.filterMenuText && jeMenu(it.text) }
            .filter { GameLingoIgnoredLines.keeps(it.text, GameLingoOCRConfig.ignoredBlueEntries) }'''
    if code.count(before)!=1:
        raise RuntimeError("Android OCR native reader source changed")
    select.write_text(code.replace(before,after,1),encoding="utf8")
    reader=base/SRC/"Ctecka.kt"
    reader.write_text(wire_blue_minimum_height(wire_yellow_control_filter(wire_dialogue_menu_filter(reader.read_text(encoding="utf8")))),encoding="utf8")

    service=base/SRC/"RemotePlayService.kt"
    code=service.read_text(encoding="utf8")
    checks=(
        ('        super.onCreate()\n        ui.post(pribehHlidac)',
         '        super.onCreate()\n        GameLingoOCRConfig.load(this)\n        ui.post(pribehHlidac)'),
        ('if (telefon) INTERVAL_MS_TELEFON else if (uspora) INTERVAL_MS_USPORA else INTERVAL_MS',
         'if (telefon) GameLingoOCRConfig.intervalMs() else GameLingoOCRConfig.tvIntervalMs(uspora)'),
    )
    for old,new in checks:
        if code.count(old)!=1:
            raise RuntimeError("Android OCR sampler source changed: "+old[:70])
        code=code.replace(old,new,1)
    service.write_text(code,encoding="utf8")

    for module in ("GameLingoOCRConfig.kt", "GameLingoOCRCadence.kt", "GameLingoYellowOCRFilter.kt"):
        source=Path(__file__).with_name(module)
        (base/SRC/source.name).write_text(source.read_text(encoding="utf8"),encoding="utf8")
    if mode=="mobile":
        # Apply the same locally selected font, foreground and background to
        # the actual in-game captions on the Android phone (not a fake preview).
        phone=base/SRC/"TelefonActivity.kt"
        code=phone.read_text(encoding="utf8")
        changes=(
            ('''    private fun nastavVelikost(t: TextView) {
        t.setAutoSizeTextTypeUniformWithConfiguration(
            14, VELIKOSTI[velikost].toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }''',
             '''    private fun nastavVelikost(t: TextView) {
        val scale = if (Propojeni.Vysilac.cilIp == null)
            GameLingoPhoneSubtitleStyle.scale(this) else 1f
        t.setAutoSizeTextTypeUniformWithConfiguration(
            14, (VELIKOSTI[velikost] * scale).toInt().coerceAtLeast(14),
            1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }'''),
            ('''            nastavVelikost(this)
            setOnClickListener {''',
             '''            nastavVelikost(this)
            GameLingoPhoneSubtitleStyle.apply(this@TelefonActivity, this)
            setOnClickListener {'''),
            ('''                    titulek.text = obarviJmena(text, TelefonTitulky.barvy)
                    titulek.setTextColor(Color.WHITE)''',
             '''                    titulek.text = obarviJmena(text, TelefonTitulky.barvy)
                    GameLingoPhoneSubtitleStyle.apply(this@TelefonActivity, titulek)
                    nastavVelikost(titulek)'''),
            ('''                addView(titulek, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))''',
             '''                addView(titulek, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))'''),
        )
        for old,new in changes:
            if code.count(old)!=1:
                raise RuntimeError("Android phone caption renderer anchor changed: "+old[:65])
            code=code.replace(old,new,1)
        phone.write_text(code,encoding="utf8")

        # Independent native UI belongs only to GameLingo's Android mobile
        # build, never to the Google TV remote-control receiver application.
        for module in ("GameLingoProfiles.kt", "GameLingoSettings.kt",
                       "GameLingoSubtitleAppearance.kt", "GameLingoDiagnostics.kt",
                       "GameLingoRegions.kt", "GameLingoOCRConfig.kt",
                       "GameLingoOCRSettings.kt",
                       "GameLingoPhoneSubtitleStyle.kt"):
            source=Path(__file__).with_name(module)
            (base/SRC/source.name).write_text(source.read_text(encoding="utf8"),encoding="utf8")
    print("GameLingo",mode,"reference dashboard installed; network and OCR unchanged")

if __name__=="__main__":
    main()
