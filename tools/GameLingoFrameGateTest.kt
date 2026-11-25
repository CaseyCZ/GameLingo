package cz.titulky.tv
import org.junit.Assert.*
import org.junit.Test

class GameLingoFrameGateTest {
    @Test fun requiresRealReadAndDoesNotExtendOnSkip() {
        val g=GameLingoFrameGate(650L)
        assertFalse(g.canSkip(42L,100L))
        g.record(42L,100L)
        assertTrue(g.canSkip(42L,200L))
        assertTrue(g.canSkip(42L,749L))
        assertFalse(g.canSkip(42L,750L))
        assertFalse(g.canSkip(42L,99L))
        assertFalse(g.canSkip(43L,200L))
        g.reset()
        assertFalse(g.canSkip(42L,200L))
    }
    @Test fun geometryAndOnePixelChangeAffectSignature() {
        val a=GameLingoFrameGate.seed(128,64)
        val b=GameLingoFrameGate.seed(64,128)
        assertNotEquals(a,b)
        assertNotEquals(GameLingoFrameGate.mix(a,0xff000000.toInt()),GameLingoFrameGate.mix(a,0xff000001.toInt()))
    }
}
