package cz.titulky.tv

/** Canonicalize layout whitespace only. Case and punctuation can change meaning.
 * Used for a recognized dialogue episode, not to suppress a later repetition. */
internal object GameLingoDialogueText {
    private val whitespace = Regex("[\\s\\p{Z}]+")
    fun normalize(raw: String): String = raw.replace(whitespace, " ").trim().take(240)

    // Keep classic geometry untouched. The explicitly selected short-dialogue
    // mode can pass two-letter lines; numeric noise and isolated glyphs cannot.
    fun readableLine(text: String, shortMode: Boolean): Boolean =
        text.count(Char::isLetter) >= if (shortMode) 2 else 3
}


/** Shield explicit OCR-recognized buttons, never infer an icon from plain letters. */
internal class GameLingoControllerSymbols(original: String) {
    companion object {
        private val pattern = Regex("""\[(?:A|B|X|Y|LB|RB|LT|RT|LS|RS|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU|SHARE|VIEW)\]|[△○×□]""")
        fun symbols(text: String): List<String> = pattern.findAll(text).map { it.value }.sorted().toList()
        fun valid(original: String, translated: String): Boolean = symbols(original).let { it.isEmpty() || it == symbols(translated) }
    }
    val input: String
    private val pairs: List<Pair<String,String>>
    init {
        var prefix = "GLBUTTON"
        while (original.contains(prefix, ignoreCase = true)) prefix += "Z"
        val tokens = mutableListOf<Pair<String,String>>()
        var index = 0
        input = pattern.replace(original) { match ->
            val marker = prefix + index++ + "TOKEN"
            tokens.add(marker to match.value)
            marker
        }
        pairs = tokens
    }
    fun restore(translated: String): String? {
        var result = translated
        for ((marker,symbol) in pairs) {
            val start = result.indexOf(marker, ignoreCase = true)
            if (start < 0 || result.indexOf(marker, start + marker.length, ignoreCase = true) >= 0) return null
            result = result.replaceRange(start, start+marker.length, symbol)
        }
        if (pairs.isNotEmpty() && symbols(result) != pairs.map { it.second }.sorted()) return null
        return result
    }
}
