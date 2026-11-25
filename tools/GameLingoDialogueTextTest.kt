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
    @Test fun existingInputBoundIsPreserved() {
        assertEquals("", GameLingoDialogueText.normalize(" \n\t "))
        assertEquals(240, GameLingoDialogueText.normalize("x".repeat(400)).length)
    }
}
