package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class GameLingoCircuitBreakerTest {
    @Test fun twoFailuresOneProbeAndRecovery() {
        val primary = GameLingoCircuitBreaker()
        val fallback = GameLingoCircuitBreaker()
        val late = primary.acquire(0)!!
        primary.complete(primary.acquire(0)!!, false, 1)
        primary.complete(primary.acquire(1)!!, false, 2)
        primary.complete(late, true, 3)
        assertNull(primary.acquire(30_001))
        assertNotNull(fallback.acquire(3))
        val probe = primary.acquire(30_002)!!
        assertNull(primary.acquire(30_003))
        primary.complete(probe, false, 30_004)
        assertNull(primary.acquire(60_003))
        val cancelled = primary.acquire(60_004)!!
        primary.complete(cancelled, null, 60_005)
        assertNull(primary.acquire(90_004))
        primary.complete(primary.acquire(90_005)!!, true, 90_006)
        primary.complete(primary.acquire(90_006)!!, false, 90_007)
        primary.complete(primary.acquire(90_007)!!, true, 90_008)
        primary.complete(primary.acquire(90_008)!!, false, 90_009)
        assertNotNull(primary.acquire(90_009))
    }

    @Test fun concurrentHalfOpenIsExclusive() {
        val breaker = GameLingoCircuitBreaker()
        breaker.complete(breaker.acquire(0)!!, false, 0)
        breaker.complete(breaker.acquire(0)!!, false, 0)
        val allowed = java.util.concurrent.atomic.AtomicInteger()
        val ready = java.util.concurrent.CountDownLatch(1)
        val workers = List(16) {
            Thread {
                ready.await()
                if (breaker.acquire(30_000) != null) allowed.incrementAndGet()
            }.apply { start() }
        }
        ready.countDown()
        workers.forEach { it.join() }
        assertEquals(1, allowed.get())
    }
}
