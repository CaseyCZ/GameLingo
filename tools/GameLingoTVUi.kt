package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * GameLingo TV's original ten-foot navigation and settings.
 * Deliberately uses large focus targets and no touch-dependent gestures.
 * Remote Play, signed subtitle reception, and console credentials are owned by
 * their existing services, not by any fake state in the UI.
 */
object GameLingoTVUi {
    private val ink = Color.parseColor("#070B19")
    private val cyan = Color.parseColor("#02D6FF")
    private val blue = Color.parseColor("#3557FF")
    private val purple = Color.parseColor("#A329FA")
    private val dim = Color.parseColor("#9FB1D4")

    fun dp(a: Activity, v: Int): Int =
        (a.resources.displayMetrics.density * v + .5f).toInt()

    fun panel(a: Activity, focus: Boolean = false): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                if (focus) Color.parseColor("#253D81") else Color.parseColor("#111A33"),
                if (focus) Color.parseColor("#321653") else Color.parseColor("#14112C")
            )
        ).apply {
            cornerRadius = dp(a, 22).toFloat()
            setStroke(dp(a, if (focus) 3 else 1),
                if (focus) cyan else Color.parseColor("#644393"))
        }

    fun title(a: Activity, text: String, size: Float = 24f,
              muted: Boolean = false): TextView = TextView(a).apply {
        this.text = text
        textSize = size
        setTextColor(if (muted) dim else Color.WHITE)
        setTypeface(null, if (muted) Typeface.NORMAL else Typeface.BOLD)
    }

    fun brand(a: Activity): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(a).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            adjustViewBounds = true
            contentDescription = "GameLingo TV"
        }, LinearLayout.LayoutParams(dp(a, 74), dp(a, 74)).apply {
            rightMargin = dp(a, 15)
        })
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(title(a, "GameLingo", 35f))
            addView(title(a, "TV · Překlad herních dialogů", 16f, muted = true))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /**
     * This card is reachable with arrows and activated by OK. Focus is drawn
     * outside its panel with the same accent on every Google/Android TV device.
     */
    fun tile(a: Activity, symbol: String, heading: String, detail: String,
             onActivate: () -> Unit): LinearLayout {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(a, 23), dp(a, 21), dp(a, 23), dp(a, 21))
            background = panel(a)
            isFocusable = true
            isClickable = true
            minimumHeight = dp(a, 136)
            setOnClickListener { onActivate() }
            setOnFocusChangeListener { view, focused ->
                view.background = panel(a, focused)
                view.elevation = dp(a, if (focused) 12 else 2).toFloat()
                view.scaleX = if (focused) 1.025f else 1f
                view.scaleY = if (focused) 1.025f else 1f
            }
            contentDescription = heading + ". " + detail
        }
        box.addView(title(a, symbol, 29f).apply {
            setTextColor(cyan)
        })
        box.addView(title(a, heading, 22f), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(a, 7)
        })
        box.addView(title(a, detail, 15f, muted = true).apply {
            maxLines = 3
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(a, 5)
        })
        return box
    }

    fun action(a: Activity, label: String, primary: Boolean = false,
               onActivate: () -> Unit): TextView = TextView(a).apply {
        text = label
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        minimumHeight = dp(a, 59)
        setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 12))
        background = if (primary) GameLingoVisualKit.primary(a) else panel(a)
        isClickable = true
        isFocusable = true
        setOnClickListener { onActivate() }
        setOnFocusChangeListener { view, hasFocus ->
            view.background = if (primary && !hasFocus)
                GameLingoVisualKit.primary(a) else panel(a, hasFocus)
            view.elevation = dp(a, if (hasFocus) 10 else 0).toFloat()
        }
    }

    fun statusPill(a: Activity, label: String,
                   detail: String, ready: Boolean): LinearLayout =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = panel(a)
            setPadding(dp(a, 15), dp(a, 12), dp(a, 15), dp(a, 12))
            addView(title(a, if (ready) "●" else "○", 21f).apply {
                setTextColor(if (ready) Color.parseColor("#35E57D") else dim)
            }, LinearLayout.LayoutParams(dp(a, 30), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(title(a, label, 16f))
                addView(title(a, detail, 13f, muted = true))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    /**
     * First launch is a real GameLingo welcome, not PlayCaption's beta or
     * legacy language list. Source and target are saved independently from UI.
     */
    fun showWelcome(a: Activity, onFinish: (String) -> Unit) {
        if (a.isFinishing) return
        val dialog = Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(a, 48), dp(a, 28), dp(a, 48), dp(a, 28))
            background = GameLingoVisualKit.pageBackground(a)
        }
        root.addView(brand(a), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(a, 27)
        })
        root.addView(title(a, "Vítej v GameLingo TV", 30f),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(a, 10)
            })
        root.addView(title(a,
            "Vyber jazyky. Potom můžeš překládat z telefonu nebo přímo na televizi.",
            17f, muted = true), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(a, 20)
        })
        fun select(name: String, choices: List<GameLingoLanguages.Language>,
                   current: () -> String, kind: GameLingoLanguages.Kind) {
            val button = action(a,
                name + "  ·  " + GameLingoLanguages.name(choices, current())) {
                GameLingoLanguages.choose(a, kind) {
                    dialog.dismiss()
                    showWelcome(a, onFinish)
                }
            }
            root.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(a, 13)
            })
        }
        select("Jazyk aplikace", GameLingoLanguages.appLanguages,
            { GameLingoLanguages.app(a) }, GameLingoLanguages.Kind.APP)
        select("Jazyk hry", GameLingoLanguages.gameLanguages,
            { GameLingoLanguages.game(a) }, GameLingoLanguages.Kind.GAME)
        select("Jazyk překladu", GameLingoLanguages.targetLanguages,
            { GameLingoLanguages.target(a) }, GameLingoLanguages.Kind.TARGET)

        val begin = action(a, "Pokračovat na TV", primary = true) {
            dialog.dismiss()
            onFinish(GameLingoLanguages.app(a))
        }
        root.addView(begin, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(a, 15)
        })
        val scroll = ScrollView(a).apply {
            isFillViewport = true
            addView(root)
        }
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        begin.requestFocus()
    }

    fun showSettings(a: Activity, onUpdate: () -> Unit) {
        if (a.isFinishing) return
        val dialog = Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val page = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 40), dp(a, 26), dp(a, 40), dp(a, 30))
            background = GameLingoVisualKit.pageBackground(a)
        }
        val header = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title(a, "Nastavení GameLingo TV", 30f), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val close = action(a, "Zpět") { dialog.dismiss(); onUpdate() }
        header.addView(close)
        page.addView(header)
        page.addView(title(a, "JAZYKY", 16f).apply {
            setTextColor(cyan)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(a, 28)
            bottomMargin = dp(a, 13)
        })

        fun languageRow(name: String, choices: List<GameLingoLanguages.Language>,
                        kind: GameLingoLanguages.Kind,
                        selected: () -> String) {
            val row = tile(a, "◎", name,
                GameLingoLanguages.name(choices, selected())) {
                GameLingoLanguages.choose(a, kind) {
                    dialog.dismiss()
                    onUpdate()
                }
            }
            page.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(a, 11)
            })
            if (kind == GameLingoLanguages.Kind.APP) row.requestFocus()
        }
        languageRow("Jazyk aplikace", GameLingoLanguages.appLanguages,
            GameLingoLanguages.Kind.APP) { GameLingoLanguages.app(a) }
        languageRow("Jazyk hry", GameLingoLanguages.gameLanguages,
            GameLingoLanguages.Kind.GAME) { GameLingoLanguages.game(a) }
        languageRow("Jazyk překladu", GameLingoLanguages.targetLanguages,
            GameLingoLanguages.Kind.TARGET) { GameLingoLanguages.target(a) }

        page.addView(title(a, "ZOBRAZENÍ", 16f).apply {
            setTextColor(cyan)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(a, 15)
            bottomMargin = dp(a, 12)
        })
        val overlay = tile(a, "▣", "Titulky přes obraz hry",
            if (Settings.canDrawOverlays(a)) "Povoleno" else
                "Vyžaduje povolení překryvu") {
            if (!Settings.canDrawOverlays(a)) {
                a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + a.packageName)))
            }
        }
        page.addView(overlay)
        page.addView(title(a,
            "Pro čtení her přímo v TV potřebuje aplikace povolení překryvu.",
            14f, muted = true), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(a, 11)
        })
        val scroll = ScrollView(a).apply {
            isFillViewport = true
            addView(page)
        }
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        page.post { close.requestFocus() }
    }
}
