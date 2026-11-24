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

    private fun snapshot(c: Context, settings: Nastaveni) = JSONObject().apply {
        put("dialogy", settings.dialogy)
        put("pribehovy", settings.pribehovy)
        put("cas", settings.casPribehuProcenta)
        put("prekryvat", settings.prekryvat)
        put("gameLanguage", GameLingoLanguages.game(c))
        put("targetLanguage", GameLingoLanguages.target(c))
        GameLingoOCRConfig.load(c)
        put("ocrMode", GameLingoOCRConfig.mode)
        put("ocrFilter", GameLingoOCRConfig.filterMenuText)
        put("ocrExpand", GameLingoOCRConfig.expandSearch)
        put("phoneScale", GameLingoPhoneSubtitleStyle.scale(c).toDouble())
        put("phoneColor", GameLingoPhoneSubtitleStyle.color(c))
        put("phoneBackground", GameLingoPhoneSubtitleStyle.background(c))
        put("volbyZap", Volby.zapnuto)
        put("volbyText", Volby.rezimText)
        put("volbyZamek", Volby.zamceno)
        put("volbyOblast", array(Volby.oblast))
        put("dialogRucne", OblastTitulku.rucne)
        put("dialogZamek", OblastTitulku.zamceno)
        put("dialogOblast", array(OblastTitulku.oblast))
        // Output position/font are persisted in the current TV profile.
        val p = TvProfily.nastaveni(c)
        put("tvX", p.getFloat("tvX", 0.5f).toDouble())
        put("tvY", p.getFloat("tvY", 0.9f).toDouble())
        put("tvSirka", p.getFloat("tvSirka", 0.75f).toDouble())
        put("tvPismo", p.getFloat("tvPismo", 1f).toDouble())
    }

    private fun restore(c: Context, settings: Nastaveni, data: JSONObject) {
        settings.dialogy = data.optBoolean("dialogy", settings.dialogy)
        settings.pribehovy = data.optBoolean("pribehovy", settings.pribehovy)
        settings.casPribehuProcenta = data.optInt("cas", settings.casPribehuProcenta)
        settings.prekryvat = data.optBoolean("prekryvat", settings.prekryvat)
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

        Volby.nacti(c)
        Volby.zapnuto = data.optBoolean("volbyZap", Volby.zapnuto)
        Volby.rezimText = data.optBoolean("volbyText", Volby.rezimText)
        Volby.zamceno = data.optBoolean("volbyZamek", Volby.zamceno)
        Volby.oblast = readArray(data, "volbyOblast", Volby.oblast)
        Volby.uloz(c)

        OblastTitulku.nacti(c)
        OblastTitulku.rucne = data.optBoolean("dialogRucne", OblastTitulku.rucne)
        OblastTitulku.zamceno = data.optBoolean("dialogZamek", OblastTitulku.zamceno)
        OblastTitulku.oblast = readArray(data, "dialogOblast", OblastTitulku.oblast)
        OblastTitulku.uloz(c)

        val output = TvProfily.nastaveni(c).edit()
        for ((key, min, max) in listOf(
            Triple("tvX", 0f, 1f), Triple("tvY", 0f, 1f),
            Triple("tvSirka", 0.15f, 1f), Triple("tvPismo", 0.6f, 2f)
        )) {
            val v = data.optDouble(key, Double.NaN).toFloat()
            if (v.isFinite() && v in min..max) output.putFloat(key, v)
        }
        output.apply()
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
        outer.addView(GameLingoVisualKit.hero(activity), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 })
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
        val names = saved.all.keys.sorted()
        names.forEach { name ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GameLingoVisualKit.card(activity)
                setPadding(12, 10, 12, 10)
            }
            row.addView(text(activity, name, 16f, Color.WHITE, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(button(activity, "Použít") {
                if (!canChangeProfile(activity)) return@button
                val encoded = saved.getString(name, null)
                val data = runCatching { JSONObject(encoded ?: "") }.getOrNull()
                if (data != null) {
                    restore(activity, settings, data)
                    GameLingoGlossaryStore.activate(activity, name)
                    dialog.dismiss()
                    onApplied()
                }
            })
            row.addView(button(activity, "Slovník") {
                GameLingoGlossaryUi.edit(activity, name)
            })
            row.addView(button(activity, "×") {
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
            })
            outer.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 })
        }
        if (names.isEmpty()) {
            outer.addView(text(activity, "Zatím nemáš uložený žádný profil.", 14f, muted))
        }
        outer.addView(button(activity, "Bez herního slovníku") {
            if (canChangeProfile(activity)) GameLingoGlossaryStore.activate(activity, null)
        })
        outer.addView(button(activity, "＋  Uložit aktuální nastavení") {
            val input = EditText(activity).apply {
                hint = "Název hry"
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(muted)
            }
            val container = LinearLayout(activity).apply {
                setPadding(24, 8, 24, 8)
                addView(input)
            }
            AlertDialog.Builder(activity)
                .setTitle("Uložit profil hry")
                .setView(container)
                .setPositiveButton("Uložit") { _, _ ->
                    if (!canChangeProfile(activity)) return@setPositiveButton
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty() && name.length <= 64) {
                        // Objects are loaded from the active TV profile, not
                        // from a fixed global preference store.
                        Volby.nacti(activity)
                        OblastTitulku.nacti(activity)
                        saved.edit().putString(name, snapshot(activity, settings).toString()).apply()
                        dialog.dismiss()
                        show(activity, settings, onApplied)
                    }
                }
                .setNegativeButton("Zrušit", null)
                .show()
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 })
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
