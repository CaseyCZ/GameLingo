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
 * English is the fallback selection for unsupported system languages.
 * Legacy upstream screens may still use Czech until localized.
 */
object GameLingoLanguages {
    data class Language(val code: String, val title: String)
    val appLanguages = listOf(
        Language("cs", "Čeština"), Language("sk", "Slovenština"), Language("en", "English"))
    val gameLanguages = listOf(
        Language("en", "English"), Language("de", "Deutsch"),
        Language("fr", "Français"), Language("es", "Español"),
        Language("it", "Italiano"), Language("pt", "Português"))
    val targetLanguages = listOf(
        Language("cs", "Čeština"), Language("sk", "Slovenština"),
        Language("en", "English"), Language("de", "Deutsch"),
        Language("pl", "Polski"), Language("fr", "Français"),
        Language("es", "Español"), Language("it", "Italiano"),
        Language("pt", "Português"))

    // App-owned static UI only; never translate OCR results, identifiers or user text.
    private val uiLabels = mapOf(
        "Jazyk aplikace" to Pair("Jazyk aplikácie", "App language"),
        "Jazyk hry" to Pair("Jazyk hry", "Game language"),
        "Jazyk překladu" to Pair("Jazyk prekladu", "Translation language"),
        "Jazyky GameLingo" to Pair("Jazyky GameLingo", "GameLingo languages"),
        "Zrušit" to Pair("Zrušiť", "Cancel"),
        "Hotovo" to Pair("Hotovo", "Done"),
        "Jazyk nabídky GameLingo" to Pair("Jazyk ponuky GameLingo", "GameLingo interface language"),
        "Původní jazyk dialogů a herního textu" to Pair("Pôvodný jazyk dialógov a herného textu", "Original language of dialogue and game text"),
        "Do jakého jazyka překládat" to Pair("Do akého jazyka prekladať", "Language to translate into"),
        "Vítej v GameLingo" to Pair("Vitaj v GameLingo", "Welcome to GameLingo"),
        "Jazyky a překlad" to Pair("Jazyky a preklad", "Languages and translation"),
        "Vyber zařízení" to Pair("Vyber zariadenie", "Choose devices"),
        "Překládej dialogy a herní texty přímo během hraní." to Pair("Prekladaj dialógy a herné texty priamo počas hrania.", "Translate dialogue and game text while playing."),
        "Překládat do" to Pair("Prekladať do", "Translate into"),
        "Tyto jazyky platí pro dialogy i herní texty. Změnit je můžeš později v Nastavení." to Pair("Tieto jazyky platia pre dialógy aj herné texty. Zmeniť ich môžeš neskôr v Nastaveniach.", "These languages apply to dialogue and game text. You can change them later in Settings."),
        "Vybraná konzole je uložená. Spojení ověříme při spuštění." to Pair("Vybraná konzola je uložená. Spojenie overíme pri spustení.", "Console saved. Connection will be checked when you start."),
        "Po výběru můžeš konzoli připojit v posledním kroku." to Pair("Po výbere môžeš konzolu pripojiť v poslednom kroku.", "Choose a console, then connect it below."),
        "Na telefonu" to Pair("Na telefóne", "On your phone"),
        "Spárovat TV" to Pair("Spárovať TV", "Pair TV"),
        "Připojit konzoli" to Pair("Pripojiť konzolu", "Connect console"),
        "Uložené zařízení neznamená aktivní Remote Play. Živé spojení a překlad se ověří až při spuštění." to Pair("Uložené zariadenie neznamená aktívne Remote Play. Živé spojenie a preklad sa overia až pri spustení.", "A saved device does not mean Remote Play is active. Connection and translation are checked when you start."),
        "Nastavit později" to Pair("Nastaviť neskôr", "Set up later"),
        "Zpět" to Pair("Späť", "Back"),
        "Pokračovat" to Pair("Pokračovať", "Continue"),
        "Dokončit nastavení" to Pair("Dokončiť nastavenie", "Finish setup"),
        "Vybraná televize nepodporuje šestimístné párování GameLingo TV. Pro tento výstup vyber Android TV nebo telefon." to Pair("Vybraný televízor nepodporuje šesťmiestne párovanie GameLingo TV. Pre tento výstup vyber Android TV alebo telefón.", "This TV does not support six-digit GameLingo TV pairing. Choose Android TV or your phone."),
        "Nastavení" to Pair("Nastavenia", "Settings"),
        "Zavřít nastavení" to Pair("Zavrieť nastavenia", "Close settings"),
        "JAZYKY" to Pair("JAZYKY", "LANGUAGES"),
        "ROZPOZNÁVÁNÍ A PŘEKLAD" to Pair("ROZPOZNÁVANIE A PREKLAD", "RECOGNITION AND TRANSLATION"),
        "ZOBRAZENÍ A PROFILY" to Pair("ZOBRAZENIE A PROFILY", "DISPLAY AND PROFILES"),
        "OBECNÉ" to Pair("VŠEOBECNÉ", "GENERAL"),
        "Jazyky" to Pair("Jazyky", "Languages"),
        "OCR a rozpoznávání" to Pair("OCR a rozpoznávanie", "OCR and recognition"),
        "Překlad" to Pair("Preklad", "Translation"),
        "Režimy, dialogy a časování" to Pair("Režimy, dialógy a časovanie", "Modes, dialogue and timing"),
        "Oblasti obrazovky" to Pair("Oblasti obrazovky", "Screen regions"),
        "Automatické zóny" to Pair("Automatické zóny", "Automatic zones"),
        "Čtyři nezávislé oblasti" to Pair("Štyri nezávislé oblasti", "Four independent regions"),
        "Standardní oblasti" to Pair("Štandardné oblasti", "Standard regions"),
        "Vzhled titulků" to Pair("Vzhľad titulkov", "Subtitle appearance"),
        "Velikost, šířka a poloha titulků" to Pair("Veľkosť, šírka a poloha titulkov", "Subtitle size, width and position"),
        "Profily her" to Pair("Profily hier", "Game profiles"),
        "Vlastní nastavení pro každou hru" to Pair("Vlastné nastavenia pre každú hru", "Custom settings for each game"),
        "Zařízení" to Pair("Zariadenia", "Devices"),
        "Historie překladů" to Pair("História prekladov", "Translation history"),
        "Poslední dialogy a herní texty · volitelné" to Pair("Posledné dialógy a herné texty · voliteľné", "Recent dialogue and game text · optional"),
        "Překladová cache" to Pair("Prekladová vyrovnávacia pamäť", "Translation cache"),
        "Ukládání a vymazání místních překladů" to Pair("Ukladanie a vymazanie miestnych prekladov", "Store and clear local translations"),
        "Diagnostika" to Pair("Diagnostika", "Diagnostics"),
        "Stav relace a uložených zařízení" to Pair("Stav relácie a uložených zariadení", "Session and saved device status"),
        "O aplikaci / Licence" to Pair("O aplikácii / Licencie", "About / Licenses"),
        "GameLingo, zdroje a AGPLv3" to Pair("GameLingo, zdroje a AGPLv3", "GameLingo, sources and AGPLv3"),
        "Pokročilé nastavení" to Pair("Pokročilé nastavenia", "Advanced settings"),
        "Citlivost, režim a časování" to Pair("Citlivosť, režim a časovanie", "Sensitivity, mode and timing"),
        "Nastavení GameLingo TV" to Pair("Nastavenia GameLingo TV", "GameLingo TV settings"),
        "Aplikace, hra, překlad" to Pair("Aplikácia, hra, preklad", "App, game, translation"),
        "Dialogy, herní texty, Story" to Pair("Dialógy, herné texty, Story", "Dialogue, game text, Story"),
        "Čtyři oblasti" to Pair("Štyri oblasti", "Four regions"),
        "Barvy, pozadí a velikost" to Pair("Farby, pozadie a veľkosť", "Colors, background and size"),
        "Hry, video a aplikace TV" to Pair("Hry, video a aplikácie TV", "Games, video and TV apps"),
        "Konzole a telefony" to Pair("Konzoly a telefóny", "Consoles and phones"),
        "Herní slovník" to Pair("Herný slovník", "Game glossary"),
        "Vlastní výrazy" to Pair("Vlastné výrazy", "Custom terms"),
        "Překlady na TV" to Pair("Preklady na TV", "Translations on TV"),
        "Ukládání a vymazání" to Pair("Ukladanie a vymazanie", "Storage and clearing"),
        "Příjem, oprávnění, verze" to Pair("Príjem, oprávnenia, verzia", "Receiver, permissions, version"),
        "Verze a licence" to Pair("Verzia a licencie", "Version and licenses"),
        "Nejdřív zastav překlad a příjem." to Pair("Najprv zastav preklad a príjem.", "Stop translation and receiving first."),
        "Povolit" to Pair("Povoliť", "Allow"),
        "Vymazat" to Pair("Vymazať", "Clear")
    )
    fun ui(c: Context, cs: String): String = when (app(c)) {
        "sk" -> uiLabels[cs]?.first ?: cs
        "en" -> uiLabels[cs]?.second ?: cs
        else -> cs
    }

    private const val PREFS = "gamelingo_languages"
    private val changes = java.util.concurrent.atomic.AtomicLong()
    val revision: Long get() = changes.get()

    private fun setTranslationLanguage(c: Context, key: String, code: String) {
        val old = if (key == "game") game(c) else target(c)
        if (old == code) return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, code).apply()
        changes.incrementAndGet()
    }
    private fun systemLanguage(c: Context): String = c.resources.configuration.locales[0].language
    fun systemApp(c: Context): String = systemLanguage(c).takeIf { code -> appLanguages.any { it.code == code } } ?: "en"
    private fun systemTarget(c: Context): String = systemLanguage(c).takeIf { code -> targetLanguages.any { it.code == code } } ?: "en"
    fun app(c: Context) = Nastaveni(c).jazyk?.takeIf { code -> appLanguages.any { it.code == code } } ?: systemApp(c)
    fun game(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("game", "en")?.takeIf { code ->
                gameLanguages.any { it.code == code }
            } ?: "en"
    fun target(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("target", null)?.takeIf { code ->
                targetLanguages.any { it.code == code }
            } ?: systemTarget(c)
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
            Kind.APP -> ui(activity, "Jazyk aplikace")
            Kind.GAME -> ui(activity, "Jazyk hry")
            Kind.TARGET -> ui(activity, "Jazyk překladu")
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
            }.setNegativeButton(ui(activity, "Zrušit"), null).show()
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
            body.addView(GameLingoTVUi.title(activity,ui(activity, "Jazyky GameLingo"),28f))
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (kind in Kind.values()) {
                val label = when (kind) {
                    Kind.APP -> ui(activity, "Jazyk aplikace")
                    Kind.GAME -> ui(activity, "Jazyk hry")
                    Kind.TARGET -> ui(activity, "Jazyk překladu")
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
            body.addView(GameLingoTVUi.action(activity,ui(activity, "Hotovo")) { dialog.dismiss() })
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
            .setTitle(ui(activity, "Jazyky GameLingo"))
            .setView(container)
            .setPositiveButton(ui(activity, "Hotovo"), null)
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
                        if (languages === appLanguages && !activity.isFinishing && !activity.isDestroyed) {
                            parent.dismiss()
                            show(activity, onChanged)
                        }
                    }.setNegativeButton(ui(activity, "Zrušit"), null).show()
            }
            container.addView(current,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = GameLingoVisualKit.dp(activity, 20)
                })
        }

        addPicker(ui(activity, "Jazyk aplikace"), ui(activity, "Jazyk nabídky GameLingo"),
            appLanguages, { app(activity) }) { code ->
            Nastaveni(activity).jazyk = code
        }
        addPicker(ui(activity, "Jazyk hry"), ui(activity, "Původní jazyk dialogů a herního textu"),
            gameLanguages, { game(activity) }) { code ->
            setTranslationLanguage(activity, "game", code)
        }
        addPicker(ui(activity, "Jazyk překladu"), ui(activity, "Do jakého jazyka překládat"),
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
