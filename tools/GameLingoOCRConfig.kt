package cz.titulky.tv

import android.content.Context

/**
 * Genuine mobile OCR options used by the existing RemotePlayService reader.
 * Preserves Android's proven 100 ms legacy sampling unless the user selects
 * another profile. Does not change Google TV OCR or the language catalog.
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
        sync()
    }

    private fun sync() {
        VyberTitulku.shortMode = mode == "street"
        VyberTitulku.filterMenuText = filterMenuText
        VyberTitulku.expandSearch = expandSearch && mode != "classic"
    }

    /** At most one OCR sampling opportunity at this interval; the existing
     * busy-reader guard still limits work under load. No fake OCR results.
     */
    fun intervalMs(): Long = when (mode) {
        "automatic" -> 85L
        "street" -> 70L
        else -> 100L
    }

    fun setMode(context: Context, selected: String) {
        require(selected in modes)
        mode = selected
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("mode", selected).apply()
        sync()
    }

    fun setFilterMenu(context: Context, selected: Boolean) {
        filterMenuText = selected
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("filterMenuText", selected).apply()
        sync()
    }

    fun setExpandSearch(context: Context, selected: Boolean) {
        expandSearch = selected
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("expandSearch", selected).apply()
        sync()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
        mode = "classic"
        filterMenuText = true
        expandSearch = false
        sync()
    }
}
