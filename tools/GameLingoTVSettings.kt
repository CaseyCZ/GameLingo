package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar

/** TV-specific category dashboard, not a phone-sized list of individual rows. */
object GameLingoTVSettings {
    fun show(a: Activity, onDevices: () -> Unit, onUpdate: () -> Unit) {
        val dialog = Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.pageBackground(a)
            setPadding(d(a, 18), d(a, 10), d(a, 18), d(a, 10))
        }
        val header = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(GameLingoTVUi.title(a, "Nastavení GameLingo TV", 25f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val back = GameLingoTVUi.action(a, "Zpět") { dialog.dismiss(); onUpdate() }
        back.id = View.generateViewId()
        header.addView(back)
        root.addView(header)
        val cards = mutableListOf<View>()
        fun tile(symbol: String, label: String, detail: String, action: () -> Unit) {
            val card = GameLingoTVUi.tile(a, symbol, label, detail, action).apply {
                minimumHeight = 0
                setPadding(d(a, 8), d(a, 4), d(a, 8), d(a, 4))
                id = View.generateViewId()
                (getChildAt(0) as? TextView)?.textSize = 14f
                (getChildAt(1) as? TextView)?.textSize = 15f
                (getChildAt(2) as? TextView)?.apply { textSize = 12f; maxLines = 1 }
                for (index in 1..2) (getChildAt(index).layoutParams as LinearLayout.LayoutParams).topMargin = d(a, 1)
            }
            cards.add(card)
        }
        tile("◎", "Jazyky", "Aplikace, hra, překlad") { GameLingoLanguages.show(a) }
        tile("▣", "OCR", "Režim, filtr a rozšíření") { ocr(a) }
        tile("文", "Překlad", "Dialogy, herní texty, Story") { translation(a) }
        tile("⌗", "Rozložení", "Čtyři oblasti") { GameLingoDirectTVLayout.show(a) {} }
        tile("Aa", "Vzhled titulků", "Barvy, pozadí a velikost") { GameLingoTVCaptionStyle.show(a) }
        tile("♧", "Profily her", "Uložená nastavení") { GameLingoTVProfiles.show(a) }
        tile("▭", "Zařízení", "Konzole a telefony") { dialog.dismiss(); onDevices() }
        tile("文", "Herní slovník", "Vlastní výrazy") { GameLingoGlossaryUi.show(a) }
        tile("◷", "Historie", "Překlady na TV") { GameLingoHistoryUi.show(a) }
        tile("▣", "Překladová paměť", "Ukládání a vymazání") { GameLingoCachePrivacyUi.show(a) }
        tile("≋", "Diagnostika", "Příjem, oprávnění, verze") { diagnostics(a) }
        tile("ⓘ", "O aplikaci", "Verze a licence") { GameLingoAbout.show(a) }
        val rows = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        cards.chunked(3).forEach { group ->
            val row = LinearLayout(a).apply { clipChildren = false }
            group.forEachIndexed { index, card ->
                row.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (index < 2) rightMargin = d(a, 7)
                })
            }
            rows.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = d(a, 6)
            })
        }
        root.addView(rows, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        cards.forEachIndexed { i, card ->
            card.nextFocusLeftId = cards[if (i % 3 > 0) i-1 else i].id
            card.nextFocusRightId = cards[if (i % 3 < 2) i+1 else i].id
            card.nextFocusUpId = if (i >= 3) cards[i-3].id else back.id
            card.nextFocusDownId = cards.getOrNull(i+3)?.id ?: card.id
        }
        back.nextFocusDownId = cards[2].id
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        cards.first().requestFocus()
    }
    private fun d(a: Activity, v: Int) = GameLingoTVUi.dp(a, v)
    private fun busy(a: Activity): Boolean {
        if (!RemotePlayService.aktivni && !PrijemService.bezi && !GameLingoScreenTranslationService.running) return false
        AlertDialog.Builder(a).setMessage("Nejdřív zastav překlad a příjem.").setPositiveButton("OK", null).show()
        return true
    }
    private fun ocr(a: Activity) {
        if (busy(a)) return
        GameLingoOCRConfig.load(a)
        val keys = arrayOf("classic", "automatic", "street")
        val labels = arrayOf("Původní · stabilní", "Automatický · vyvážený", "Krátké dialogy · rychlý")
        AlertDialog.Builder(a).setTitle("OCR a rozpoznávání")
            .setItems(arrayOf("Režim: " + GameLingoOCRConfig.title(),
                "Filtrovat menu: " + if (GameLingoOCRConfig.filterMenuText) "Ano" else "Ne",
                "Rozšířené hledání: " + if (GameLingoOCRConfig.expandSearch) "Ano" else "Ne",
                "Výchozí nastavení")) { _, i ->
                when (i) {
                    0 -> AlertDialog.Builder(a).setTitle("Režim OCR").setSingleChoiceItems(labels, keys.indexOf(GameLingoOCRConfig.mode)) { pick, n ->
                        GameLingoOCRConfig.setMode(a, keys[n]); pick.dismiss(); ocr(a)
                    }.setNegativeButton("Zpět", null).show()
                    1 -> { GameLingoOCRConfig.setFilterMenu(a, !GameLingoOCRConfig.filterMenuText); ocr(a) }
                    2 -> { GameLingoOCRConfig.setExpandSearch(a, !GameLingoOCRConfig.expandSearch); ocr(a) }
                    3 -> { GameLingoOCRConfig.reset(a); ocr(a) }
                }
            }.setNegativeButton("Zpět", null).show()
    }
    private fun translation(a: Activity) {
        if (busy(a)) return
        val s = Nastaveni(a)
        Volby.nacti(a); OblastTitulku.nacti(a)
        val labels = arrayOf("Dialogy", "Nápověda a úkoly", "Překrývat originál", "Příběhový režim", "Herní texty a volby (žlutá oblast)", "Celý text místo seznamu voleb", "Ruční oblast OCR (modrá)")
        val checked = booleanArrayOf(s.dialogy, s.napovedy, s.prekryvat, s.pribehovy, Volby.zapnuto, Volby.rezimText, OblastTitulku.rucne)
        val panel = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(24,8,24,8) }
        for (i in labels.indices) panel.addView(android.widget.CheckBox(a).apply {
            text = labels[i]; isChecked = checked[i]; isFocusable = true
            setOnCheckedChangeListener { _, enabled -> checked[i] = enabled }
        })
        val min = CasovaniPribehu.MIN_PROCENT
        var duration = s.casPribehuProcenta
        val label = TextView(a).apply { text = "Doba zobrazení: $duration %" }
        panel.addView(label)
        panel.addView(SeekBar(a).apply {
            max = CasovaniPribehu.MAX_PROCENT - min; progress = duration - min
            keyProgressIncrement = 5; isFocusable = true
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    duration = min + value; label.text = "Doba zobrazení: $duration %"
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        })
        AlertDialog.Builder(a).setTitle("Překlad hry")
            .setView(android.widget.ScrollView(a).apply { addView(panel) })
            .setNegativeButton("Zrušit", null).setPositiveButton("Uložit") { _, _ ->
                if (busy(a)) return@setPositiveButton
                s.dialogy = checked[0]; s.napovedy = checked[1]; s.prekryvat = checked[2]; s.pribehovy = checked[3]
                Volby.zapnuto = checked[4]; Volby.rezimText = checked[5]; Volby.uloz(a)
                OblastTitulku.rucne = checked[6]; OblastTitulku.uloz(a)
                s.casPribehuProcenta = duration
            }.show()
    }
    private fun diagnostics(a: Activity) {
        val info = PrijemService.statistikaPrijmu() + "\n\nVerze: " +
            a.packageManager.getPackageInfo(a.packageName, 0).versionName
        AlertDialog.Builder(a).setTitle("Diagnostika TV").setMessage(info +
            "\nPřekryv: " + if (Settings.canDrawOverlays(a)) "Povolen" else "Nepovolen")
            .setNeutralButton("Povolit překryv") { _, _ -> a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.packageName))) }
            .setNegativeButton("Další nastavení") { _, _ ->
                val legacy = GameLingoTVPairing.legacyEnabled(a)
                AlertDialog.Builder(a).setTitle("Diagnostika a kompatibilita")
                    .setItems(arrayOf("Legacy příjem: " + if (legacy) "Zapnuto" else "Vypnuto", "Vymazat místní diagnostický log")) { _, i ->
                        if (i == 0) {
                            if (legacy) GameLingoTVPairing.setLegacyEnabled(a, false)
                            else AlertDialog.Builder(a).setMessage("Povolit nepodepsané titulky ze starších aplikací pouze v důvěryhodné domácí síti?")
                                .setNegativeButton("Zrušit", null).setPositiveButton("Povolit") { _, _ -> GameLingoTVPairing.setLegacyEnabled(a, true) }.show()
                        } else if (!busy(a)) AlertDialog.Builder(a).setMessage("Vymazat místní diagnostické logy?")
                            .setNegativeButton("Zrušit", null).setPositiveButton("Vymazat") { _, _ ->
                                if (!busy(a)) listOf("titulky.log","titulky-private-v2.log").forEach { java.io.File(a.filesDir,it).delete() }
                            }.show()
                    }.setNegativeButton("Zpět", null).show()
            }.setPositiveButton("Zpět", null).show()
    }
}
