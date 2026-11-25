package cz.titulky.tv

/** Session-local display context. Never stored in history or provider cache. */
internal class GameLingoDialogueWindow {
    private data class Entry(val source: String, val translation: String, val at: Long)
    private val entries = ArrayList<Entry>(2)
    private var context = ""

    @Synchronized fun reset() { entries.clear(); context = "" }

    @Synchronized fun render(source: String, translation: String, enabled: Boolean,
                             now: Long, contextKey: String, correction: Boolean = false): String {
        if (!enabled || translation.isBlank()) { reset(); return translation }
        if (context != contextKey) { reset(); context = contextKey }
        val key = source.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
        entries.removeAll { now < it.at || now - it.at > 12000L }
        val last = entries.lastOrNull()
        if (last != null && (correction || last.source == key ||
            (minOf(last.source.length, key.length) >= 8 &&
                (last.source.startsWith(key + " ") || key.startsWith(last.source + " "))))) entries.removeAt(entries.lastIndex)
        entries.add(Entry(key, translation, now))
        while (entries.size > 2) entries.removeAt(0)
        return if (entries.size == 2 && entries[0].translation != translation)
            entries[0].translation + "\n" + translation else translation
    }
}
