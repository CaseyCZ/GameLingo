package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class GameLingoPerformanceWindowTest {
    @Test fun phasesRemainSeparateAndUnknownNamesAreIgnored() {
        val phases = GameLingoPhaseMetrics(listOf("OCR", "Queue"))
        phases.record("OCR", 20L)
        phases.record("Queue", 5L)
        phases.record("unregistered", 900L)
        assertEquals("OCR: 20 / 20 ms (n=1)\nQueue: 5 / 5 ms (n=1)", phases.summary())
    }
    @Test fun networkRateUsesCounterDeltasAndDoesNotInventMissingValues() {
        val net = GameLingoXboxNetworkMetrics()
        net.record(GameLingoXboxNetworkMetrics.Sample("a",0L,1000.0,100.0,10.0,0.003,0.04))
        assertFalse(net.summary(0L).contains("kb/s"))
        net.record(GameLingoXboxNetworkMetrics.Sample("a",1000L,2000.0,190.0,20.0,0.003,0.04))
        val summary = net.summary(1000L)
        assertTrue(summary.contains("8.0 kb/s"))
        assertTrue(summary.contains("10.0 %"))
        assertTrue(summary.contains("3.0 ms"))
        assertTrue(summary.contains("40.0 ms"))
        net.record(GameLingoXboxNetworkMetrics.Sample("b",2000L,500.0,10.0,0.0,null,null))
        assertFalse(net.summary(2000L).contains("kb/s"))
        assertTrue(net.summary(18000L).contains("nedostupná"))
    }
    @Test fun xboxVideoUsesDeliveredFramesAndExpiresStaleFps() {
        val metrics = GameLingoXboxVideoMetrics()
        assertNull(metrics.fps(0))
        for (n in 0..30) metrics.frame(1280,720,n*1000L/30L)
        assertEquals(30.0, metrics.fps(1000L)!!, 0.001)
        assertTrue(metrics.summary(1000L).contains("1280×720"))
        assertNull(metrics.fps(3001L))
        metrics.stop()
        assertNull(metrics.fps(1000L))
    }
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
