package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class GameLingoPerformanceWindowTest {
    @Test fun boundedWindowAndPercentile() {
        val window = GameLingoPerformanceWindow(3)
        assertEquals("–", window.summary())
        window.record(-1)
        for (n in listOf(10L, 20L, 30L, 40L)) window.record(n)
        assertEquals("30 / 40 ms (n=4)", window.summary())
        repeat(1000) { window.record(7) }
        assertEquals("7 / 7 ms (n=1004)", window.summary())
    }
}
