package cz.titulky.tv

/** Same monotonic state machine as iOS. Safe across story and standard workers. */
internal class GameLingoCircuitBreaker {
    data class Ticket internal constructor(val generation: Long, val probe: Boolean)
    private var generation = 0L
    private var failures = 0
    private var openUntil: Long? = null
    private var probing = false

    @Synchronized fun acquire(now: Long): Ticket? {
        val until = openUntil
        if (until != null) {
            if (now < until || probing) return null
            probing = true
            return Ticket(generation, true)
        }
        return Ticket(generation, false)
    }

    @Synchronized fun complete(ticket: Ticket, success: Boolean?, now: Long) {
        if (ticket.generation != generation) return
        if (ticket.probe) {
            if (!probing) return
            probing = false
            generation++
            if (success == true) {
                failures = 0
                openUntil = null
            } else openUntil = now + 30_000L
            return
        }
        if (openUntil != null || success == null) return
        if (success) { failures = 0; return }
        failures++
        if (failures >= 2) {
            generation++
            openUntil = now + 30_000L
        }
    }
}
