package cz.titulky.tv

/** Session-local display context. Never stored in history or provider cache. */
internal class GameLingoDialogueWindow {
    private data class Entry(val source: String, val translation: String, val at: Long)
    private val entries = ArrayList<Entry>(2)
    private var context = ""

    @Synchronized fun reset() { entries.clear(); context = "" }

    @Synchronized fun render(source: String, translation: String, enabled: Boolean,
                             now: Long, contextKey: String, correction: Boolean = false, previousCount: Int = 1): String {
        if (!enabled || translation.isBlank()) { reset(); return translation }
        if (context != contextKey) { reset(); context = contextKey }
        val key = GameLingoDialogueText.normalize(source)
        entries.removeAll { now < it.at || now - it.at > 12000L }
        val last = entries.lastOrNull()
        if (last != null && (correction || last.source == key ||
            (minOf(last.source.length, key.length) >= 8 &&
                (last.source.startsWith(key + " ") || key.startsWith(last.source + " "))))) entries.removeAt(entries.lastIndex)
        entries.add(Entry(key, translation, now))
        while (entries.size > previousCount.coerceIn(1, 2) + 1) entries.removeAt(0)
        val seen = hashSetOf(translation)
        val previous = ArrayList<String>(2)
        var remaining = 300
        for (entry in entries.dropLast(1).asReversed()) {
            if (seen.add(entry.translation) && entry.translation.length <= remaining) {
                previous.add(entry.translation)
                remaining -= entry.translation.length
            }
        }
        return (previous.asReversed() + translation).joinToString("\n")
    }
}


/** Two or more real empty reads spanning 800 ms, not skipped frames.
 * A readable frame cancels the pending absence; explicit stop resets it. */
internal class GameLingoDialogueAbsence {
    private var since: Long? = null
    fun reset() { since = null }
    fun observeEmpty(now: Long): Boolean {
        val first = since
        if (first == null || now < first) {
            since = now
            return false
        }
        return now - first >= 800L
    }
}
