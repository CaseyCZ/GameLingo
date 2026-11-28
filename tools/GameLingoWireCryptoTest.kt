package cz.titulky.tv
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class GameLingoWireCryptoTest {
    @Test fun matchesSwiftAndRejectsTampering() {
        val key = "ab".repeat(32)
        val id = "audit-test-phone"
        val data = "PCT1\t1\t1800\tHello".toByteArray(Charsets.UTF_8)
        val expected = "AAECAwQFBgcICQoLXJHyEjqnv5PGmoS/LeO3QqMcaCN1b3y5Z6GKzDSS9wA1"
        assertEquals(expected, GameLingoWireCrypto.sealWithNonce(key, id, data, ByteArray(12) { it.toByte() }))
        assertArrayEquals(data, GameLingoWireCrypto.open(key, id, expected))
        assertNull(GameLingoWireCrypto.open(key, "other", expected))
        assertNull(GameLingoWireCrypto.open("cd".repeat(32), id, expected))
        val tampered = Base64.getDecoder().decode(expected)
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        assertNull(GameLingoWireCrypto.open(key, id, Base64.getEncoder().encodeToString(tampered)))
        assertNotEquals(GameLingoWireCrypto.seal(key, id, data), GameLingoWireCrypto.seal(key, id, data))
    }
}
