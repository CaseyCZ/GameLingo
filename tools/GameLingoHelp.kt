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
        "tv" -> "GameLingo TV" to
            "1. Otevři GameLingo na telefonu.\n2. Zvol Google TV a vyber tuto televizi.\n3. Zadej šest číslic z obrazovky.\n\nTelefon a TV musí být ve stejné síti. Pokud používáš starý přijímač PlayCaption, zavři jej. Přijímač může zobrazit titulky přes HDMI obraz jen na podporovaném televizoru."
        "regions" -> "Oblasti" to
            "Modrá oblast čte původní titulky. Zelená určuje místo českých dialogů. Žlutá čte volby a herní texty, oranžová zobrazuje jejich překlad. Polohu a rozměry měň podle konkrétní hry. Oblasti se ukládají do profilů."
        "translation" -> "Překlad" to
            "OCR čte anglické texty ze hry. Samostatně lze zapnout dialogy, herní texty a volby. Při změně rychlosti nebo oblastí doporučujeme překlad nejdříve zastavit. Výchozí nastavení vychází z ověřeného základu PlayCaption."
        "profiles" -> "Profily her" to
            "Profil ukládá aktuální OCR oblasti, volby a nastavení překladu pro konkrétní hru. Údaje o účtech Sony a Microsoft se do profilů neukládají."
        "console" -> "Xbox a PlayStation" to
            "Pro Xbox se přihlašuješ svým účtem Microsoft a povolíš vzdálené funkce. Pro PS5/PS4 použij Remote Play a údaje svého účtu PlayStation. Párovací PIN z konzole se liší od hesla. Do hry ani do konzole se nic neinstaluje."
        "phone" -> "Překlad na telefonu" to
            "Zvol Standardní pro dialogy nebo Příběhový pro delší titulky. Pokud překládáš přes TV, telefon i TV musí být ve stejné síti. Změny zobrazení jsou k dispozici v Oblastech a Nastavení."
        else -> "GameLingo" to
            "Vyber Xbox nebo PlayStation, připoj zařízení a spusť překlad. Kam se české titulky zobrazí, nastavíš v Zařízení. Oblasti upravují místo čtení a zobrazení, Profily her ukládají vlastní konfiguraci."
    }

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
        contentDescription = "Otevřít nápovědu"
        setOnClickListener { show(activity, topic) }
    }

    fun show(activity: Activity, topic: String) {
        val (title, description) = info(topic)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(NAVY))
                cornerRadius = 24f
            }
        }
        container.addView(TextView(activity).apply {
            text = title
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(6, 0, 6, 14)
        })
        container.addView(TextView(activity).apply {
            text = description
            textSize = 16f
            setTextColor(Color.parseColor("#D8E2F0"))
            setLineSpacing(4f, 1.12f)
            setPadding(6, 0, 6, 12)
        })
        val scroll = ScrollView(activity)
        scroll.addView(container)
        AlertDialog.Builder(activity)
            .setView(scroll)
            .setPositiveButton("Hotovo", null)
            .show()
    }
}
