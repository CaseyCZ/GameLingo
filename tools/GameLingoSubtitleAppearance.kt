package cz.titulky.tv

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * GameLingo subtitle appearance editor for the selected phone/TV output.
 * Uses the SAME tvPismo/tvSirka/tvX/tvY preferences and live transmitter as
 * the working TelefonActivity layout editor. No duplicate, decorative state.
 */
object GameLingoSubtitleAppearance {
    fun show(activity: Activity) {
        if (activity.isFinishing) return
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
        val prefs = TvProfily.nastaveni(activity)
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val dp: (Int) -> Int = { GameLingoVisualKit.dp(activity, it) }
        val muted = Color.parseColor("#92A4C7")
        val cyan = Color.parseColor("#02D6FF")
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.pageBackground(activity)
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }

        fun text(label: String, size: Float = 15f, color: Int = Color.WHITE,
                 bold: Boolean = false): TextView = TextView(activity).apply {
            gravity = android.view.Gravity.CENTER
            this.text = tr(label)
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        fun addCard(child: LinearLayout) {
            layout.addView(child, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("Vzhled titulků", 27f, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(GameLingoHelp.button(activity,"appearance"),GameLingoHelp.layout(activity,dp(36),dp(44)))
        header.addView(TextView(activity).apply {
            this.text = "✕"
            contentDescription = tr("Zavřít vzhled titulků")
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GameLingoVisualKit.card(activity)
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        layout.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(18) })
        layout.addView(text(
            "Vlastní vzhled titulků pro telefon a nastavení zelené oblasti na TV. Přesná poloha rámečků zůstává v Oblastech obrazovky.",
            14f, muted
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })

        val preview = text("", 18f, bold = true).apply {
            this.text = GameLingoPhoneSubtitleStyle.sample(activity)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(16), dp(14), dp(16))
            background = GameLingoVisualKit.card(activity, true)
        }
        val previewCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.card(activity)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(text("NÁHLED", 12f, cyan, true))
            addView(GameLingoVisualKit.captionPreview(activity,preview), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })
        }
        addCard(previewCard)

        var font = prefs.getFloat("tvPismo", 1f).coerceIn(0.70f, 1.80f)
        var width = prefs.getFloat("tvSirka", 0.62f).coerceIn(0.30f, 0.95f)
        val sliders = mutableListOf<SeekBar>()
        fun publish() {
            prefs.edit().putFloat("tvPismo", font).putFloat("tvSirka", width).apply()
            val area = floatArrayOf(
                prefs.getFloat("tvX", 0.5f), prefs.getFloat("tvY", 0.93f),
                width, font
            )
            Propojeni.Vysilac.nastavRozvrh(area)
        }
        fun scalePreview() {
            preview.textSize = (18f * GameLingoPhoneSubtitleStyle.scale(activity))
                .coerceIn(12f, 39f)
            GameLingoPhoneSubtitleStyle.apply(activity, preview)
        }
        fun slider(label: String, min: Int, max: Int, initial: Int,
                   forTelevision: Boolean = false, onChanged: (Int) -> Unit) {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = GameLingoVisualKit.card(activity)
                setPadding(dp(16), dp(15), dp(16), dp(12))
            }
            val number = text("", 16f, bold = true)
            fun refresh(value: Int) { number.text = "${tr(label)}: $value %" }
            refresh(initial)
            card.addView(number)
            val bar = SeekBar(activity).apply {
                this.max = max - min
                progress = initial - min
                progressTintList = ColorStateList.valueOf(cyan)
                thumbTintList = ColorStateList.valueOf(cyan)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val actual = min + progress
                        onChanged(actual)
                        refresh(actual)
                        scalePreview()
                        if (forTelevision) publish()
                    }
                    override fun onStartTrackingTouch(seek: SeekBar) {}
                    override fun onStopTrackingTouch(seek: SeekBar) {}
                })
            }
            if (forTelevision) sliders.add(bar)
            card.addView(bar)
            addCard(card)
        }

        layout.addView(text("TITULKY NA TELEFONU", 14f, cyan, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
        slider("Velikost písma", 70, 180,
            (GameLingoPhoneSubtitleStyle.scale(activity) * 100).toInt()) {
            GameLingoPhoneSubtitleStyle.setScale(activity, it / 100f)
        }

        val phoneColors = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GameLingoVisualKit.card(activity)
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        val colorButtons = mutableListOf<Pair<String, TextView>>()
        GameLingoPhoneSubtitleStyle.colors.forEach { choice ->
            val swatch = TextView(activity).apply {
                text = "●"
                textSize = 29f
                gravity = Gravity.CENTER
                contentDescription = tr("Barva textu") + ": " + tr(when (choice) {
                    "yellow" -> "Žlutá"
                    "cyan" -> "Tyrkysová"
                    "green" -> "Zelená"
                    "purple" -> "Fialová"
                    else -> "Bílá"
                })
                setTextColor(GameLingoPhoneSubtitleStyle.textColor(choice))
                background = GameLingoVisualKit.card(activity,
                    GameLingoPhoneSubtitleStyle.color(activity) == choice)
                setOnClickListener {
                    GameLingoPhoneSubtitleStyle.setColor(activity, choice)
                    colorButtons.forEach { (key, button) ->
                        button.background = GameLingoVisualKit.card(activity, key == choice)
                        button.isSelected = key == choice
                    }
                    scalePreview()
                }
            }
            colorButtons.add(choice to swatch)
            phoneColors.addView(swatch, LinearLayout.LayoutParams(
                0, dp(55), 1f
            ).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }
        layout.addView(text("Barva textu", 16f, bold = true))
        addCard(phoneColors)

        val backgrounds = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GameLingoVisualKit.card(activity)
            setPadding(dp(6), dp(12), dp(6), dp(12))
        }
        val backgroundButtons = mutableListOf<Pair<String, TextView>>()
        GameLingoPhoneSubtitleStyle.backgrounds.forEach { choice ->
            val button = TextView(activity).apply {
                text = tr(when (choice) {
                    "dark" -> "Tmavé"
                    "translucent" -> "Průhledné"
                    else -> "Žádné"
                })
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(Color.WHITE)
                contentDescription = tr("Pozadí") + ": " + text
                isSelected = GameLingoPhoneSubtitleStyle.background(activity) == choice
                background = GameLingoVisualKit.card(activity, isSelected)
                setOnClickListener {
                    GameLingoPhoneSubtitleStyle.setBackground(activity, choice)
                    backgroundButtons.forEach { (key, chip) ->
                        chip.isSelected = key == choice
                        chip.background = GameLingoVisualKit.card(activity, chip.isSelected)
                    }
                    scalePreview()
                }
            }
            backgroundButtons.add(choice to button)
            backgrounds.addView(button, LinearLayout.LayoutParams(
                0, dp(54), 1f
            ).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        layout.addView(text("Pozadí", 16f, bold = true))
        addCard(backgrounds)

        layout.addView(text("TITULKY NA TV", 14f, cyan, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(12) })
        slider("Velikost písma TV", 70, 180, (font * 100).toInt(),
            forTelevision = true) {
            font = it / 100f
        }
        slider("Šířka titulků", 30, 95, (width * 100).toInt(),
            forTelevision = true) {
            width = it / 100f
        }

        val locked = prefs.getBoolean("rozvrhZamek", false)
        val lockCard = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GameLingoVisualKit.card(activity)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        lockCard.addView(text("Zamknout polohu a vzhled", 16f, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        lockCard.addView(Switch(activity).apply {
            isChecked = locked
            contentDescription = tr("Zamknout polohu a vzhled titulků")
            thumbTintList = ColorStateList.valueOf(cyan)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("rozvrhZamek", checked).apply()
                sliders.forEach { it.isEnabled = !checked }
            }
        })
        addCard(lockCard)
        sliders.forEach { it.isEnabled = !locked }
        scalePreview()

        // TV preview is available only for the actively selected target,
        // not merely for a saved/discovered receiver. Sending does not assert
        // that the TV actually received or displayed the subtitle.
        val tvStore = TvProfily(activity)
        val selectedTv = tvStore.posledniId?.let { tvStore.najdi(it) }
        val senderIp = Propojeni.Vysilac.cilIp
        val tvSelected = !tvStore.pouzeTelefon && selectedTv != null &&
            senderIp != null && senderIp == selectedTv.ip
        layout.addView(TextView(activity).apply {
            text = tr("Náhled na TV")
            contentDescription = tr("Odeslat ukázkový překlad do vybrané TV")
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GameLingoVisualKit.primary(activity)
            minHeight = dp(54)
            isEnabled = tvSelected
            alpha = if (tvSelected) 1f else 0.45f
            setOnClickListener {
                if (tvSelected && Propojeni.Vysilac.cilIp == selectedTv?.ip)
                    Propojeni.Vysilac.ukazka(GameLingoPhoneSubtitleStyle.sample(activity))
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { topMargin = dp(8); bottomMargin = dp(12) })

        layout.addView(text(
            "Změny se ukládají pro aktuálně vybranou televizi nebo telefon. Pro přesné umístění rámečků otevři Oblasti obrazovky.",
            13f, muted
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(5); bottomMargin = dp(17) })
        layout.addView(TextView(activity).apply {
            text = tr("Hotovo")
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GameLingoVisualKit.primary(activity)
            minHeight = dp(54)
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ))

        GameLingoTVUi.mountPage(activity, dialog, tr("Vzhled titulků"), layout, helpTopic = "appearance")
    }
}
