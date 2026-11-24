package cz.titulky.tv

/** Main-thread owned admission for game-choice requests. A running request
 * retains its slot until completion, even when its UI generation is stale.
 * This prevents A-B-A OCR changes from starting a second identical request. */
internal class GameLingoChoiceFlights<K>(
    private val limit: Int = 8,
    private val retryDelayMs: Long = 2000,
    private val now: () -> Long = android.os.SystemClock::elapsedRealtime
) {
    private val active = HashMap<K, Long>()
    private val retryAfter = LinkedHashMap<K, Long>()
    private var sequence = 0L

    fun begin(key: K): Long? {
        val time = now()
        retryAfter.entries.removeAll { it.value <= time }
        if (active.size >= limit || active.containsKey(key) || retryAfter.containsKey(key)) return null
        return (++sequence).also { active[key] = it }
    }

    fun finish(key: K, ticket: Long, succeeded: Boolean): Boolean {
        if (active[key] != ticket) return false
        active.remove(key)
        if (!succeeded) {
            retryAfter[key] = now() + retryDelayMs
            while (retryAfter.size > 64) retryAfter.remove(retryAfter.keys.first())
        }
        return true
    }
}
