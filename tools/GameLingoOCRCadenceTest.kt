package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class GameLingoOCRCadenceTest {
    @Test fun orderedModesAndPowerFloor() {
        for (phone in listOf(true, false)) {
            assertTrue(GameLingoOCRCadence.dialogue("classic", phone, false) >
                GameLingoOCRCadence.dialogue("automatic", phone, false))
            assertTrue(GameLingoOCRCadence.dialogue("automatic", phone, false) >
                GameLingoOCRCadence.dialogue("street", phone, false))
        }
        assertEquals(100L, GameLingoOCRCadence.dialogue("unknown", true, false))
        assertEquals(250L, GameLingoOCRCadence.dialogue("classic", false, false))
        assertEquals(600L, GameLingoOCRCadence.dialogue("street", false, true))
        assertEquals(150L, GameLingoOCRCadence.gameText("street", false, false, 0))
        assertEquals(187L, GameLingoOCRCadence.gameText("street", true, false, 0))
        assertEquals(600L, GameLingoOCRCadence.gameText("street", false, true, 0))
        assertEquals(1200L, GameLingoOCRCadence.gameText("street", false, false, 800))
        assertEquals(1500L, GameLingoOCRCadence.gameText("street", false, false, 5000))
    }
}
