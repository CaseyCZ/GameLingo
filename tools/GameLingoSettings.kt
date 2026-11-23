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

    fun show(activity: Activity, settings: Nastaveni,
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
}
