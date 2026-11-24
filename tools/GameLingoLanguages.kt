package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Three distinct settings: app UI, OCR/game source, translated output.
 * The app languages below are limited to the actually localized upstream
 * interface (Czech and Slovak). Source/target apply to real translation.
 */
object GameLingoLanguages {
    data class Language(val code: String, val title: String)
    val appLanguages = listOf(
        Language("cs", "Čeština"), Language("sk", "Slovenština"))
    val gameLanguages = listOf(
        Language("en", "Angličtina"), Language("de", "Němčina"),
        Language("fr", "Francouzština"), Language("es", "Španělština"),
        Language("it", "Italština"), Language("pt", "Portugalština"))
    val targetLanguages = listOf(
        Language("cs", "Čeština"), Language("sk", "Slovenština"),
        Language("en", "Angličtina"), Language("de", "Němčina"),
        Language("pl", "Polština"), Language("fr", "Francouzština"),
        Language("es", "Španělština"), Language("it", "Italština"),
        Language("pt", "Portugalština"))

    private const val PREFS = "gamelingo_languages"
    private val changes = java.util.concurrent.atomic.AtomicLong()
    val revision: Long get() = changes.get()

    private fun setTranslationLanguage(c: Context, key: String, code: String) {
        val old = if (key == "game") game(c) else target(c)
        if (old == code) return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, code).apply()
        changes.incrementAndGet()
    }
    fun app(c: Context) = Nastaveni(c).jazykNeboCs
    fun game(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("game", "en")?.takeIf { code ->
                gameLanguages.any { it.code == code }
            } ?: "en"
    fun target(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("target", "cs")?.takeIf { code ->
                targetLanguages.any { it.code == code }
            } ?: "cs"
    /** Restore only validated, existing translation languages; never change UI language. */
    fun restoreProfile(c: Context, source: String?, target: String?) {
        if (source != null && gameLanguages.any { it.code == source }) setTranslationLanguage(c, "game", source)
        if (target != null && targetLanguages.any { it.code == target }) setTranslationLanguage(c, "target", target)
    }
    fun name(choices: List<Language>, code: String) =
        choices.firstOrNull { it.code == code }?.title ?: code

    enum class Kind { APP, GAME, TARGET }

    /** Same one-field selection behavior as the iOS SwiftUI pickers. */
    fun choose(activity: Activity, kind: Kind, onChanged: () -> Unit = {}) {
        val choices = when (kind) {
            Kind.APP -> appLanguages
            Kind.GAME -> gameLanguages
            Kind.TARGET -> targetLanguages
        }
        val title = when (kind) {
            Kind.APP -> "Jazyk aplikace"
            Kind.GAME -> "Jazyk hry"
            Kind.TARGET -> "Jazyk překladu"
        }
        val current = when (kind) {
            Kind.APP -> app(activity)
            Kind.GAME -> game(activity)
            Kind.TARGET -> target(activity)
        }
        val index = choices.indexOfFirst { it.code == current }.coerceAtLeast(0)
        fun applyChoice(choice: Int) {
            val selected = choices[choice].code
            when (kind) {
                Kind.APP -> Nastaveni(activity).jazyk = selected
                Kind.GAME -> setTranslationLanguage(activity, "game", selected)
                Kind.TARGET -> setTranslationLanguage(activity, "target", selected)
            }
            onChanged()
        }
        if (GameLingoTVUi.isTV(activity)) {
            GameLingoTVUi.select(activity, title, choices.map { it.title }.toTypedArray(), index, ::applyChoice)
        } else AlertDialog.Builder(activity)
            .setTitle(title)
            .setSingleChoiceItems(choices.map { it.title }.toTypedArray(), index) { picker, choice ->
                picker.dismiss(); applyChoice(choice)
            }.setNegativeButton("Zrušit", null).show()
    }

    fun show(activity: Activity, onChanged: () -> Unit = {}) {
        if (activity.isFinishing) return
        if (GameLingoTVUi.isTV(activity)) {
            val dialog = android.app.Dialog(activity)
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(GameLingoTVUi.dp(activity,20),GameLingoTVUi.dp(activity,20),
                    GameLingoTVUi.dp(activity,20),GameLingoTVUi.dp(activity,20))
                background = android.graphics.drawable.ColorDrawable(Color.parseColor("#080E1B"))
            }
            body.addView(GameLingoTVUi.title(activity,"Jazyky GameLingo",28f))
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (kind in Kind.values()) {
                val label = when (kind) {
                    Kind.APP -> "Jazyk aplikace"
                    Kind.GAME -> "Jazyk hry"
                    Kind.TARGET -> "Jazyk překladu"
                }
                val value = when (kind) {
                    Kind.APP -> name(appLanguages,app(activity))
                    Kind.GAME -> name(gameLanguages,game(activity))
                    Kind.TARGET -> name(targetLanguages,target(activity))
                }
                row.addView(GameLingoTVUi.tile(activity,"◎",label,value) {
                    choose(activity,kind) { dialog.dismiss(); onChanged(); show(activity,onChanged) }
                }, LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
                    .apply { rightMargin = GameLingoTVUi.dp(activity,10) })
            }
            body.addView(row)
            body.addView(GameLingoTVUi.action(activity,"Hotovo") { dialog.dismiss() })
            dialog.setContentView(body); dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .75f).toInt(),ViewGroup.LayoutParams.WRAP_CONTENT)
            row.getChildAt(0).requestFocus()
            return
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = GameLingoVisualKit.dp(activity, 21)
            setPadding(pad, pad, pad, pad)
        }
        val parent = AlertDialog.Builder(activity)
            .setTitle("Jazyky GameLingo")
            .setView(container)
            .setPositiveButton("Hotovo", null)
            .create()

        fun addPicker(title: String, caption: String, languages: List<Language>,
                      selected: () -> String, onSelect: (String) -> Unit) {
            val heading = TextView(activity).apply {
                text = title
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            }
            container.addView(heading)
            container.addView(TextView(activity).apply {
                text = caption
                textSize = 12.5f
                setTextColor(Color.parseColor("#9FB1D4"))
                setPadding(0, 2, 0, 8)
            })
            val current = TextView(activity).apply {
                text = name(languages, selected()) + "    ▾"
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = GameLingoVisualKit.dp(activity, 55)
                background = GameLingoVisualKit.card(activity)
                val pad = GameLingoVisualKit.dp(activity, 13)
                setPadding(pad, 0, pad, 0)
                isClickable = true
                isFocusable = true
            }
            current.setOnClickListener {
                val selectedIndex = languages.indexOfFirst {
                    it.code == selected()
                }.coerceAtLeast(0)
                AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setSingleChoiceItems(languages.map { it.title }.toTypedArray(),
                        selectedIndex) { picker, index ->
                        onSelect(languages[index].code)
                        current.text = languages[index].title + "    ▾"
                        picker.dismiss()
                        onChanged()
                    }.setNegativeButton("Zrušit", null).show()
            }
            container.addView(current,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = GameLingoVisualKit.dp(activity, 20)
                })
        }

        addPicker("Jazyk aplikace", "Jazyk nabídky GameLingo",
            appLanguages, { app(activity) }) { code ->
            Nastaveni(activity).jazyk = code
        }
        addPicker("Jazyk hry", "Původní jazyk dialogů a herního textu",
            gameLanguages, { game(activity) }) { code ->
            setTranslationLanguage(activity, "game", code)
        }
        addPicker("Jazyk překladu", "Do jakého jazyka překládat",
            targetLanguages, { target(activity) }) { code ->
            setTranslationLanguage(activity, "target", code)
        }
        parent.show()
        parent.window?.setBackgroundDrawableResource(android.R.color.background_dark)
        parent.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
