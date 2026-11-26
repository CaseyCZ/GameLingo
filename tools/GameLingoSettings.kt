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
    private val bg = Color.parseColor(GameLingoUIContract.Palette.background)
    private val card = Color.parseColor(GameLingoUIContract.Palette.panel)
    private val accent = Color.parseColor(GameLingoUIContract.Palette.cyan)
    private val muted = Color.parseColor(GameLingoUIContract.Palette.muted)
    private fun rounded(color: Int, radius: Float = 17f) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    /** Reset only display and translation keys; never wipe profiles or pairing. */
    fun restoreDefaultSettings(a: Activity, directTVBusy: () -> Boolean = { false }): Boolean {
        if (RemotePlayService.aktivni || directTVBusy() ||
            (PrijemService.bezi && PrijemService.telefonPripojen())) return false
        val keys = listOf(
            "pribehovy", "casPribehuProcenta", "prekryvat", "dialogy", "napovedy",
            "tvX", "tvY", "tvSirka", "tvPismo", "rozvrhZamek",
            "volbyZap", "volbyUkazat", "volbyZamek", "volbyRezimText",
            "volbyL", "volbyT", "volbyR", "volbyB",
            "titRucne", "titZamek", "titL", "titT", "titR", "titB",
            "directGreenEnabled", "directLockBlue", "directLockGreen",
            "directLockYellow", "directLockOrange"
        ) + (0..4).flatMap { i -> listOf("directOrange$i", "directGreen$i") }
        val local = a.getSharedPreferences("gamelingo", android.content.Context.MODE_PRIVATE)
        val active = TvProfily.nastaveni(a)
        for (prefs in listOf(local, active).distinct()) {
            val editor = prefs.edit()
            keys.forEach { editor.remove(it) }
            editor.apply()
        }
        a.getSharedPreferences("gamelingo_tv_parity",
            android.content.Context.MODE_PRIVATE).edit().apply {
            listOf("region", "showPreviousDialogue", "previousDialogueCount",
                "hideOnSourceGone", "sourceGoneDelay", "fixedTiming",
                "seconds", "skipOld", "maxAge").forEach { remove(it) }
        }.apply()
        a.getSharedPreferences("gamelingo_automatic",
            android.content.Context.MODE_PRIVATE).edit()
            .remove("enabled").remove("zones").apply()
        a.getSharedPreferences("gamelingo_phone_subtitles",
            android.content.Context.MODE_PRIVATE).edit()
            .remove("scale").remove("color").remove("background").apply()
        a.getSharedPreferences("gamelingo_tv_local_style",
            android.content.Context.MODE_PRIVATE).edit()
            .remove("color").remove("opacity").apply()
        a.getSharedPreferences("gamelingo_tv_caption_style",
            android.content.Context.MODE_PRIVATE).edit()
            .remove("color").remove("background").apply()
        a.getSharedPreferences("gamelingo_languages",
            android.content.Context.MODE_PRIVATE).edit()
            .remove("game").remove("target").apply()
        Nastaveni(a).jazyk = GameLingoLanguages.systemApp(a)
        GameLingoTVUi.setMenuPercent(a, 100)
        GameLingoOCRConfig.reset(a)
        Volby.nacti(a)
        OblastTitulku.nacti(a)
        return true
    }

    fun confirmRestoreDefaults(a: Activity, directTVBusy: () -> Boolean = { false },
                               onReset: () -> Unit = {}) {
        fun tr(value: String) = GameLingoLanguages.ui(a, value)
        if (RemotePlayService.aktivni || directTVBusy() ||
            (PrijemService.bezi && PrijemService.telefonPripojen())) {
            android.app.AlertDialog.Builder(a)
                .setMessage(tr("Nejdřív zastav překlad a příjem."))
                .setPositiveButton("OK", null).show()
            return
        }
        android.app.AlertDialog.Builder(a)
            .setTitle(tr("Obnovit výchozí nastavení?"))
            .setMessage(tr("Obnoví OCR, překlad, oblasti a vzhled titulků. Zařízení, účty a herní profily zůstanou zachované."))
            .setNegativeButton(tr("Zrušit"), null)
            .setPositiveButton(tr("Obnovit nastavení")) { _, _ ->
                if (restoreDefaultSettings(a, directTVBusy)) onReset()
                else android.app.AlertDialog.Builder(a)
                    .setMessage(tr("Nejdřív zastav překlad a příjem."))
                    .setPositiveButton("OK", null).show()
            }.show()
    }

    private fun row(c: Activity, title: String, detail: String, state: Boolean,
                    update: (Boolean) -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(card)
        setPadding(17, 13, 17, 13)
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(c).apply {
                text = GameLingoLanguages.ui(c, title); gravity = Gravity.CENTER; textSize = GameLingoVisualKit.sp(c, 17f)
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(c).apply {
                text = GameLingoLanguages.ui(c, detail); gravity = Gravity.CENTER; textSize = GameLingoVisualKit.sp(c, 13f)
                setTextColor(muted)
                setPadding(0, 5, 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val television=GameLingoTVUi.isTV(c)
        val toggle=Switch(c).apply {
            isChecked = state
            isFocusable = !television
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnCheckedChangeListener { _, checked -> update(checked) }
        }
        addView(toggle)
        if(television) {
            isFocusable=true; isClickable=true
            descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
            GameLingoTVUi.controlFocus(c,this)
            setOnClickListener { toggle.isChecked=!toggle.isChecked }
        }
    }

    fun showTranslation(activity: Activity, settings: Nastaveni,
             onAreas: () -> Unit, onLanguage: () -> Unit) {
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
        Volby.nacti(activity)
        OblastTitulku.nacti(activity)
        val television=GameLingoTVUi.isTV(activity)
        val dialog = if(television) Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen) else Dialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = if(television) GameLingoVisualKit.pageBackground(activity) else rounded(bg)
        }
        fun add(r: LinearLayout) {
            content.addView(r, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 })
        }
        add(row(activity, "Překládat dialogy", "Titulky pro rozhovory ve hrách.",
            settings.dialogy) { settings.dialogy = it })
        add(row(activity, "Zobrazit i předchozí repliku", "V běžném režimu bez překrývání originálu; nemění příběhovou frontu.",
            GameLingoOCRConfig.showPreviousDialogue(activity)) { GameLingoOCRConfig.setShowPreviousDialogue(activity, it) })
        add(row(activity, "Dvě předchozí repliky", "Pokud je zapnutý předchozí dialog, zobrazí až dva nedávné překlady místo jednoho.",
            GameLingoOCRConfig.previousDialogueCount(activity) == 2) { GameLingoOCRConfig.setPreviousDialogueCount(activity, if (it) 2 else 1) })
        add(row(activity, "Skrýt po zmizení originálu", "Volitelně ukončí zobrazení běžného dialogu po potvrzeném zmizení zdrojového textu.",
            GameLingoOCRConfig.hideOnSourceGone(activity)) { GameLingoOCRConfig.setHideOnSourceGone(activity, it) })
        add(row(activity, "Herní texty a volby", "Samostatná žlutá oblast OCR.",
            Volby.zapnuto) { Volby.zapnuto = it; Volby.uloz(activity) })
        add(row(activity, "Dopisy a zprávy", "Překládat celý text místo seznamu voleb.",
            Volby.rezimText) { Volby.rezimText = it; Volby.uloz(activity) })
        add(row(activity, "Nápověda a úkoly", "Překládat herní nápovědu.",
            settings.napovedy) { settings.napovedy = it })
        add(row(activity, "Překrývat originál", "Překlad překryje původní titulky.",
            settings.prekryvat) { settings.prekryvat = it })
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
            textSize = GameLingoVisualKit.sp(activity, 16f); gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        fun refresh() {
            label.text = tr("Doba zobrazení") + ": " + settings.casPribehuProcenta + " %"
        }
        refresh()
        sliderCard.addView(label)
        sliderCard.addView(SeekBar(activity).apply {
            max = CasovaniPribehu.MAX_PROCENT - CasovaniPribehu.MIN_PROCENT
            progress = settings.casPribehuProcenta - CasovaniPribehu.MIN_PROCENT
            isFocusable = true; keyProgressIncrement = 5
            if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, this)
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
        if(television) {
            add(row(activity,"Pevná doba titulku","Nastavení v sekundách jako na iOS; příběhová fronta zachovává vlastní časování.",
                GameLingoOCRConfig.fixedTiming(activity)) { GameLingoOCRConfig.saveTiming(activity,fixed=it) })
            fun timingSlider(title:String,min:Int,max:Int,value:Int,save:(Int)->Unit) {
                val panel=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; background=rounded(card);setPadding(17,14,17,16) }
                val caption=TextView(activity).apply { gravity=Gravity.CENTER;textSize = GameLingoVisualKit.sp(activity, 16f);setTextColor(Color.WHITE);text="${tr(title)}: $value s" }
                panel.addView(caption)
                panel.addView(SeekBar(activity).apply {
                    this.max=max-min;progress=value-min;keyProgressIncrement=1;isFocusable=true
                    GameLingoTVUi.controlFocus(activity,this)
                    setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar:SeekBar, v:Int, fromUser:Boolean) {
                            caption.text="${tr(title)}: "+(v+min)+" s"
                            if(fromUser) save(v+min)
                        }
                        override fun onStartTrackingTouch(bar:SeekBar){}
                        override fun onStopTrackingTouch(bar:SeekBar){}
                    })
                })
                add(panel)
            }
            timingSlider("Doba titulku",3,10,GameLingoOCRConfig.seconds(activity)){GameLingoOCRConfig.saveTiming(activity,duration=it)}
            add(row(activity,"Přeskočit starý překlad","Zahodí výsledek, který překročil maximální stáří.",
                GameLingoOCRConfig.skipOld(activity)){GameLingoOCRConfig.saveTiming(activity,skip=it)})
            timingSlider("Maximální stáří",2,8,GameLingoOCRConfig.maxAge(activity)){GameLingoOCRConfig.saveTiming(activity,age=it)}
        }

        fun button(title: String, action: () -> Unit) {
            content.addView(TextView(activity).apply {
                text = title; gravity = Gravity.CENTER; textSize = GameLingoVisualKit.sp(activity, 17f)
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
                if (GameLingoTVUi.isTV(activity)) GameLingoTVUi.controlFocus(activity, this)
                setOnClickListener { dialog.dismiss(); action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 9 })
        }
        button(tr("Prodleva po zmizení") + ": " + GameLingoOCRConfig.sourceGoneDelay(activity) + " s") {
            val labels = arrayOf(tr("Hned"), "1 s", "2 s")
            fun save(index: Int) {
                GameLingoOCRConfig.setSourceGoneDelay(activity, index)
                showTranslation(activity, settings, onAreas, onLanguage)
            }
            if (television) GameLingoTVUi.select(activity, tr("Prodleva po zmizení"), labels, GameLingoOCRConfig.sourceGoneDelay(activity), ::save)
            else android.app.AlertDialog.Builder(activity).setTitle(tr("Prodleva po zmizení"))
                .setSingleChoiceItems(labels, GameLingoOCRConfig.sourceGoneDelay(activity)) { chooser, index -> chooser.dismiss(); save(index) }
                .setNegativeButton(tr("Zrušit"), null).show()
        }
        button("▣  " + tr("Oblasti na obrazovce")) { onAreas() }
        button("🌐  " + tr("Jazyk překladu")) { onLanguage() }
        GameLingoTVUi.mountPage(activity, dialog, tr("Překlad"), content, helpTopic = "translation")
    }
    /**
     * Exactly the same menu hierarchy as iOS:
     * Jazyky, OCR, Překlad, Oblasti, Titulky, Profily, Zařízení, O aplikaci.
     * Every destination invokes an existing functional screen.
     */
    /** A real embedded settings tab, reused by the full-screen settings entry point. */
    fun content(activity: Activity, settings: Nastaveni,
             onAreas: () -> Unit, onLanguage: () -> Unit,
             onDevices: () -> Unit = {},
             onDismiss: () -> Unit = {}, showClose: Boolean = false,
             onRefresh: () -> Unit = {}, showHeader: Boolean = true): LinearLayout {
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
        val d = GameLingoVisualKit.dp(activity, 1)
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16 * d, 12 * d, 16 * d, 16 * d)
            background = GameLingoVisualKit.pageBackground(activity)
        }

        val heading = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(TextView(activity).apply {
            text = tr("Nastavení"); gravity = Gravity.CENTER
            textSize = GameLingoVisualKit.sp(activity, 29f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (showClose) heading.addView(TextView(activity).apply {
            text = "✕"
            textSize = GameLingoVisualKit.sp(activity, 23f)
            gravity = Gravity.CENTER
            contentDescription = tr("Zavřít nastavení")
            setTextColor(Color.WHITE)
            background = GameLingoVisualKit.card(activity)
            isFocusable = true
            setOnClickListener { onDismiss() }
        }, LinearLayout.LayoutParams(48 * d, 48 * d))
        if (showHeader) page.addView(GameLingoVisualKit.neonTitle(activity,
            "Nastavení", "Přizpůsobení překladu a titulků", "gearshape.fill"))

        fun section(title: String) {
            page.addView(TextView(activity).apply {
                text = tr(title); gravity = Gravity.CENTER
                textSize = GameLingoVisualKit.sp(activity, 12f)
                letterSpacing = 0.10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#02D6FF"))
                setPadding(0, 14 * d, 0, 8 * d)
            })
        }
        fun menu(symbol: String, title: String, sub: String, parent: LinearLayout = page, open: () -> Unit) {
            val row = GameLingoVisualKit.detailRow(activity,symbol,tr(title),tr(sub)) { onDismiss();open() }
            parent.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 9 * d
            })
        }
        section(GameLingoUIContract.settingsSections[0])
        menu("globe", "Jazyky",
            GameLingoLanguages.name(GameLingoLanguages.gameLanguages,
                GameLingoLanguages.game(activity)) + " → " +
            GameLingoLanguages.name(GameLingoLanguages.targetLanguages,
                GameLingoLanguages.target(activity))) {
            onLanguage()
        }

        // Match the inline iOS Standardní / Automatický segmented control.
        page.addView(GameLingoAutomaticConfig.modePicker(activity, onChanged = onRefresh),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 9 * d })
        section(GameLingoUIContract.settingsSections[1])
        val advanced = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }
        page.addView(GameLingoVisualKit.detailRow(activity, "slider.horizontal.3", tr("Pokročilé nastavení"), tr("OCR a rozpoznávání · Překlad")) {
            advanced.visibility = if (advanced.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 9 * d })
        menu("viewfinder", "OCR a rozpoznávání", "Citlivost, režim a filtrování textu", parent = advanced) {
            GameLingoOCRSettings.show(activity, onAreas)
        }
        menu("character.book.closed.fill", "Překlad", "Časování a aktuálnost titulků", parent = advanced) {
            showTranslation(activity, settings, onAreas, onLanguage)
        }
        page.addView(advanced)
        menu("rectangle.dashed", "Oblasti obrazovky",
             if(GameLingoAutomaticConfig.enabled(activity)) "Automatické zóny" else "Čtyři nezávislé oblasti") {
            if(GameLingoAutomaticConfig.enabled(activity))GameLingoAutomaticConfig.showZones(activity) else onAreas()
        }

        section(GameLingoUIContract.settingsSections[2])
        menu("text.bubble", "Vzhled titulků",
             "Velikost, šířka a poloha titulků") {
             GameLingoSubtitleAppearance.show(activity)
         }
        menu("gamecontroller", "Profily her",
             "Vlastní nastavení pro každou hru") {
            GameLingoProfiles.show(activity, settings) {}
        }
        section(GameLingoUIContract.settingsSections[3])
        menu("display.2", "Zařízení",
             "Xbox, PlayStation a Google TV") { onDevices() }
        menu("clock.arrow.circlepath", "Historie překladů", "Poslední dialogy a herní texty") {
            GameLingoHistoryUi.show(activity)
        }
        menu("internaldrive", "Překladová cache", "Místní uložené překlady") {
            GameLingoCachePrivacyUi.show(activity)
        }
        menu("waveform.path.ecg", "Diagnostika", "Stav připojení a technický log") {
            GameLingoDiagnostics.show(activity)
        }
        menu("info.circle", "O aplikaci / Licence",
             "GameLingo, zdroje a AGPLv3") {
            GameLingoAbout.show(activity)
        }
        menu("arrow.counterclockwise", "Obnovit výchozí nastavení",
             "OCR, překlad, oblasti a vzhled titulků") {
            confirmRestoreDefaults(activity) { onRefresh() }
        }
        return page
    }

    fun show(activity: Activity, settings: Nastaveni,
             onAreas: () -> Unit, onLanguage: () -> Unit,
             onDevices: () -> Unit = {}) {
        if (activity.isFinishing) return
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val page = content(activity, settings, onAreas, onLanguage, onDevices,
            onDismiss = { dialog.dismiss() }, showClose = false, showHeader = false)
        GameLingoTVUi.mountPage(activity, dialog, GameLingoLanguages.ui(activity, "Nastavení"), page, helpTopic = "settings")
    }

}
