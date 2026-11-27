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
