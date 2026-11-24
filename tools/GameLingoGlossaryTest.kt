package cz.titulky.tv
import org.junit.Assert.*
import org.junit.Test

class GameLingoGlossaryTest {
    @Test fun literalGuardedLanguageAndProfileRules() {
        val rules = GameLingoGlossary.parse("Arthur | Artur | Arthur\nNew York | Nový York | New York\nGo! | | Běž!\nMorgan | Arthur | Morgan", "en", "cs")!!
        assertEquals("Běž!", rules.apply("Go!", "Jdi!", "en", "cs"))
        assertEquals("Arthur v New York", rules.apply("Arthur and Morgan in New York", "Artur v Nový York", "en", "cs"))
        assertEquals("Artur", rules.apply("Arthurian", "Artur", "en", "cs"))
        assertEquals("Artur", rules.apply("Arthur", "Artur", "en", "de"))
        assertNull(GameLingoGlossary.parse("X | Y | Z\nX | Y | Q", "en", "cs"))
        assertNull(GameLingoGlossary.parse("X | Y | ", "en", "cs"))
        val other = GameLingoGlossary.parse("Arthur | Artur | A.", "en", "cs")!!
        assertEquals("A.", other.apply("Arthur", "Artur", "en", "cs"))
    }
}
