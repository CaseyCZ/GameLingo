package cz.titulky.tv

/** Opt-in filter for explicit controller prompts, never generic menu labels. */
internal object GameLingoIgnoredLines {
    private fun normalize(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
    fun entries(raw: String): Set<String> =
        raw.take(8192).lineSequence().take(100).map(::normalize).filter { it.isNotEmpty() }.toSet()
    fun keeps(text: String, entries: Set<String>): Boolean = normalize(text) !in entries
}

internal object GameLingoYellowOCRFilter {
    private val prompt = Regex("""(?i)^(?:press|hold|tap) +(?:button +)?(?:A|B|X|Y|LB|RB|LT|RT|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU|\[(?:A|B|X|Y|LB|RB|LT|RT|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU)\])(?: +to +(?:continue|confirm|skip|select|go back))?[.!]?$""")

    // The explicit list is independent of the optional controller prompt filter.
    fun keepsAutomaticDialogue(text: String, filterMenus: Boolean, ignored: Set<String>): Boolean =
        GameLingoIgnoredLines.keeps(text, ignored) && keeps(text, filterMenus)

    fun keeps(text: String, enabled: Boolean): Boolean =
        !enabled || !prompt.matches(text.trim())
}
