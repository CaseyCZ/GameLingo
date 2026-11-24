package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Real OCR settings in the same iOS order: mode, menu filtering,
 * extended detection, manual-area selector, defaults. Each change goes to
 * GameLingoOCRConfig or the existing persistent subtitle region store.
 */
object GameLingoOCRSettings {
    fun show(activity: Activity, onAreas: () -> Unit) {
        if (activity.isFinishing) return
        GameLingoOCRConfig.load(activity)
        OblastTitulku.nacti(activity)
        val canEdit = !RemotePlayService.aktivni
        val d: (Int) -> Int = { GameLingoVisualKit.dp(activity, it) }
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.pageBackground(activity)
            setPadding(d(20), d(20), d(20), d(28))
        }
        fun text(value: String, size: Float = 15f, bold: Boolean = false): TextView =
            TextView(activity).apply {
                this.text = value
                textSize = size
                setTextColor(Color.WHITE)
                if (bold) typeface = Typeface.DEFAULT_BOLD
            }
        fun add(view: android.view.View) {
            root.addView(view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = d(12) })
        }
        add(text("OCR a rozpoznávání", 26f, true))
        add(text("Rychlost čtení, filtrování menu a skutečná oblast snímání. " +
            "Během spuštěné relace nejprve zastav překlad.", 14f))
        fun action(title: String, detail: String, enabled: Boolean = true,
                   run: () -> Unit) {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = GameLingoVisualKit.card(activity)
                setPadding(d(16), d(15), d(16), d(15))
                minimumHeight = d(67)
                isClickable = enabled
                isFocusable = enabled
                if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, this)
                alpha = if (enabled) 1f else 0.5f
                setOnClickListener { if (enabled) run() }
            }
            card.addView(text(title, 17f, true))
            card.addView(text(detail, 13f).apply {
                setTextColor(Color.parseColor(GameLingoUIContract.Palette.muted))
                setPadding(0, d(4), 0, 0)
            })
            add(card)
        }
        fun toggle(title: String, caption: String, active: Boolean,
                   available: Boolean = canEdit,
                   changed: (Boolean) -> Unit) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GameLingoVisualKit.card(activity)
                setPadding(d(15), d(13), d(15), d(13))
                alpha = if (available) 1f else 0.5f
            }
            val words = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 16f, true))
                addView(text(caption, 13f).apply {
                    setTextColor(Color.parseColor(GameLingoUIContract.Palette.muted))
                })
            }
            row.addView(words, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Switch(activity).apply {
                isChecked = active
                isEnabled = available
                isFocusable = available
                if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, this)
                setOnCheckedChangeListener { _, value -> if (available) changed(value) }
            })
            add(row)
        }
        val title = when (GameLingoOCRConfig.mode) {
            "automatic" -> "Automatický · vyvážený"
            "street" -> "Krátké dialogy · rychlý"
            else -> "Původní · stabilní"
        }
        action("Režim OCR", title, canEdit) {
            val labels = arrayOf(
                "Původní · stabilní",
                "Automatický · vyvážený",
                "Krátké dialogy · rychlý"
            )
            val keys = arrayOf("classic", "automatic", "street")
            if (GameLingoTVUi.isTV(activity)) {
                dialog.dismiss()
                GameLingoTVUi.select(activity, "Režim OCR", labels, keys.indexOf(GameLingoOCRConfig.mode)) { index ->
                    GameLingoOCRConfig.setMode(activity, keys[index])
                    show(activity, onAreas)
                }
                return@action
            }
            AlertDialog.Builder(activity)
                .setTitle("Režim OCR")
                .setSingleChoiceItems(labels, keys.indexOf(GameLingoOCRConfig.mode)) {
                    picker, index ->
                    GameLingoOCRConfig.setMode(activity, keys[index])
                    picker.dismiss()
                    dialog.dismiss()
                    show(activity, onAreas)
                }.setNegativeButton("Zrušit", null).show()
        }
        toggle("Filtrovat menu hry",
            "Omezí velké nadpisy a tlačítka hry.",
            GameLingoOCRConfig.filterMenuText) {
            GameLingoOCRConfig.setFilterMenu(activity, it)
        }
        toggle("Rozšířit hledání",
            "Při automatickém režimu zahrne širší část obrazu.",
            GameLingoOCRConfig.expandSearch,
            available = canEdit && GameLingoOCRConfig.mode != "classic" &&
                !OblastTitulku.rucne) {
            GameLingoOCRConfig.setExpandSearch(activity, it)
        }
        toggle("Ruční oblast OCR",
            "Použít modrý rámeček místo automatického výběru.",
            OblastTitulku.rucne) {
            OblastTitulku.rucne = it
            OblastTitulku.uloz(activity)
            dialog.dismiss()
            show(activity, onAreas)
        }
        action("Oblasti obrazovky",
            "Upravit modrý, zelený, žlutý a oranžový rámeček.") {
            dialog.dismiss()
            onAreas()
        }
        action("Výchozí nastavení OCR",
            "Obnovit původní režim, filtrování a automatické čtení.",
            canEdit) {
            GameLingoOCRConfig.reset(activity)
            OblastTitulku.rucne = false
            OblastTitulku.oblast = OblastTitulku.VYCHOZI.copyOf()
            OblastTitulku.uloz(activity)
            dialog.dismiss()
            show(activity, onAreas)
        }
        action("Hotovo", "Zpět do Nastavení") { dialog.dismiss() }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(root)
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
