package cz.titulky.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GameLingoTranslationCacheTest {
    @Test fun persistenceAndTrueLru() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cache-test-${UUID.randomUUID()}.db"
        val first = GameLingoCacheStore(context, name, 3)
        try {
            first.remember("google", "en", "cs", "Don't go", "Nechoď")
            first.remember("edge", "en", "cs", "Don't go", "Nechoďte")
            first.remember("google", "en", "sk", "Don't go", "Nechoď preč")
            assertEquals("Nechoď", first.lookup(listOf("google"), "en", "cs", "Don't go"))
            first.remember("google", "de", "cs", "Hallo", "Ahoj")
            assertNull(first.lookup(listOf("edge"), "en", "cs", "Don't go"))
            first.remember("google", "en", "cs", "bad", " ")
            first.close()
            val second = GameLingoCacheStore(context, name, 3)
            try {
                assertEquals("Nechoď", second.lookup(listOf("google"), "en", "cs", "Don't go"))
                assertEquals("Nechoď preč", second.lookup(listOf("google"), "en", "sk", "Don't go"))
                assertNull(second.lookup(listOf("google"), "de", "cs", "Don't go"))
                assertNull(second.lookup(listOf("google"), "en", "cs", "bad"))
            } finally { second.close() }
        } finally { first.close(); context.deleteDatabase(name) }
    }
}
