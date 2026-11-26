package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Optional contextual help shared by every native GameLingo Android / TV page. */
object GameLingoHelp {
    private const val NAVY = "#070B14"
    private const val CYAN = "#38BDF8"

    private fun info(screen: String): Pair<String, String> = when (screen) {
        "settings" -> "Nastavení TV" to
            "Jazyky, OCR a Překlad určují, co a jak se překládá. Rozložení nastavuje čtyři oblasti. Vzhled upravuje titulky a Profily ukládají konfiguraci pro hry, filmy a aplikace."
        "ocr" -> "OCR a rozpoznávání" to
            "Zvol režim rozpoznávání, filtrování menu a případně rozšířené hledání. Ruční modrá oblast omezuje čtení na místo dialogů. Před změnou zastav překlad."
        "appearance" -> "Vzhled titulků" to
            "Vyber barvu a pozadí překladu. Polohu, šířku a velikost písma upravíš v Rozložení a uložíš do profilu."
        "diagnostics" -> "Diagnostika" to
            "Test překryvu ověří zobrazení pevného titulku. Test překladového jádra ověří online překlad. Test snímání vyžaduje souhlas Androidu. Náhled lze vymazat."
        "download" -> "Stáhnout aplikaci" to
            "Vyber iPhone nebo Android a naskenuj QR kód fotoaparátem telefonu. Odkaz vede na nejnovější dostupný mobilní balíček na GitHubu. IPA vyžaduje podpis; Android APK může vyžadovat povolení instalace z tohoto zdroje. Pokud vydání nelze ověřit, použij stránku všech vydání. Toto není párovací QR kód."
        "pairing" -> "Párování telefonu" to
            "Telefon a TV připoj ke stejné síti. V mobilu otevři Zařízení, vyber TV a naskenuj QR kód nebo zadej šestimístný PIN. Po vypršení platnosti vytvoř nový kód."
        "history" -> "Historie překladů" to
            "Historie je volitelná a uchovává poslední překlady v paměti. Je oddělená od místní překladové paměti."
        "cache" -> "Překladová paměť" to
            "Místní paměť ukládá originály a překlady pro opakované použití. Můžeš ji vypnout nebo vymazat bez odstranění profilů."
        "glossary" -> "Herní slovník" to
            "Slovník opravuje vlastní výrazy a jména pro zvolenou jazykovou dvojici. Před úpravou zastav překlad."
        "tv" -> "GameLingo TV" to
            "1. Otevři GameLingo na telefonu.\n2. Zvol Google TV a vyber tuto televizi.\n3. Zadej šest číslic z obrazovky.\n\nTelefon a TV musí být ve stejné síti. Pokud používáš starý přijímač PlayCaption, zavři jej. Přijímač může zobrazit titulky přes HDMI obraz jen na podporovaném televizoru."
        "regions" -> "Oblasti" to
            "Modrá oblast čte původní titulky. Zelená určuje místo přeložených dialogů. Žlutá čte volby a herní texty, oranžová zobrazuje jejich překlad. Polohu a rozměry měň podle konkrétní hry. Oblasti se ukládají do profilů."
        "translation" -> "Překlad" to
            "OCR čte dialogy ve zvoleném zdrojovém jazyce. Samostatně lze zapnout dialogy, herní texty a volby. Při změně rychlosti nebo oblastí doporučujeme překlad nejdříve zastavit. Výchozí nastavení vychází z ověřeného základu PlayCaption."
        "profiles" -> "Profily her" to
            "Profil ukládá aktuální OCR oblasti, volby a nastavení překladu pro konkrétní hru, film nebo aplikaci. Údaje o účtech Sony a Microsoft se do profilů neukládají."
        "console" -> "Xbox a PlayStation" to
            "Pro Xbox se přihlašuješ svým účtem Microsoft a povolíš vzdálené funkce. Pro PS5/PS4 použij Remote Play a údaje svého účtu PlayStation. Párovací PIN z konzole se liší od hesla. Do hry ani do konzole se nic neinstaluje."
        "phone" -> "Překlad na telefonu" to
            "Standardní režim funguje bez dalšího nastavování. Automatický režim používá připravenou zónu a další můžeš přidat v Oblastech obrazovky. Pokud překládáš přes TV, telefon i TV musí být ve stejné síti."
        else -> "GameLingo" to
            "Vyber Xbox, PlayStation nebo samostatnou TV, připoj zařízení a spusť překlad. Kam se přeložené titulky zobrazí, nastavíš v Zařízení. Oblasti upravují místo čtení a zobrazení, Profily her ukládají vlastní konfiguraci."
    }

    /** Fixed TV geometry; mobile callers retain their existing dimensions. */
    fun layout(activity: Activity, mobileWidth: Int = -2, mobileHeight: Int = -2): LinearLayout.LayoutParams =
        if (GameLingoTVUi.isTV(activity)) LinearLayout.LayoutParams(
            GameLingoTVUi.dp(activity,64), GameLingoTVUi.dp(activity,64)).apply {
                marginStart=GameLingoTVUi.dp(activity,12)
            }
        else LinearLayout.LayoutParams(mobileWidth,mobileHeight)

    fun button(activity: Activity, topic: String = "home",
               size: Float = 24f): TextView = TextView(activity).apply {
        text = "?"
        textSize = size
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(CYAN))
        setPadding(17, 9, 17, 9)
        minWidth = 52
        minHeight = 48
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#172033"))
            cornerRadius = 16f
            setStroke(2, Color.parseColor("#2A3850"))
        }
        isClickable = true
        isFocusable = true
        contentDescription = GameLingoLanguages.ui(activity, "Otevřít nápovědu")
        setOnClickListener { show(activity, topic) }
        if (!GameLingoTVUi.isTV(activity)) {
            text = ""
            background = null
            setPadding(0,0,0,0)
            setCompoundDrawablesWithIntrinsicBounds(GameLingoVisualKit.iconDrawable("questionmark.circle",Color.parseColor("#02D6FF"),GameLingoVisualKit.dp(activity,23)),null,null,null)
            minWidth=0;minHeight=0
        }
        if (GameLingoTVUi.isTV(activity)) {
            textSize = 23f
            setPadding(0,0,0,0)
            minWidth=GameLingoTVUi.dp(activity,64)
            minHeight=GameLingoTVUi.dp(activity,64)
            layoutParams=layout(activity)
            id=android.view.View.generateViewId()
            fun paint(focused: Boolean) {
                background=GameLingoTVUi.panel(activity,focused).apply {
                    cornerRadius=GameLingoTVUi.dp(activity,32).toFloat()
                    if(!focused) {setColor(Color.TRANSPARENT);setStroke(0,Color.TRANSPARENT)}
                }
                text=""
                val color=if(focused)Color.WHITE else Color.parseColor("#02D6FF")
                val drawable=GameLingoVisualKit.iconDrawable("questionmark.circle",color,GameLingoTVUi.dp(activity,38))
                setCompoundDrawablesWithIntrinsicBounds(drawable,null,null,null)
                setPadding(GameLingoTVUi.dp(activity,13),0,0,0)
                elevation=GameLingoTVUi.dp(activity,if(focused) 10 else 0).toFloat()
            }
            setOnFocusChangeListener { _, focused -> paint(focused) }
            paint(hasFocus())
        }
    }

    fun show(activity: Activity, topic: String) {
        val (titleKey, descriptionKey) = info(topic)
        val title = GameLingoLanguages.ui(activity, titleKey)
        val description = GameLingoLanguages.ui(activity, descriptionKey)
        if (GameLingoTVUi.isTV(activity)) {
            val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
            body.addView(TextView(activity).apply {
                text=description; gravity = android.view.Gravity.CENTER; textSize=19f
                setTextColor(Color.WHITE); setLineSpacing(5f,1.18f)
                background=GameLingoTVUi.panel(activity)
                val padding=GameLingoTVUi.dp(activity,24)
                setPadding(padding,padding,padding,padding)
            },LinearLayout.LayoutParams(-1,-2))
            GameLingoTVUi.page(activity,GameLingoLanguages.ui(activity, "Nápověda") + " · " + title,body)
            return
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(NAVY))
                cornerRadius = 24f
            }
        }
        container.addView(TextView(activity).apply {
            text = title; gravity = android.view.Gravity.CENTER
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(6, 0, 6, 14)
        })
        container.addView(TextView(activity).apply {
            text = description; gravity = android.view.Gravity.CENTER
            textSize = 16f
            setTextColor(Color.parseColor("#D8E2F0"))
            setLineSpacing(4f, 1.12f)
            setPadding(6, 0, 6, 12)
        })
        val scroll = ScrollView(activity)
        scroll.addView(container)
        AlertDialog.Builder(activity)
            .setView(scroll)
            .setPositiveButton(GameLingoLanguages.ui(activity, "Hotovo"), null)
            .show()
    }
}
