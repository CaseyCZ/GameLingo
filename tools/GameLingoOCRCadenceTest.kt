package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class GameLingoOCRCadenceTest {
    @Test fun fastEmptyReadsDoNotEndDialoguePrematurely() {
        val absence = GameLingoDialogueAbsence()
        assertFalse(absence.observeEmpty(1000))
        assertFalse(absence.observeEmpty(1100))
        assertFalse(absence.observeEmpty(1799))
        assertTrue(absence.observeEmpty(1800))
        absence.reset()
        assertFalse(absence.observeEmpty(2000))
        absence.reset() // readable frame cancels disappearance
        assertFalse(absence.observeEmpty(2800))
        assertFalse(absence.observeEmpty(3000))
        assertTrue(absence.observeEmpty(3600))
        absence.reset()
        assertFalse(absence.observeEmpty(5000))
        assertFalse(absence.observeEmpty(4000)) // clock discontinuity
    }
    @Test fun previousDialoguePreservesCaseSensitiveSourceChanges() {
        val window = GameLingoDialogueWindow()
        assertEquals("Pojďme.", window.render("Let us go.", "Pojďme.", true, 1000, "A"))
        assertEquals("Pojďme.\nPusťte NÁS.", window.render("Let US go.", "Pusťte NÁS.", true, 2000, "A"))
        assertEquals("Pojďme.\nPusťte nás!", window.render("Let  US go.", "Pusťte nás!", true, 3000, "A"))
    }
    @Test fun previousDialogueCountIsBounded() {
        val window = GameLingoDialogueWindow()
        window.render("1", "A", true, 1000, "A", previousCount = 2)
        window.render("2", "B", true, 2000, "A", previousCount = 2)
        assertEquals("A\nB\nC", window.render("3", "C", true, 3000, "A", previousCount = 2))
        assertEquals("C\nD", window.render("4", "D", true, 4000, "A", previousCount = 1))
    }
    @Test fun previousDialogueIsBoundedAndContextLocal() {
        val window = GameLingoDialogueWindow()
        assertEquals("První", window.render("First", "První", true, 1000, "A"))
        assertEquals("První\nDruhý", window.render("Second", "Druhý", true, 2000, "A"))
        assertEquals("První\nDruhá", window.render("Second", "Druhá", true, 3000, "A", true))
        assertEquals("Třetí", window.render("Third", "Třetí", true, 4000, "B"))
        assertEquals("Čtvrtý", window.render("Fourth", "Čtvrtý", true, 20000, "B"))
    }
    @Test fun standaloneYellowModeSetsOnlyMinimumSamplingGap() {
        assertEquals(0L, GameLingoOCRCadence.standaloneYellowInterval("follow"))
        assertEquals(800L, GameLingoOCRCadence.standaloneYellowInterval("classic"))
        assertEquals(500L, GameLingoOCRCadence.standaloneYellowInterval("automatic"))
        assertEquals(250L, GameLingoOCRCadence.standaloneYellowInterval("street"))
    }
    @Test fun ignoredRowsMatchOnlyWholeNormalizedLine() {
        val ignored = GameLingoIgnoredLines.entries("  SAVE  GAME\nLoading\n")
        assertFalse(GameLingoIgnoredLines.keeps("save game", ignored))
        assertTrue(GameLingoIgnoredLines.keeps("Please save game now", ignored))
        assertTrue(GameLingoIgnoredLines.keeps("Loading", emptySet()))
    }
    @Test fun yellowFilterOnlyRemovesExplicitControllerPrompts() {
        for (prompt in listOf("Press A", "Hold R2", "TAP [X] to continue.", "Press button LB to go back")) {
            assertTrue(GameLingoYellowOCRFilter.keeps(prompt, false))
            assertFalse(GameLingoYellowOCRFilter.keeps(prompt, true))
        }
        for (option in listOf("YES", "NO", "LEAVE", "Hold on", "Press X to open the door", "A", "Inventory"))
            assertTrue(GameLingoYellowOCRFilter.keeps(option, true))
    }
    @Test fun captureWatchdogDistinguishesNoFrameAndSlowOcr() {
        assertEquals("Bez zjištěného výpadku", GameLingoCaptureWatchdog.state(100,false,0))
        assertTrue(GameLingoCaptureWatchdog.state(5000,false,0).contains("Nepřicházejí"))
        assertTrue(GameLingoCaptureWatchdog.state(100,true,5000).contains("déle"))
        assertTrue(GameLingoCaptureWatchdog.state(10000,true,15000).contains("další čtení nespouštím"))
    }
    @Test fun standalonePrioritizesDialogueWithoutStarvingGameText() {
        assertEquals(1, GameLingoOCRCadence.standaloneRegion(true,true,0,0))
        assertEquals(1, GameLingoOCRCadence.standaloneRegion(true,true,1,300))
        assertEquals(2, GameLingoOCRCadence.standaloneRegion(true,true,2,600))
        assertEquals(2, GameLingoOCRCadence.standaloneRegion(true,true,1,1200))
        assertEquals(1, GameLingoOCRCadence.standaloneRegion(true,false,2,9999))
        assertEquals(2, GameLingoOCRCadence.standaloneRegion(false,true,0,0))
    }
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
