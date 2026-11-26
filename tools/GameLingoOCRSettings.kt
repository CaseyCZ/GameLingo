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
    fun show(activity: Activity, onAreas: () -> Unit) = showPage(activity, onAreas, false)

    private fun showPage(activity: Activity, onAreas: () -> Unit, advanced: Boolean) {
        if (activity.isFinishing) return
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
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
                gravity = android.view.Gravity.CENTER
                this.text = tr(value)
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
        if(GameLingoTVUi.isTV(activity)) {
            add(LinearLayout(activity).apply {
                gravity=Gravity.CENTER_VERTICAL
                addView(text("OCR a rozpoznávání",26f,true),LinearLayout.LayoutParams(0,-2,1f))
                addView(GameLingoHelp.button(activity,"ocr"),GameLingoHelp.layout(activity))
            })
        } else add(text("OCR a rozpoznávání", 26f, true))
        add(text("Rychlost čtení, filtrování menu a skutečná oblast snímání. Během spuštěné relace nejprve zastav překlad.", 14f))
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
            val television=GameLingoTVUi.isTV(activity)
            val switch=Switch(activity).apply {
                isChecked = active
                isEnabled = available
                isFocusable = available && !television
                setOnCheckedChangeListener { _, value -> if (available) changed(value) }
            }
            row.addView(switch)
            if(television) {
                row.isFocusable=available; row.isClickable=available
                row.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
                GameLingoTVUi.controlFocus(activity,row)
                row.setOnClickListener { if(available) switch.isChecked=!switch.isChecked }
            }
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
            ).map(::tr).toTypedArray()
            val keys = arrayOf("classic", "automatic", "street")
            if (GameLingoTVUi.isTV(activity)) {
                GameLingoTVUi.select(activity, tr("Režim OCR"), labels, keys.indexOf(GameLingoOCRConfig.mode)) { index ->
                    GameLingoOCRConfig.setMode(activity, keys[index])
                    dialog.dismiss()
                    showPage(activity, onAreas, advanced)
                }
                return@action
            }
            AlertDialog.Builder(activity)
                .setTitle(tr("Režim OCR"))
                .setSingleChoiceItems(labels, keys.indexOf(GameLingoOCRConfig.mode)) {
                    picker, index ->
                    GameLingoOCRConfig.setMode(activity, keys[index])
                    picker.dismiss()
                    dialog.dismiss()
                    showPage(activity, onAreas, advanced)
                }.setNegativeButton(tr("Zrušit"), null).show()
        }
        val yellowModes = arrayOf("follow", "classic", "automatic", "street")
        val yellowLabels = arrayOf("Stejný jako modrá", "Původní · stabilní", "Automatický · vyvážený", "Krátké dialogy · rychlý").map(::tr).toTypedArray()
        val yellowModeIndex = yellowModes.indexOf(GameLingoOCRConfig.yellowOCRMode).coerceAtLeast(0)
        action("Žlutá: režim OCR", yellowLabels[yellowModeIndex], canEdit) {
            fun save(index: Int) {
                GameLingoOCRConfig.setYellowOCRMode(activity, yellowModes[index])
                dialog.dismiss(); showPage(activity, onAreas, advanced)
            }
            if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.select(activity, tr("Žlutá: režim OCR"), yellowLabels, yellowModeIndex, ::save)
            else android.app.AlertDialog.Builder(activity).setTitle(tr("Žlutá: režim OCR"))
                .setSingleChoiceItems(yellowLabels, yellowModeIndex) { chooser, index -> chooser.dismiss(); save(index) }
                .setNegativeButton(tr("Zrušit"), null).show()
        }
        toggle("Filtrovat menu hry",
            "Modrá oblast: omezí velké nadpisy, tlačítka a krátké sloupce menu. Žluté oblasti se netýká.",
            GameLingoOCRConfig.filterMenuText) {
            GameLingoOCRConfig.setFilterMenu(activity, it)
        }
        action(if (advanced) "Skrýt pokročilé filtry" else "Pokročilé OCR filtry",
            "Ignorované řádky, minimální výška a filtry obou oblastí.") {
            dialog.dismiss(); showPage(activity, onAreas, !advanced)
        }
        if (advanced) {
        toggle("Žlutá: filtrovat pokyny ovladače",
            "Volitelně vynechá anglické pokyny jako Press A nebo Hold R2. Běžné volby ponechá.",
            GameLingoOCRConfig.filterChoiceControls) {
            GameLingoOCRConfig.setFilterChoiceControls(activity, it)
        }
        for (yellow in listOf(false, true)) {
            val raw = if (yellow) GameLingoOCRConfig.ignoredYellowLines else GameLingoOCRConfig.ignoredBlueLines
            val label = tr(if (yellow) "Žlutá" else "Modrá")
            action("$label: " + tr("Ignorované řádky"), tr("Počet") + ": " + GameLingoIgnoredLines.entries(raw).size, canEdit) {
                val input = android.widget.EditText(activity).apply {
                    setText(raw); minLines = 3; maxLines = 8
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    filters = arrayOf(android.text.InputFilter.LengthFilter(8192))
                    hint = tr("Jeden přesný výraz na řádek")
                }
                android.app.AlertDialog.Builder(activity).setTitle("$label: " + tr("Ignorované řádky"))
                    .setView(input).setNegativeButton(tr("Zrušit"), null)
                    .setPositiveButton(tr("Uložit")) { _, _ ->
                        GameLingoOCRConfig.setIgnoredLines(activity, yellow, input.text.toString())
                        dialog.dismiss(); showPage(activity, onAreas, advanced)
                    }.show()
            }
        }
        action("Modrá: minimální výška textu", if (GameLingoOCRConfig.minBlueHeight == 0) "Automaticky" else "${GameLingoOCRConfig.minBlueHeight} %", canEdit) {
            val labels = arrayOf(tr("Automaticky"), "1 %", "2 %", "3 %", "4 %", "5 %")
            fun save(index: Int) {
                GameLingoOCRConfig.setMinBlueHeight(activity, index)
                dialog.dismiss(); showPage(activity, onAreas, advanced)
            }
            if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.select(activity, tr("Modrá: velikost textu"), labels, GameLingoOCRConfig.minBlueHeight, ::save)
            else android.app.AlertDialog.Builder(activity).setTitle(tr("Modrá: velikost textu"))
                .setSingleChoiceItems(labels, GameLingoOCRConfig.minBlueHeight) { chooser, index -> chooser.dismiss(); save(index) }
                .setNegativeButton(tr("Zrušit"), null).show()
        }
        action("Žlutá: minimální výška textu", if (GameLingoOCRConfig.minYellowHeight == 0) "Automaticky" else "${GameLingoOCRConfig.minYellowHeight} %", canEdit) {
            val labels = arrayOf(tr("Automaticky"), "1 %", "2 %", "3 %", "4 %", "5 %")
            fun save(index: Int) {
                GameLingoOCRConfig.setMinYellowHeight(activity, index)
                dialog.dismiss(); showPage(activity, onAreas, advanced)
            }
            if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.select(activity, tr("Žlutá: velikost textu"), labels, GameLingoOCRConfig.minYellowHeight, ::save)
            else android.app.AlertDialog.Builder(activity).setTitle(tr("Žlutá: velikost textu"))
                .setSingleChoiceItems(labels, GameLingoOCRConfig.minYellowHeight) { chooser, index -> chooser.dismiss(); save(index) }
                .setNegativeButton(tr("Zrušit"), null).show()
        }
        action("Obnovit filtry modré oblasti", "Bez změny rámečků a žluté oblasti.", canEdit) {
            GameLingoOCRConfig.setFilterMenu(activity, true)
            GameLingoOCRConfig.setIgnoredLines(activity, false, "")
            GameLingoOCRConfig.setMinBlueHeight(activity, 0)
            dialog.dismiss(); showPage(activity, onAreas, advanced)
        }
        action("Obnovit filtry žluté oblasti", "Bez změny rámečků a modré oblasti.", canEdit) {
            GameLingoOCRConfig.setFilterChoiceControls(activity, false)
            GameLingoOCRConfig.setIgnoredLines(activity, true, "")
            GameLingoOCRConfig.setMinYellowHeight(activity, 0)
            dialog.dismiss(); showPage(activity, onAreas, advanced)
        }
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
            showPage(activity, onAreas, advanced)
        }
        if(GameLingoTVUi.isTV(activity)) {
            action("Výchozí oblast",GameLingoOCRConfig.regionLabels[GameLingoOCRConfig.regionKeys.indexOf(GameLingoOCRConfig.region(activity))],
                canEdit && !OblastTitulku.rucne) {
                GameLingoTVUi.select(activity,tr("Výchozí oblast"),GameLingoOCRConfig.regionLabels.map(::tr).toTypedArray(),
                    GameLingoOCRConfig.regionKeys.indexOf(GameLingoOCRConfig.region(activity))) { i ->
                    GameLingoOCRConfig.setRegion(activity,GameLingoOCRConfig.regionKeys[i])
                    dialog.dismiss();showPage(activity,onAreas,advanced)
                }
            }
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
            if(GameLingoTVUi.isTV(activity)) GameLingoOCRConfig.setRegion(activity,"legacy")
            OblastTitulku.rucne = false
            OblastTitulku.oblast = OblastTitulku.VYCHOZI.copyOf()
            OblastTitulku.uloz(activity)
            dialog.dismiss()
            showPage(activity, onAreas, advanced)
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
