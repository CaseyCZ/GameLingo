package cz.titulky.tv
import org.junit.Assert.*
import org.junit.Test

class GameLingoTranslationHistoryTest {
    @Test fun differentModesRemainDistinctAndOptOutClearsAll() {
        val history = GameLingoTranslationHistory
        history.setEnabled(false)
        history.setEnabled(true)
        try {
            for (kind in listOf("Dialog", "Automatický", "Kamera")) {
                history.append(kind, "en", "cs", "Hello", "Ahoj")
                history.append(kind, "en", "cs", "Hello", "Ahoj")
            }
            assertEquals(listOf("Kamera", "Automatický", "Dialog"), history.snapshot().map { it.kind })
            history.setEnabled(false)
            history.append("Kamera", "en", "cs", "Private", "Soukromé")
            assertTrue(history.snapshot().isEmpty())
        } finally { history.setEnabled(false) }
    }

    @Test fun optInBoundDeduplicateAndClear() {
        val history = GameLingoTranslationHistory
        history.setEnabled(false)
        fun append(text: String, target: String = "cs") = history.append("Dialog", "en", target, text, "překlad")
        append("off")
        assertTrue(history.snapshot().isEmpty())
        history.setEnabled(true)
        try {
            append("one")
            append("one")
            assertEquals(1, history.snapshot().size)
            append("one", "sk")
            append("")
            assertEquals(2, history.snapshot().size)
            repeat(250) { append("line $it") }
            assertEquals(200, history.snapshot().size)
            assertEquals("line 50", history.snapshot().last().original)
            append("😀".repeat(3000))
            assertTrue(history.snapshot().first().original.length <= 4096)
            history.clear()
            assertTrue(history.snapshot().isEmpty())
            assertTrue(history.enabled())
            append("new")
        } finally { history.setEnabled(false) }
        assertTrue(history.snapshot().isEmpty())
        assertFalse(history.enabled())
    }
}
