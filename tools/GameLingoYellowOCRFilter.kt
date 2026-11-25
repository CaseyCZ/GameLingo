package cz.titulky.tv

/** Opt-in filter for explicit controller prompts, never generic menu labels. */
internal object GameLingoYellowOCRFilter {
    private val prompt = Regex("""(?i)^(?:press|hold|tap) +(?:button +)?(?:A|B|X|Y|LB|RB|LT|RT|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU|\[(?:A|B|X|Y|LB|RB|LT|RT|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU)\])(?: +to +(?:continue|confirm|skip|select|go back))?[.!]?$""")

    fun keeps(text: String, enabled: Boolean): Boolean =
        !enabled || !prompt.matches(text.trim())
}
