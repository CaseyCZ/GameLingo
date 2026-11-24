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
            setPadding(d(a, 38), d(a, 24), d(a, 38), d(a, 24))
        }
        val header = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(GameLingoTVUi.title(a, "Nastavení GameLingo TV", 25f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val back = GameLingoTVUi.action(a, "Zpět") { dialog.dismiss(); onUpdate() }
        back.id = View.generateViewId()
        val help=GameLingoHelp.button(a,"settings")
        header.addView(help,GameLingoHelp.layout(a))
        header.addView(back)
        root.addView(header)
        val cards = mutableListOf<View>()
        fun tile(symbol: String, label: String, detail: String, action: () -> Unit) {
            val card = GameLingoTVUi.tile(a, symbol, label, detail, action).apply {
                minimumHeight = 0
                setPadding(d(a, 15), d(a, 7), d(a, 15), d(a, 7))
                background = compactPanel(a, false)
                setOnFocusChangeListener { v, focused -> v.background = compactPanel(a, focused) }
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
        tile("♧", "Profily", "Hry, video a aplikace TV") { GameLingoTVProfiles.show(a) }
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
                    if (index < 2) rightMargin = d(a, 16)
                })
            }
            rows.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(a, 110)).apply {
                topMargin = d(a, 12)
            })
        }
        root.addView(android.widget.ScrollView(a).apply { addView(rows); isFillViewport = false }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        cards.forEachIndexed { i, card ->
            card.nextFocusLeftId = cards[if (i % 3 > 0) i-1 else i].id
            card.nextFocusRightId = cards[if (i % 3 < 2) i+1 else i].id
            card.nextFocusUpId = if (i >= 3) cards[i-3].id else back.id
            card.nextFocusDownId = cards.getOrNull(i+3)?.id ?: card.id
        }
        back.nextFocusDownId = cards[2].id
        back.nextFocusLeftId=help.id
        help.nextFocusRightId=back.id
        help.nextFocusDownId=cards[1].id
        cards[1].nextFocusUpId=help.id
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        cards.first().requestFocus()
    }
    private fun d(a: Activity, v: Int) = GameLingoTVUi.dp(a, v)
    private fun compactPanel(a: Activity, focused: Boolean) = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.parseColor(if (focused) "#203B55" else "#142033"))
        cornerRadius = d(a, 13).toFloat()
        setStroke(d(a, if (focused) 4 else 1).coerceAtLeast(1), Color.parseColor(if (focused) "#00D6FF" else "#34445D"))
    }
    private fun busy(a: Activity): Boolean {
        if (!RemotePlayService.aktivni && !PrijemService.bezi && !GameLingoScreenTranslationService.running) return false
        AlertDialog.Builder(a).setMessage("Nejdřív zastav překlad a příjem.").setPositiveButton("OK", null).show()
        return true
    }
    private fun ocr(a: Activity) {
        if (busy(a)) return
        GameLingoOCRSettings.show(a) { GameLingoDirectTVLayout.show(a) {} }
    }
    private fun translation(a: Activity) {
        if (busy(a)) return
        GameLingoSettings.showTranslation(a, Nastaveni(a),
            { GameLingoDirectTVLayout.show(a) {} }, { GameLingoLanguages.show(a) })
    }
    private fun diagnostics(a: Activity) {
        val info = PrijemService.statistikaPrijmu() + "\n\nVerze: " +
            a.packageManager.getPackageInfo(a.packageName, 0).versionName +
            "\nSamostatná TV: " + GameLingoScreenTranslationService.status +
            "\nOCR obrazovky: pouze MediaProjection"
        val body=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
        body.addView(GameLingoTVUi.title(a,info+"\nPřekryv: "+if(Settings.canDrawOverlays(a)) "Povolen" else "Nepovolen",20f))
        body.addView(GameLingoTVUi.action(a,"Povolit překryv") {
            a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+a.packageName)))
        })
        body.addView(GameLingoTVUi.action(a,"Další nastavení") {
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
        })
        body.addView(GameLingoTVUi.action(a,"Test OCR") { GameLingoScreenTranslationService.diagnostics(a) })
        GameLingoTVUi.page(a,"Diagnostika TV",body,helpTopic="diagnostics")
    }
}
