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
    @Volatile var expandSearch: Boolean = false
        private set

    fun load(context: Context) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = store.getString("mode", "classic")
            ?.takeIf { it in modes } ?: "classic"
        filterMenuText = store.getBoolean("filterMenuText", true)
        expandSearch = store.getBoolean("expandSearch", false)
    }

    /** At most one OCR sampling opportunity at this interval; the existing
     * busy-reader guard still limits work under load. No fake OCR results.
     */
    fun intervalMs(): Long = GameLingoOCRCadence.dialogue(mode, true, false)

    fun tvIntervalMs(lowPower: Boolean): Long =
        GameLingoOCRCadence.dialogue(mode, false, lowPower)

    fun gameTextIntervalMs(asText: Boolean, lowPower: Boolean, recentCostMs: Long): Long =
        GameLingoOCRCadence.gameText(mode, asText, lowPower, recentCostMs)

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
        expandSearch = false
    }
}
