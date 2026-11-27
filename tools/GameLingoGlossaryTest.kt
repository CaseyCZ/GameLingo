package cz.titulky.tv
import org.junit.Assert.*
import org.junit.Test

class GameLingoGlossaryTest {
    @Test fun exactOnlyAndLiteralReplacementsRespectBoundaries() {
        val rules = GameLingoGlossary.parse("Go! | | Běž!\nA+B | X.Y | A+B\nArthur | Artur | Arthur", "en", "cs")!!
        assertEquals("Běž!", rules.apply("Go!", "Jdi!", "en", "cs"))
        assertEquals("Jdi! rychle", rules.apply("Go! now", "Jdi! rychle", "en", "cs"))
        assertEquals("A+B a XzY", rules.apply("Use A+B", "X.Y a XzY", "en", "cs"))
        assertEquals("Artur", rules.apply("arthur", "Artur", "en", "cs"))
        assertNull(GameLingoGlossary.parse((1..51).joinToString("\n") { "Rule$it | wrong | right" }, "en", "cs"))
    }

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
