package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class GameLingoDialogueTextTest {
    @Test fun lineWrappingDoesNotCreateAnotherRequest() {
        val original = "Come with me, please."
        assertEquals(original, GameLingoDialogueText.normalize("  Come  with\nme,\tplease. "))
        assertEquals(original, GameLingoDialogueText.normalize("Come\u00a0with me, please."))
    }
    @Test fun meaningfulChangesAreNotCollapsed() {
        val values = listOf("Come with me.", "Come without me.", "Come with me!",
            "Let us go.", "Let US go.", "No!", "No?")
        assertEquals(values, values.map(GameLingoDialogueText::normalize))
        assertEquals(values.size, values.map(GameLingoDialogueText::normalize).toSet().size)
    }
    @Test fun shortDialogueModeRetainsTwoLetterLinesWithoutAdmittingNoise() {
        for (text in listOf("Go!", "No", "OK?", "Ja", "Не")) {
            assertTrue(text, GameLingoDialogueText.readableLine(text, true))
            assertFalse(text, GameLingoDialogueText.readableLine(text, false))
        }
        for (text in listOf("A", "X!", "123", "?", " ")) {
            assertFalse(text, GameLingoDialogueText.readableLine(text, true))
        }
        assertTrue(GameLingoDialogueText.readableLine("Run!", false))
    }
    @Test fun controllerSymbolsSurviveTranslationOrFailSafely() {
        val original = "Press [A] then [R2] △ and [A]."
        val protected = GameLingoControllerSymbols(original)
        assertEquals(original, protected.restore(protected.input))
        assertEquals("Stiskni [A] then [R2] △ and [A].", protected.restore(protected.input.replace("Press","Stiskni")))
        assertNull(protected.restore(protected.input.replace("GLBUTTON0TOKEN","")))
        assertNull(protected.restore(protected.input + " GLBUTTON0TOKEN"))
        assertTrue(GameLingoControllerSymbols.valid(original, "Stiskni [A] a [R2] △ [A]."))
        assertFalse(GameLingoControllerSymbols.valid(original, "Stiskni [B] a [R2] △ [A]."))
        assertEquals("A car, X rays", GameLingoControllerSymbols("A car, X rays").input)
        assertEquals("GLBUTTON [A]", GameLingoControllerSymbols("GLBUTTON [A]").let { it.restore(it.input) })
    }
    @Test fun existingInputBoundIsPreserved() {
        assertEquals("", GameLingoDialogueText.normalize(" \n\t "))
        assertEquals(240, GameLingoDialogueText.normalize("x".repeat(400)).length)
    }
}
