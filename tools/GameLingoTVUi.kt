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

    // Android TV already has an enlarged interface density. Match the
    // original compact 10-foot UI and preserve readable D-pad focus targets.
    private const val TV_SCALE = 0.66f
    fun dp(a: Activity, v: Int): Int =
        (a.resources.displayMetrics.density * v * TV_SCALE + .5f).toInt()

    fun panel(a: Activity, focus: Boolean = false): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                if (focus) Color.parseColor("#253D81") else Color.parseColor("#111A33"),
                if (focus) Color.parseColor("#321653") else Color.parseColor("#14112C")
            )
        ).apply {
            cornerRadius = dp(a, 22).toFloat()
            setStroke(dp(a, if (focus) 4 else 1).coerceAtLeast(1),
                if (focus) cyan else Color.parseColor("#644393"))
        }

    fun title(a: Activity, text: String, size: Float = 24f,
              muted: Boolean = false): TextView = TextView(a).apply {
        this.text = text
        textSize = size * TV_SCALE
        setTextColor(if (muted) dim else Color.WHITE)
        setTypeface(null, if (muted) Typeface.NORMAL else Typeface.BOLD)
    }

    fun isTV(a: Activity): Boolean = (a.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

    // Selected is a persistent checkmark; focus is an independent bright ring.
    // Do not depend on the manufacturer's AlertDialog row selector.
    fun select(a: Activity, heading: String, labels: Array<String>, selected: Int = -1, onSelect: (Int) -> Unit) {
        val dialog = Dialog(a)
        val body = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 24),dp(a, 16),dp(a, 24),dp(a, 16))
            background = android.graphics.drawable.ColorDrawable(Color.parseColor("#080E1B"))
        }
        body.addView(title(a, heading, 27f))
        val rows = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val buttons = labels.mapIndexed { index, label ->
            action(a, (if (index == selected) "✓  " else "    ") + label) {
                dialog.dismiss(); onSelect(index)
            }.apply {
                id = View.generateViewId()
                gravity = Gravity.CENTER_VERTICAL
                textSize = 16f
                minimumHeight = dp(a, 50)
                setPadding(dp(a, 14), dp(a, 7), dp(a, 14), dp(a, 7))
                setTextColor(if (index == selected) Color.parseColor("#57EBA3") else Color.WHITE)
                rows.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(a, 7) })
            }
        }
        val scroll = ScrollView(a).apply { addView(rows) }
        body.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            minOf(dp(a, 430), a.resources.displayMetrics.heightPixels * 55 / 100,
                dp(a, 63 * labels.size)).coerceAtLeast(dp(a, 54))))
        val back = action(a, "Zpět") { dialog.dismiss() }
        back.id = View.generateViewId()
        body.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(a, 12) })
        buttons.forEachIndexed { i, button ->
            button.nextFocusUpId = buttons.getOrNull(i-1)?.id ?: button.id
            button.nextFocusDownId = buttons.getOrNull(i+1)?.id ?: back.id
        }
        back.nextFocusUpId = buttons.lastOrNull()?.id ?: back.id
        dialog.setContentView(body)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((a.resources.displayMetrics.widthPixels * .48f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        (buttons.getOrNull(selected) ?: buttons.firstOrNull() ?: back).requestFocus()
    }

    /** Full-screen TV settings destination. Content may supply its own weighted scroll area. */
    fun page(a: Activity, heading: String, body: View, scroll: Boolean = true,
             helpTopic: String? = null, onClose: () -> Unit = {}): Dialog {
        val dialog = Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.pageBackground(a)
            setPadding(dp(a,32),dp(a,18),dp(a,32),dp(a,18))
        }
        val top = LinearLayout(a).apply { gravity=Gravity.CENTER_VERTICAL }
        top.addView(title(a,heading,29f),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        val back=action(a,"Zpět"){dialog.dismiss()}
        if(helpTopic!=null) top.addView(GameLingoHelp.button(a,helpTopic),GameLingoHelp.layout(a))
        top.addView(back)
        root.addView(top,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(a,14)})
        val content=if(scroll) ScrollView(a).apply{isFillViewport=true;addView(body)} else body
        root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(root)
        dialog.setOnDismissListener { onClose() }
        dialog.show()
        dialog.window?.setLayout(-1,-1)
        if(!body.requestFocus()) back.requestFocus()
        return dialog
    }

    fun controlFocus(a: Activity, view: View) {
        val old = view.background
        view.setOnFocusChangeListener { v, focused ->
            v.background = if (focused) panel(a, true) else old
        }
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
            setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12))
            background = panel(a)
            isFocusable = true
            isClickable = true
            minimumHeight = dp(a, 105)
            setOnClickListener { onActivate() }
            setOnFocusChangeListener { view, focused ->
                view.background = panel(a, focused)
                view.elevation = dp(a, if (focused) 12 else 2).toFloat()
                view.scaleX = if (focused) 1.015f else 1f
                view.scaleY = if (focused) 1.015f else 1f
            }
            contentDescription = heading + ". " + detail
        }
        if (symbol == "PS" || symbol == "ⓧ") {
            box.addView(ImageView(a).apply {
                setImageResource(if (symbol == "PS")
                    R.drawable.gamelingo_playstation else R.drawable.gamelingo_xbox)
                setColorFilter(Color.WHITE)
                adjustViewBounds = true
                contentDescription = if (symbol == "PS") "PlayStation" else "Xbox"
            }, LinearLayout.LayoutParams(dp(a, 34), dp(a, 34)))
        } else {
            box.addView(title(a, symbol, 25f).apply {
                setTextColor(cyan)
            })
        }
        box.addView(title(a, heading, 20f), LinearLayout.LayoutParams(
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
        textSize = 18f * TV_SCALE
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
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 26), dp(a, 17), dp(a, 26), dp(a, 18))
            background = GameLingoVisualKit.pageBackground(a)
        }
        val header = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title(a, "Nastavení GameLingo TV", 25f), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val close = action(a, "Zpět") { dialog.dismiss(); onUpdate() }
        close.id = View.generateViewId()
        header.addView(close)
        root.addView(header)

        val page = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(a, 4), 0, dp(a, 4), dp(a, 6))
        }
        // Every section uses the same three columns. Empty slots are layout
        // spacers, never focusable actions. A fixed header remains accessible.
        val columns = 3
        val focusRows = mutableListOf<List<LinearLayout>>()
        fun section(label: String) {
            page.addView(title(a, label, 16f).apply {
                setTextColor(cyan)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(a, 13)
                bottomMargin = dp(a, 8)
            })
        }
        fun settingsTile(symbol: String, heading: String, detail: String,
                         onActivate: () -> Unit): LinearLayout =
            tile(a, symbol, heading, detail, onActivate).apply {
                setPadding(dp(a, 13), dp(a, 8), dp(a, 13), dp(a, 8))
                minimumHeight = dp(a, 92)
                (getChildAt(0) as? TextView)?.textSize = 20f * TV_SCALE
                (getChildAt(1) as TextView).apply {
                    textSize = 19f * TV_SCALE
                    (layoutParams as LinearLayout.LayoutParams).topMargin = dp(a, 3)
                }
                (getChildAt(2) as TextView).apply {
                    (layoutParams as LinearLayout.LayoutParams).topMargin = dp(a, 3)
                }
            }

        fun addGrid(tiles: List<LinearLayout>) {
            tiles.chunked(columns).forEach { cards ->
                val row = LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                }
                repeat(columns) { column ->
                    val cell: View = cards.getOrNull(column)?.apply {
                        id = View.generateViewId()
                    } ?: View(a).apply { isFocusable = false }
                    row.addView(cell, LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (column < columns - 1) rightMargin = dp(a, 10)
                    })
                }
                page.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(a, 8)
                })
                focusRows.add(cards)
            }
        }

        fun languageTile(name: String, choices: List<GameLingoLanguages.Language>,
                         kind: GameLingoLanguages.Kind,
                         selected: () -> String): LinearLayout =
            settingsTile("◎", name, GameLingoLanguages.name(choices, selected())) {
                GameLingoLanguages.choose(a, kind) {
                    dialog.dismiss()
                    onUpdate()
                }
            }

        section("JAZYKY")
        addGrid(listOf(
            languageTile("Jazyk aplikace", GameLingoLanguages.appLanguages,
                GameLingoLanguages.Kind.APP) { GameLingoLanguages.app(a) },
            languageTile("Jazyk hry", GameLingoLanguages.gameLanguages,
                GameLingoLanguages.Kind.GAME) { GameLingoLanguages.game(a) },
            languageTile("Jazyk překladu", GameLingoLanguages.targetLanguages,
                GameLingoLanguages.Kind.TARGET) { GameLingoLanguages.target(a) }
        ))

        section("PŘEKLAD HRY")
        val userSettings = Nastaveni(a)
        fun toggle(title: String, detail: String,
                   get: () -> Boolean, set: (Boolean) -> Unit): LinearLayout {
            lateinit var choice: LinearLayout
            fun summary(): String = detail + "  ·  " +
                if (get()) "Zapnuto" else "Vypnuto"
            choice = settingsTile("◎", title, summary()) {
                set(!get())
                (choice.getChildAt(2) as TextView).text = summary()
                choice.contentDescription = title + ". " + summary()
            }
            return choice
        }
        addGrid(listOf(
            toggle("Dialogy", "Překlad mluvených dialogů",
                { userSettings.dialogy }, { userSettings.dialogy = it }),
            toggle("Herní texty", "Nápověda a úkoly v herním obraze",
                { userSettings.napovedy }, { userSettings.napovedy = it }),
            toggle("Překrýt původní titulky", "Přeložený text přímo přes originál",
                { userSettings.prekryvat }, { userSettings.prekryvat = it }),
            toggle("Příběhový režim", "Postupné čtení delších dialogů",
                { userSettings.pribehovy }, { userSettings.pribehovy = it })
        ))
        if (RemotePlayService.aktivni) {
            page.addView(title(a,
                "Změny nastavení překladu se projeví po novém spuštění.",
                14f, muted = true))
        }

        section("OCR A ROZPOZNÁVÁNÍ")
        GameLingoOCRConfig.load(a)
        addGrid(listOf(settingsTile("◎", "Režim OCR", GameLingoOCRConfig.title()) {
            if (RemotePlayService.aktivni) {
                android.app.AlertDialog.Builder(a).setMessage("Nejprve zastav překlad.")
                    .setPositiveButton("OK", null).show()
            } else {
                val keys = arrayOf("classic", "automatic", "street")
                val labels = arrayOf("Původní · stabilní", "Automatický · vyvážený", "Krátké dialogy · rychlý")
                android.app.AlertDialog.Builder(a).setTitle("Režim OCR")
                    .setSingleChoiceItems(labels, keys.indexOf(GameLingoOCRConfig.mode)) { picker, index ->
                        GameLingoOCRConfig.setMode(a, keys[index])
                        picker.dismiss()
                        dialog.dismiss()
                        onUpdate()
                    }.setNegativeButton("Zrušit", null).show()
            }
        }))
        page.addView(title(a, "Platí pro čtení přímo na TV. Při příjmu titulků z telefonu rozhoduje telefon.",
            14f, muted = true))
        page.addView(title(a, "Přímý překlad i překlad z telefonu používají internet nebo dříve uložené překlady.",
            14f, muted = true))

        section("ZOBRAZENÍ")
        val overlay = settingsTile("▣", "Titulky přes obraz hry",
            if (Settings.canDrawOverlays(a)) "Povoleno" else
                "Vyžaduje povolení překryvu") {
            if (!Settings.canDrawOverlays(a)) {
                a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + a.packageName)))
            }
        }
        addGrid(listOf(overlay))
        page.addView(title(a,
            "Pro čtení her přímo v TV potřebuje aplikace povolení překryvu.",
            14f, muted = true))

        section("ZABEZPEČENÍ")
        addGrid(listOf(toggle("Legacy · staré aplikace",
            "Povolí nepodepsané titulky (jen důvěryhodná síť)",
            { GameLingoTVPairing.legacyEnabled(a) },
            { GameLingoTVPairing.setLegacyEnabled(a, it) })))

        section("DALŠÍ")
        addGrid(listOf(settingsTile("文", "Herní slovník", "Vlastní výrazy pro přímé OCR") {
            GameLingoGlossaryUi.show(a)
        }, settingsTile("◷", "Historie překladů", "Překlady zpracované přímo na TV") {
            GameLingoHistoryUi.show(a)
        }, settingsTile("▣", "Překladová cache", "Ukládání a vymazání místních překladů") {
            GameLingoCachePrivacyUi.show(a)
        }, settingsTile("ⓘ", "O aplikaci / Licence",
            "Původní autoři, zdrojový kód a GNU AGPLv3") {
            GameLingoAbout.show(a)
        }))

        // Explicit D-pad destinations also handle incomplete rows and
        // prevent focus from moving into a spacer or leaving the dialog.
        focusRows.forEachIndexed { rowIndex, cards ->
            cards.forEachIndexed { column, card ->
                card.nextFocusLeftId = cards.getOrNull(column - 1)?.id ?: card.id
                card.nextFocusRightId = cards.getOrNull(column + 1)?.id ?: card.id
                val above = focusRows.getOrNull(rowIndex - 1)
                val below = focusRows.getOrNull(rowIndex + 1)
                card.nextFocusUpId = above?.get(column.coerceAtMost(above.lastIndex))?.id
                    ?: close.id
                card.nextFocusDownId = below?.get(column.coerceAtMost(below.lastIndex))?.id
                    ?: card.id
            }
        }
        close.nextFocusDownId = focusRows.first().last().id
        close.nextFocusUpId = close.id
        close.nextFocusLeftId = close.id
        close.nextFocusRightId = close.id

        val scroll = ScrollView(a).apply {
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            addView(page)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        page.post { focusRows.first().first().requestFocus() }
    }
}
