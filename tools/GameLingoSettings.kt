package cz.titulky.tv

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** GameLingo native Android settings, editing real PlayStation/Xbox OCR settings. */
object GameLingoSettings {
    private val bg = Color.parseColor("#070B14")
    private val card = Color.parseColor("#111827")
    private val accent = Color.parseColor("#38BDF8")
    private val muted = Color.parseColor("#94A3B8")
    private fun rounded(color: Int, radius: Float = 17f) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun row(c: Activity, title: String, detail: String, state: Boolean,
                    update: (Boolean) -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(card)
        setPadding(17, 13, 17, 13)
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(c).apply {
                text = title; textSize = 17f
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            })

        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(c).apply {
            isChecked = state
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnCheckedChangeListener { _, checked -> update(checked) }
        })
    }

    private fun showTranslation(activity: Activity, settings: Nastaveni,
             onAreas: () -> Unit, onLanguage: () -> Unit) {
        Volby.nacti(activity)
        OblastTitulku.nacti(activity)
        val dialog = Dialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = rounded(bg)
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(activity).apply {
            text = "Překlad"; textSize = 25f
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(GameLingoHelp.button(activity, "translation"),
            LinearLayout.LayoutParams(54, 54))
        content.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 18 })
        fun add(r: LinearLayout) {
            content.addView(r, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 })
        }
        add(row(activity, "Překládat dialogy", "České titulky pro rozhovory ve hrách.",
            settings.dialogy) { settings.dialogy = it })
        add(row(activity, "Herní texty a volby", "Samostatná žlutá oblast OCR.",
            Volby.zapnuto) { Volby.zapnuto = it; Volby.uloz(activity) })
        add(row(activity, "Dopisy a zprávy", "Překládat celý text místo seznamu voleb.",
            Volby.rezimText) { Volby.rezimText = it; Volby.uloz(activity) })
        add(row(activity, "Příběhový režim", "Delší přeložené dialogy.",
            settings.pribehovy) { settings.pribehovy = it })
        add(row(activity, "Ruční OCR oblast", "Vlastní modrý rámeček.",
            OblastTitulku.rucne) {
                OblastTitulku.rucne = it
                OblastTitulku.uloz(activity)
            })

        val sliderCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card)
            setPadding(17,14,17,16)
        }
        val label = TextView(activity).apply {
            textSize = 16f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        fun refresh() {
            label.text = "Doba zobrazení: " + settings.casPribehuProcenta + " %"
        }
        refresh()
        sliderCard.addView(label)
        sliderCard.addView(SeekBar(activity).apply {
            max = CasovaniPribehu.MAX_PROCENT - CasovaniPribehu.MIN_PROCENT
            progress = settings.casPribehuProcenta - CasovaniPribehu.MIN_PROCENT
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        settings.casPribehuProcenta = CasovaniPribehu.MIN_PROCENT + value
                        refresh()
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        })
        add(sliderCard)

        fun button(title: String, action: () -> Unit) {
            content.addView(TextView(activity).apply {
                text = title; textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                minHeight = 54
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#7150E5"),
                        Color.parseColor("#13B6EE"))).apply {
                    cornerRadius = 15f
                }
                setPadding(13,15,13,15)
                isFocusable = true
                setOnClickListener { dialog.dismiss(); action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 9 })
        }
        button("▣  Oblasti na obrazovce") { onAreas() }
        button("🌐  Jazyk překladu") { onLanguage() }
        button("Hotovo") {}

        val scroll = ScrollView(activity)
        scroll.addView(content)
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.93f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    /**
     * Exactly the same menu hierarchy as iOS:
     * Jazyky, OCR, Překlad, Oblasti, Titulky, Profily, Zařízení, O aplikaci.
     * Every destination invokes an existing functional screen.
     */
    fun show(activity: Activity, settings: Nastaveni,
             onAreas: () -> Unit, onLanguage: () -> Unit,
             onDevices: () -> Unit = {}) {
        if (activity.isFinishing) return
        val d = GameLingoVisualKit.dp(activity, 1)
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20 * d, 15 * d, 20 * d, 24 * d)
            background = GameLingoVisualKit.pageBackground(activity)
        }

        val heading = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(TextView(activity).apply {
            text = "Nastavení"
            textSize = 29f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(TextView(activity).apply {
            text = "✕"
            textSize = 23f
            gravity = Gravity.CENTER
            contentDescription = "Zavřít nastavení"
            setTextColor(Color.WHITE)
            background = GameLingoVisualKit.card(activity)
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(48 * d, 48 * d))
        page.addView(heading)

        fun section(title: String) {
            page.addView(TextView(activity).apply {
                text = title
                textSize = 12f
                letterSpacing = 0.10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#02D6FF"))
                setPadding(3 * d, 23 * d, 0, 12 * d)
            })
        }
        fun menu(symbol: String, title: String, sub: String, open: () -> Unit) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GameLingoVisualKit.card(activity)
                setPadding(14 * d, 11 * d, 14 * d, 11 * d)
                isFocusable = true
                isClickable = true
                setOnClickListener { dialog.dismiss(); open() }
            }
            row.addView(TextView(activity).apply {
                text = symbol
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#02D6FF"))
                background = GameLingoVisualKit.primary(activity)
            }, LinearLayout.LayoutParams(45 * d, 45 * d).apply {
                rightMargin = 13 * d
            })
            row.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = title
                    textSize = 16.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(activity).apply {
                    text = sub
                    textSize = 12f
                    setTextColor(Color.parseColor("#92A4C7"))
                    setPadding(0, 4 * d, 0, 0)
                })
            }, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(activity).apply {
                text = "›"
                textSize = 24f
                setTextColor(Color.parseColor("#92A4C7"))
            })
            page.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 9 * d
            })
        }
        section("JAZYKY")
        menu("◎", "Jazyk hry a překladu",
            GameLingoLanguages.name(GameLingoLanguages.gameLanguages,
                GameLingoLanguages.game(activity)) + " → " +
            GameLingoLanguages.name(GameLingoLanguages.targetLanguages,
                GameLingoLanguages.target(activity))) {
            GameLingoLanguages.show(activity)
        }

        section("ROZPOZNÁVÁNÍ A PŘEKLAD")
        menu("▣", "OCR a rozpoznávání",
             "Čtyři oblasti a přesnost snímání") { onAreas() }
        menu("文", "Překlad", "Režimy, dialogy a časování") {
            showTranslation(activity, settings, onAreas, onLanguage)
        }
        menu("⌗", "Oblasti obrazovky",
             "Dialogy, překlad a herní texty") { onAreas() }

        section("ZOBRAZENÍ A DALŠÍ VOLBY")
        menu("Aa", "Vzhled titulků",
             "Velikost, poloha a výstupní oblasti") { onAreas() }
        menu("♧", "Profily her",
             "Vlastní nastavení pro každou hru") {
            GameLingoProfiles.show(activity, settings) {}
        }
        menu("▭", "Zařízení",
             "Xbox, PlayStation a TV") { onDevices() }
        menu("ⓘ", "O aplikaci / Licence",
             "GameLingo, zdroje a AGPLv3") {
            GameLingoAbout.show(activity)
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(page)
        }
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

}
