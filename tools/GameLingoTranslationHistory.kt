package cz.titulky.tv

/** Process-local opt-in history, separate from the translation cache and log. */
internal object GameLingoTranslationHistory {
    data class Entry(val time: Long, val kind: String, val source: String, val target: String,
                     val original: String, val translated: String)
    private const val LIMIT = 200
    private val entries = ArrayDeque<Entry>()
    private var recording = false
    @Synchronized fun enabled() = recording
    @Synchronized fun setEnabled(value: Boolean) {
        recording = value
        if (!value) entries.clear()
    }
    @Synchronized fun clear() { entries.clear() }
    @Synchronized fun snapshot(): List<Entry> = entries.toList().asReversed()
    private fun bounded(text: String): String {
        if (text.length <= 4096) return text
        var end = 4095
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end) + "…"
    }
    @Synchronized fun append(kind: String, source: String, target: String,
                             original: String, translated: String) {
        if (!recording || original.isBlank() || translated.isBlank()) return
        val input = bounded(original)
        val output = bounded(translated)
        val last = entries.lastOrNull()
        if (last != null && last.kind == kind && last.source == source && last.target == target &&
            last.original == input && last.translated == output) return
        if (entries.size >= LIMIT) entries.removeFirst()
        entries.addLast(Entry(System.currentTimeMillis(), kind, source, target, input, output))
    }
}
