package cz.titulky.tv

import android.content.Context

/**
 * OCR options for mobile and direct-on-TV readers. The receiver-only TV
 * does not run OCR. Preserve stable defaults and the TV low-power guard.
 */
object GameLingoOCRConfig {
    private const val PREFS = "gamelingo_ocr_v05"
    private val modes = setOf("classic", "automatic", "street")
    @Volatile var mode: String = "classic"
        private set
    @Volatile var filterMenuText: Boolean = true
        private set
    @Volatile var filterChoiceControls: Boolean = false
        private set
    @Volatile var minBlueHeight: Int = 0
        private set
    @Volatile var ignoredBlueLines: String = ""
        private set
    @Volatile var yellowOCRMode: String = "follow"
        private set
    @Volatile var minYellowHeight: Int = 0
        private set
    @Volatile var ignoredYellowLines: String = ""
        private set
    @Volatile internal var ignoredBlueEntries: Set<String> = emptySet()
        private set
    @Volatile internal var ignoredYellowEntries: Set<String> = emptySet()
        private set
    @Volatile var expandSearch: Boolean = false
        private set

    private fun tv(context: Context) = context.getSharedPreferences("gamelingo_tv_parity", Context.MODE_PRIVATE)
    val regionKeys = arrayOf("legacy","narrow","center","wide")
    val regionLabels = arrayOf("Původní automatika","Úzká – pouze titulky","Střed – doporučeno","Široká – původní")
    fun region(context: Context) = tv(context).getString("region","legacy")?.takeIf { it in regionKeys } ?: "legacy"
    fun setRegion(context: Context, key: String) { require(key in regionKeys); tv(context).edit().putString("region",key).apply() }
    // iOS Vision rectangles use a bottom-left origin; Android uses top-left.
    fun preset(context: Context): FloatArray? = when(region(context)) {
        "narrow" -> floatArrayOf(.23f,.73f,.77f,.90f)
        "center" -> floatArrayOf(.15f,.70f,.85f,.93f)
        "wide" -> floatArrayOf(.04f,.63f,.96f,.97f)
        else -> null
    }
    fun showPreviousDialogue(context: Context) = tv(context).getBoolean("showPreviousDialogue", false)
    fun setShowPreviousDialogue(context: Context, value: Boolean) {
        tv(context).edit().putBoolean("showPreviousDialogue", value).apply()
    }
    fun fixedTiming(context: Context) = tv(context).getBoolean("fixedTiming",false)
    fun seconds(context: Context) = tv(context).getInt("seconds",6).coerceIn(3,10)
    fun skipOld(context: Context) = tv(context).getBoolean("skipOld",false)
    fun maxAge(context: Context) = tv(context).getInt("maxAge",5).coerceIn(2,8)
    fun saveTiming(context: Context, fixed: Boolean = fixedTiming(context), duration: Int = seconds(context),
                   skip: Boolean = skipOld(context), age: Int = maxAge(context)) {
        tv(context).edit().putBoolean("fixedTiming",fixed).putInt("seconds",duration.coerceIn(3,10))
            .putBoolean("skipOld",skip).putInt("maxAge",age.coerceIn(2,8)).apply()
    }
    // Existing games keep their original time unless fixed timing is selected.
    fun displayMs(context: Context, original: Long): Long =
        if(fixedTiming(context)) seconds(context)*1000L else original
    fun expired(context: Context, ageMs: Long): Boolean = skipOld(context) && ageMs > maxAge(context)*1000L

    fun load(context: Context) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = store.getString("mode", "classic")
            ?.takeIf { it in modes } ?: "classic"
        filterMenuText = store.getBoolean("filterMenuText", true)
        filterChoiceControls = store.getBoolean("filterChoiceControls", false)
        minBlueHeight = store.getInt("minBlueHeight", 0).coerceIn(0, 5)
        ignoredBlueLines = store.getString("ignoredBlueLines", "").orEmpty()
        yellowOCRMode = store.getString("yellowOCRMode", "follow")?.takeIf { it in modes } ?: "follow"
        minYellowHeight = store.getInt("minYellowHeight", 0).coerceIn(0, 5)
        ignoredYellowLines = store.getString("ignoredYellowLines", "").orEmpty()
        ignoredBlueEntries = GameLingoIgnoredLines.entries(ignoredBlueLines)
        ignoredYellowEntries = GameLingoIgnoredLines.entries(ignoredYellowLines)
        expandSearch = store.getBoolean("expandSearch", false)
    }

    /** At most one OCR sampling opportunity at this interval; the existing
     * busy-reader guard still limits work under load. No fake OCR results.
     */
    fun intervalMs(): Long = GameLingoOCRCadence.dialogue(mode, true, false)

    fun tvIntervalMs(lowPower: Boolean): Long =
        GameLingoOCRCadence.dialogue(mode, false, lowPower)

    fun gameTextIntervalMs(asText: Boolean, lowPower: Boolean, recentCostMs: Long): Long =
        GameLingoOCRCadence.gameText(if (yellowOCRMode == "follow") mode else yellowOCRMode, asText, lowPower, recentCostMs)

    fun standaloneYellowReady(ageMs: Long): Boolean = yellowOCRMode == "follow" ||
        ageMs >= GameLingoOCRCadence.standaloneYellowInterval(yellowOCRMode)

    fun minimumDialogueLength(): Int = if (mode == "street") 2 else 4

    fun title(): String = when (mode) {
        "automatic" -> "Automatický · vyvážený"
        "street" -> "Krátké dialogy · rychlý"
        else -> "Původní · stabilní"
    }

    fun setMode(context: Context, selected: String) {
        require(selected in modes)
        mode = selected
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("mode", selected).apply()
    }

    fun setFilterMenu(context: Context, selected: Boolean) {
        filterMenuText = selected
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("filterMenuText", selected).apply()
    }

    fun setFilterChoiceControls(context: Context, selected: Boolean) {
        filterChoiceControls = selected
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("filterChoiceControls", selected).apply()
    }

    fun setMinBlueHeight(context: Context, value: Int) {
        minBlueHeight = value.coerceIn(0, 5)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("minBlueHeight", minBlueHeight).apply()
    }

    fun setYellowOCRMode(context: Context, value: String) {
        yellowOCRMode = value.takeIf { it in modes } ?: "follow"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("yellowOCRMode", yellowOCRMode).apply()
    }

    fun setMinYellowHeight(context: Context, value: Int) {
        minYellowHeight = value.coerceIn(0, 5)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("minYellowHeight", minYellowHeight).apply()
    }

    fun setIgnoredLines(context: Context, yellow: Boolean, raw: String) {
        val value = raw.take(8192)
        val entries = GameLingoIgnoredLines.entries(value)
        if (yellow) { ignoredYellowLines = value; ignoredYellowEntries = entries }
        else { ignoredBlueLines = value; ignoredBlueEntries = entries }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(if (yellow) "ignoredYellowLines" else "ignoredBlueLines", value).apply()
    }

    fun setExpandSearch(context: Context, selected: Boolean) {
        expandSearch = selected
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("expandSearch", selected).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
        mode = "classic"
        filterMenuText = true
        filterChoiceControls = false
        minBlueHeight = 0
        minYellowHeight = 0
        yellowOCRMode = "follow"
        ignoredBlueLines = ""; ignoredYellowLines = ""
        ignoredBlueEntries = emptySet(); ignoredYellowEntries = emptySet()
        expandSearch = false
    }
}
