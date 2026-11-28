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
        if (a.isFinishing || a.isDestroyed) return
        val dialog = Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        var openingDevices = false
        var reopeningForScale = false
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.pageBackground(a)
            setPadding(d(a, 38), d(a, 24), d(a, 38), d(a, 24))
        }
        val heading = GameLingoTVUi.title(a, GameLingoLanguages.ui(a, "Nastavení GameLingo TV"), 25f)
        val back = GameLingoTVUi.action(a, GameLingoLanguages.ui(a, "Zpět")) { dialog.dismiss() }
        back.id = View.generateViewId()
        val help=GameLingoHelp.button(a,"settings")
        root.addView(GameLingoTVUi.header(a, heading, back, help))
        val cards = mutableListOf<View>()
        val cardLabels = mutableListOf<Pair<String, String>>()
        fun refreshLanguage() {
            if (a.isFinishing || a.isDestroyed || !dialog.isShowing) return
            heading.text = GameLingoLanguages.ui(a, "Nastavení GameLingo TV")
            back.text = GameLingoLanguages.ui(a, "Zpět")
            help.contentDescription = GameLingoLanguages.ui(a, "Otevřít nápovědu")
            cards.forEachIndexed { index, card ->
                val (label, detail) = cardLabels[index]
                card.findViewWithTag<TextView>("gamelingo-title")?.text = GameLingoLanguages.ui(a, label)
                card.findViewWithTag<TextView>("gamelingo-detail")?.text = when (index) {
                    1 -> GameLingoAutomaticConfig.label(a)
                    3 -> GameLingoLanguages.ui(a, if (GameLingoAutomaticConfig.enabled(a)) "Automatické zóny" else "Čtyři oblasti")
                    else -> GameLingoLanguages.ui(a, detail)
                }
            }
        }
        fun tile(symbol: String, label: String, detail: String, action: () -> Unit) {
            val card = GameLingoVisualKit.detailRow(a,symbol,GameLingoLanguages.ui(a, label),GameLingoLanguages.ui(a, detail),action).apply {
                id=View.generateViewId()
            }
            cards.add(card)
            cardLabels.add(label to detail)
        }
        tile("globe", "Jazyky", "Aplikace, hra, překlad") { GameLingoLanguages.show(a, onChanged = ::refreshLanguage) }
        tile("viewfinder", "OCR a rozpoznávání", GameLingoAutomaticConfig.label(a)) { if (!GameLingoScreenTranslationService.running) GameLingoAutomaticConfig.mode(a, Nastaveni(a),
            onStandardAreas = { GameLingoDirectTVLayout.show(a) {} },
            onLanguage = { GameLingoLanguages.show(a, onChanged = ::refreshLanguage) },
            onChanged = {
                cards[1].findViewWithTag<android.widget.TextView>("gamelingo-detail")?.text = GameLingoAutomaticConfig.label(a)
                cards[3].findViewWithTag<android.widget.TextView>("gamelingo-detail")?.text = GameLingoLanguages.ui(a, if(GameLingoAutomaticConfig.enabled(a)) "Automatické zóny" else "Čtyři oblasti")
            }) }
        tile("character.book.closed.fill", "Překlad", "Dialogy, herní texty, Story") { translation(a) }
        tile("rectangle.dashed", "Oblasti obrazovky", if(GameLingoAutomaticConfig.enabled(a)) "Automatické zóny" else "Čtyři oblasti") { if (!GameLingoScreenTranslationService.running) { if(GameLingoAutomaticConfig.enabled(a)) GameLingoAutomaticConfig.showZones(a) else GameLingoDirectTVLayout.show(a) {} } }
        tile("text.bubble", "Vzhled titulků", "Barvy, pozadí a velikost") { GameLingoTVCaptionStyle.show(a) }
        tile("gamecontroller", "Profily her", "Hry, video a aplikace TV") { GameLingoTVProfiles.show(a, onApplied = ::refreshLanguage) }
        tile("display.2", "Zařízení", "Konzole a telefony") { openingDevices = true; dialog.dismiss(); onDevices() }
        tile("slider.horizontal.3", "Pokročilé nastavení", "Slovník, historie a diagnostika") {
            val options = arrayOf(
                GameLingoLanguages.ui(a, "Velikost rozhraní") + " · " + GameLingoTVUi.menuPercent(a) + " %",
                GameLingoLanguages.ui(a, "Herní slovník"),
                GameLingoLanguages.ui(a, "Historie překladů"),
                GameLingoLanguages.ui(a, "Překladová cache"),
                GameLingoLanguages.ui(a, "Diagnostika"),
                GameLingoLanguages.ui(a, "Obnovit výchozí nastavení"),
                GameLingoLanguages.ui(a, "Soukromí"))
            GameLingoTVUi.select(a, GameLingoLanguages.ui(a, "Pokročilé nastavení"), options, -1) { index ->
                when (index) {
                    0 -> {
                        val sizes = GameLingoTVUi.menuSizes
                        val labels = sizes.map { percent ->
                            "$percent %" + if (percent == 100) " · " + GameLingoLanguages.ui(a, "Výchozí") else ""
                        }.toTypedArray()
                        GameLingoTVUi.select(a, GameLingoLanguages.ui(a, "Velikost rozhraní"), labels,
                            sizes.indexOf(GameLingoTVUi.menuPercent(a))) { choice ->
                            val next = sizes[choice]
                            if (next != GameLingoTVUi.menuPercent(a) && !a.isFinishing && !a.isDestroyed) {
                                GameLingoTVUi.setMenuPercent(a, next)
                                // Recreate only the menu, not the Activity or running services.
                                reopeningForScale = true
                                dialog.dismiss()
                                onUpdate()
                                show(a, onDevices, onUpdate)
                            }
                        }
                    }
                    1 -> GameLingoGlossaryUi.show(a)
                    2 -> GameLingoHistoryUi.show(a)
                    3 -> GameLingoCachePrivacyUi.show(a)
                    4 -> diagnostics(a)
                    5 -> GameLingoSettings.confirmRestoreDefaults(a,
                        directTVBusy = { GameLingoScreenTranslationService.running }) {
                        // Rebuild the cards to show the default mode and scale.
                        reopeningForScale = true
                        dialog.dismiss()
                        onUpdate()
                        show(a, onDevices, onUpdate)
                    }
                    6 -> GameLingoAbout.showPrivacy(a)
                }
            }
        }
        tile("info.circle", "O aplikaci / Licence", "Verze a licence") { GameLingoAbout.show(a) }
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
        back.nextFocusDownId = cards[0].id
        back.nextFocusRightId=help.id
        help.nextFocusLeftId=back.id
        help.nextFocusDownId=cards[2].id
        cards[2].nextFocusUpId=help.id
        dialog.setContentView(root)
        // Both remote BACK and the visible button close through this path.
        dialog.setOnDismissListener {
            if (!reopeningForScale && !openingDevices && !a.isFinishing && !a.isDestroyed) onUpdate()
        }
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
        AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a, "Nejdřív zastav překlad a příjem.")).setPositiveButton("OK", null).show()
        return true
    }
    private fun ocr(a: Activity) {
        if (busy(a)) return
        GameLingoOCRSettings.show(a) { GameLingoDirectTVLayout.show(a) {} }
    }
    private fun translation(a: Activity) {
        if (busy(a)) return
        GameLingoSettings.showTranslation(a, Nastaveni(a),
            { if (GameLingoAutomaticConfig.enabled(a)) GameLingoAutomaticConfig.showZones(a) else GameLingoDirectTVLayout.show(a) {} }, { GameLingoLanguages.show(a) })
    }
    private fun diagnostics(a: Activity) {
        fun tr(key: String) = GameLingoLanguages.ui(a, key)
        val info = PrijemService.statistikaPrijmu() + "\n\n" + tr("Verze:") + " " +
            a.packageManager.getPackageInfo(a.packageName, 0).versionName +
            "\n" + tr("Samostatná TV:") + " " + GameLingoScreenTranslationService.localizedStatus(a, GameLingoScreenTranslationService.status) +
            "\n" + tr("OCR obrazovky: pouze MediaProjection") +
            "\n" + GameLingoXboxDiagnostics.summary() +
            "\n\n" + GameLingoScreenTranslationService.phaseSummary(a)
        val body=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
        var refreshPage: android.app.Dialog? = null
        body.addView(GameLingoTVUi.title(a,tr("Stav"),24f))
        body.addView(GameLingoTVUi.title(a,info+"\n"+tr("Překryv:")+" "+tr(if(Settings.canDrawOverlays(a)) "Povolen" else "Nepovolen"),20f).apply {
            background=GameLingoTVUi.panel(a); val pad=d(a,16);setPadding(pad,pad,pad,pad)
        })
        body.addView(GameLingoTVUi.title(a,tr("Poslední překlad"),24f))
        val item=GameLingoTranslationHistory.snapshot().firstOrNull()
        body.addView(GameLingoTVUi.title(a,if(item==null)tr("Zatím žádný překlad ve volitelné historii. Pro živý náhled použij Test OCR.") else item.original+"\n\n"+item.translated,20f).apply {
            background=GameLingoTVUi.panel(a); val pad=d(a,16);setPadding(pad,pad,pad,pad)
        })
        body.addView(GameLingoTVUi.action(a,tr("Povolit překryv")) {
            a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+a.packageName)))
        })
        body.addView(GameLingoTVUi.action(a,tr("Další nastavení")) {
                val legacy = GameLingoTVPairing.legacyEnabled(a)
                GameLingoTVUi.select(a,tr("Diagnostika a kompatibilita"),arrayOf(tr("Legacy příjem:") + " " + tr(if (legacy) "Zapnuto" else "Vypnuto"), tr("Vymazat místní diagnostický log"))) { i ->
                        if (i == 0) {
                            if (legacy) GameLingoTVPairing.setLegacyEnabled(a, false)
                            else AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a, "Povolit nepodepsané titulky ze starších aplikací pouze v důvěryhodné domácí síti?"))
                                .setNegativeButton(GameLingoLanguages.ui(a, "Zrušit"), null).setPositiveButton(GameLingoLanguages.ui(a, "Povolit")) { _, _ -> GameLingoTVPairing.setLegacyEnabled(a, true) }.show()
                        } else if (!busy(a)) AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a, "Vymazat místní diagnostické logy?"))
                            .setNegativeButton(GameLingoLanguages.ui(a, "Zrušit"), null).setPositiveButton(GameLingoLanguages.ui(a, "Vymazat")) { _, _ ->
                                if (!busy(a)) {
                                    val removed = listOf("titulky.log","titulky-private-v2.log").map {
                                        val file = java.io.File(a.filesDir,it)
                                        !file.exists() || file.delete()
                                    }.all { it }
                                    AlertDialog.Builder(a).setMessage(tr(if (removed) "Místní log překladu vymazán." else "Některý log se nepodařilo vymazat."))
                                        .setPositiveButton("OK",null).show()
                                }
                            }.show()
                    }
        })
        body.addView(GameLingoTVUi.action(a,tr("Aktualizovat stav")) {
            // Rebuild the snapshot without starting capture.
            refreshPage?.dismiss()
            diagnostics(a)
        })
        body.addView(GameLingoTVUi.action(a,tr("Technický log")) { GameLingoHelp.showTechnicalLog(a) })
        body.addView(GameLingoTVUi.action(a,tr("Test OCR")) { GameLingoScreenTranslationService.diagnostics(a) })
        body.addView(GameLingoTVUi.action(a,tr("Nahlásit problém")) { GameLingoHelp.report(a) })
        refreshPage = GameLingoTVUi.page(a,tr("Diagnostika TV"),body,helpTopic="diagnostics")
    }
}
