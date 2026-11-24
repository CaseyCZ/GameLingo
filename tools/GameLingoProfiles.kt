package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real, device-local game profiles for GameLingo Android.
 * Saves/restores subtitle/choice rectangles and selected translation settings.
 * No accounts, passwords, PSN or Xbox credentials are stored in game profiles.
 */
object GameLingoProfiles {
    private val navy = Color.parseColor("#070B19")
    private val card = Color.parseColor("#111A33")
    private val cyan = Color.parseColor("#02D6FF")
    private val muted = Color.parseColor("#92A4C7")

    private fun prefs(c: Context) =
        c.getSharedPreferences("gamelingo_game_profiles", Context.MODE_PRIVATE)

    private fun box(fill: Int = card, outline: Int? = null, radius: Float = 18f) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (outline != null) setStroke(2, outline)
        }

    private fun text(c: Context, label: String, size: Float = 16f,
                     color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(c).apply {
            this.text = label
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(14, 10, 14, 10)
        }

    private fun button(c: Context, label: String, action: () -> Unit): TextView =
        text(c, label, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GameLingoVisualKit.card(c, selected = true)
            setOnClickListener { action() }
            isClickable = true
            isFocusable = true
            minHeight = 50
        }

    private fun array(values: FloatArray) = JSONArray().apply {
        values.forEach { put(it.toDouble()) }
    }

    private fun readArray(o: JSONObject, key: String, fallback: FloatArray): FloatArray {
        val a = o.optJSONArray(key) ?: return fallback
        if (a.length() != 4) return fallback
        val r = FloatArray(4) { index -> a.optDouble(index, Double.NaN).toFloat() }
        if (r.any { !it.isFinite() || it < 0f || it > 1f } ||
            r[2] < r[0] + 0.10f || r[3] < r[1] + 0.05f) return fallback
        return r
    }

    // The dashboard may have a saved receiver while no live receiver is active.
    // Resolve its preferences without connecting or changing TvProfily.aktivniId.
    private fun layoutPrefs(c: Context): android.content.SharedPreferences {
        val store = TvProfily(c)
        val id = if (store.pouzeTelefon) null else store.posledniId
        return if (id != null && store.najdi(id) != null) store.nastaveni(id)
            else c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)
    }

    private fun rect(p: android.content.SharedPreferences, prefix: String, fallback: FloatArray): FloatArray =
        FloatArray(4) { i -> p.getFloat(prefix + listOf("L", "T", "R", "B")[i], fallback[i]) }

    private fun snapshot(c: Context, settings: Nastaveni) = JSONObject().apply {
        put("dialogy", settings.dialogy)
        put("pribehovy", settings.pribehovy)
        put("cas", settings.casPribehuProcenta)
        put("prekryvat", settings.prekryvat)
        put("napovedy", settings.napovedy)
        put("phoneTextSize", c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE).getInt("telefonPismo", 1))
        put("gameLanguage", GameLingoLanguages.game(c))
        put("targetLanguage", GameLingoLanguages.target(c))
        GameLingoOCRConfig.load(c)
        put("ocrMode", GameLingoOCRConfig.mode)
        put("ocrFilter", GameLingoOCRConfig.filterMenuText)
        put("ocrExpand", GameLingoOCRConfig.expandSearch)
        put("phoneScale", GameLingoPhoneSubtitleStyle.scale(c).toDouble())
        put("phoneColor", GameLingoPhoneSubtitleStyle.color(c))
        put("phoneBackground", GameLingoPhoneSubtitleStyle.background(c))
        val p = layoutPrefs(c)
        put("volbyZap", p.getBoolean("volbyZap", false))
        put("volbyText", p.getBoolean("volbyRezimText", false))
        put("volbyZamek", p.getBoolean("volbyZamek", false))
        put("volbyOblast", array(rect(p, "volby", Volby.VYCHOZI)))
        put("dialogRucne", p.getBoolean("titRucne", false))
        put("dialogZamek", p.getBoolean("titZamek", false))
        put("dialogOblast", array(rect(p, "tit", OblastTitulku.VYCHOZI)))
        put("tvX", p.getFloat("tvX", 0.5f).toDouble())
        put("tvY", p.getFloat("tvY", 0.9f).toDouble())
        put("tvSirka", p.getFloat("tvSirka", 0.75f).toDouble())
        put("tvPismo", p.getFloat("tvPismo", 1f).toDouble())
        put("greenLocked", p.getBoolean("rozvrhZamek", false))
        put("yellowPreview", p.getBoolean("volbyUkazat", false))
        put("orangeOutput", array(GameLingoOrangeOutput.readFrom(p)))
        put("orangeLocked", GameLingoOrangeOutput.lockedFrom(p))
    }

    private fun restore(c: Context, settings: Nastaveni, data: JSONObject) {
        settings.dialogy = data.optBoolean("dialogy", settings.dialogy)
        settings.pribehovy = data.optBoolean("pribehovy", settings.pribehovy)
        settings.casPribehuProcenta = data.optInt("cas", settings.casPribehuProcenta)
        settings.prekryvat = data.optBoolean("prekryvat", settings.prekryvat)
        settings.napovedy = data.optBoolean("napovedy", settings.napovedy)
        val phoneSize = data.optInt("phoneTextSize", -1)
        if (phoneSize in 0..2) c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)
            .edit().putInt("telefonPismo", phoneSize).apply()
        // Old profiles omit these keys: retain the current setting in that case.
        GameLingoLanguages.restoreProfile(c, data.optString("gameLanguage", ""), data.optString("targetLanguage", ""))
        val mode = data.optString("ocrMode", "")
        if (mode in setOf("classic", "automatic", "street")) GameLingoOCRConfig.setMode(c, mode)
        if (data.has("ocrFilter")) GameLingoOCRConfig.setFilterMenu(c, data.optBoolean("ocrFilter", GameLingoOCRConfig.filterMenuText))
        if (data.has("ocrExpand")) GameLingoOCRConfig.setExpandSearch(c, data.optBoolean("ocrExpand", GameLingoOCRConfig.expandSearch))
        val phoneScale = data.optDouble("phoneScale", Double.NaN).toFloat()
        if (phoneScale.isFinite() && phoneScale in 0.70f..1.80f) GameLingoPhoneSubtitleStyle.setScale(c, phoneScale)
        val phoneColor = data.optString("phoneColor", "")
        if (phoneColor in GameLingoPhoneSubtitleStyle.colors) GameLingoPhoneSubtitleStyle.setColor(c, phoneColor)
        val phoneBackground = data.optString("phoneBackground", "")
        if (phoneBackground in GameLingoPhoneSubtitleStyle.backgrounds) GameLingoPhoneSubtitleStyle.setBackground(c, phoneBackground)

        val p = layoutPrefs(c)
        val output = p.edit()
        for ((key, prefKey) in listOf(
            "volbyZap" to "volbyZap", "volbyText" to "volbyRezimText",
            "volbyZamek" to "volbyZamek", "yellowPreview" to "volbyUkazat",
            "dialogRucne" to "titRucne", "dialogZamek" to "titZamek",
            "greenLocked" to "rozvrhZamek"
        )) {
            if (data.has(key)) output.putBoolean(prefKey, data.optBoolean(key, p.getBoolean(prefKey, false)))
        }
        for ((key, prefix, fallback) in listOf(
            Triple("volbyOblast", "volby", Volby.VYCHOZI),
            Triple("dialogOblast", "tit", OblastTitulku.VYCHOZI)
        )) {
            val values = readArray(data, key, rect(p, prefix, fallback))
            listOf("L", "T", "R", "B").forEachIndexed { i, suffix ->
                output.putFloat(prefix + suffix, values[i])
            }
        }
        for ((key, min, max) in listOf(
            Triple("tvX", 0f, 1f), Triple("tvY", 0f, 1f),
            Triple("tvSirka", 0.15f, 1f), Triple("tvPismo", 0.6f, 2f)
        )) {
            val v = data.optDouble(key, Double.NaN).toFloat()
            if (v.isFinite() && v in min..max) output.putFloat(key, v)
        }
        output.apply()
        val currentOrange = GameLingoOrangeOutput.readFrom(p)
        val orange = data.optJSONArray("orangeOutput")?.takeIf { it.length() == 5 }
            ?.let { a -> FloatArray(5) { a.optDouble(it, Double.NaN).toFloat() } }
            ?.takeIf(GameLingoOrangeOutput::valid) ?: currentOrange
        GameLingoOrangeOutput.saveTo(p, orange, data.optBoolean("orangeLocked", GameLingoOrangeOutput.lockedFrom(p)))
        // Reload only the actual active layout; a later receiver selection loads its own prefs.
        Volby.nacti(c)
        OblastTitulku.nacti(c)
    }

    private fun canChangeProfile(activity: Activity): Boolean {
        if (!RemotePlayService.aktivni) return true
        AlertDialog.Builder(activity)
            .setMessage("Profil hry změň před spuštěním překladu.")
            .setPositiveButton("Rozumím", null).show()
        return false
    }

    fun show(activity: Activity, settings: Nastaveni, onApplied: () -> Unit) {
        // A paused session still owns its configuration and translation context.
        if (!canChangeProfile(activity)) return
        val saved = prefs(activity)
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            background = GameLingoVisualKit.pageBackground(activity)
        }
        val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(activity, "Profily her", 24f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(GameLingoHelp.button(activity, "profiles"),
            LinearLayout.LayoutParams(54, 54))
        outer.addView(heading)

        val dialog = android.app.Dialog(activity,
            android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val scroll = ScrollView(activity)
        scroll.addView(outer)
        val input = EditText(activity).apply {
            hint="Název hry";setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(muted)
            background=box(Color.parseColor("#172033"),radius=GameLingoVisualKit.dp(activity,11).toFloat())
            val pad=GameLingoVisualKit.dp(activity,10);setPadding(pad,pad,pad,pad)
        }
        val form=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL;background=GameLingoVisualKit.card(activity)
            val pad=GameLingoVisualKit.dp(activity,13);setPadding(pad,pad,pad,pad)
            addView(input,LinearLayout.LayoutParams(-1,-2))
        }
        val save=GameLingoVisualKit.cta(activity,"Uložit profil") {
            if(canChangeProfile(activity)) {
                val name=input.text.toString().trim()
                if(name.isEmpty()||name.length>64)input.error="Zadej název hry (max. 64 znaků)"
                else {
                    fun persist() {
                        if(canChangeProfile(activity)) {
                            saved.edit().putString(name,snapshot(activity,settings).toString()).apply()
                            dialog.dismiss();show(activity,settings,onApplied)
                        }
                    }
                    if(saved.contains(name))AlertDialog.Builder(activity).setMessage("Přepsat profil „"+name+"“?")
                        .setNegativeButton("Zrušit",null).setPositiveButton("Přepsat"){_,_->persist()}.show()
                    else persist()
                }
            }
        }
        form.addView(save,LinearLayout.LayoutParams(-1,-2).apply{topMargin=GameLingoVisualKit.dp(activity,10)})
        outer.addView(form)
        val names = saved.all.keys.sorted()
        if(names.isNotEmpty())outer.addView(button(activity,"Nepoužívat herní slovník") {
            if(canChangeProfile(activity)) {GameLingoGlossaryStore.activate(activity,null);dialog.dismiss();show(activity,settings,onApplied)}
        })
        names.forEach { name ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = GameLingoVisualKit.card(activity)
                val pad=GameLingoVisualKit.dp(activity,13);setPadding(pad,pad,pad,pad)
            }
            val title=LinearLayout(activity).apply {gravity=Gravity.CENTER_VERTICAL
                addView(GameLingoVisualKit.icon(activity,"gamecontroller",cyan),LinearLayout.LayoutParams(GameLingoVisualKit.dp(activity,25),GameLingoVisualKit.dp(activity,25)))
                addView(text(activity,name,16f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
                if(GameLingoGlossaryStore.active(activity)==name)addView(text(activity,"✓",18f,Color.parseColor("#35E57D"),true))
            }
            row.addView(title)
            row.addView(GameLingoVisualKit.detailRow(activity,"character.book.closed.fill","Herní slovník","") { GameLingoGlossaryUi.edit(activity,name) })
            val actions=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
            actions.addView(button(activity, "Použít") {
                if (!canChangeProfile(activity)) return@button
                val encoded = saved.getString(name, null)
                val data = runCatching { JSONObject(encoded ?: "") }.getOrNull()
                if (data != null) {
                    restore(activity, settings, data)
                    GameLingoGlossaryStore.activate(activity, name)
                    dialog.dismiss()
                    onApplied()
                }
            },LinearLayout.LayoutParams(0,-2,1f))
            actions.addView(button(activity, "Smazat") {
                AlertDialog.Builder(activity)
                    .setMessage("Smazat profil „$name“?")
                    .setPositiveButton("Smazat") { _: DialogInterface, _: Int ->
                        if (!canChangeProfile(activity)) return@setPositiveButton
                        saved.edit().remove(name).apply()
                        GameLingoGlossaryStore.remove(activity, name)
                        dialog.dismiss()
                        show(activity, settings, onApplied)
                    }
                    .setNegativeButton("Zrušit", null)
                    .show()
            },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(actions)
            outer.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 })
        }
        if (names.isEmpty()) {
            outer.addView(text(activity, "Zatím nemáš uložený žádný profil.", 14f, muted))
        }
        outer.addView(button(activity, "Zavřít") { dialog.dismiss() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 })
        dialog.setContentView(scroll)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
