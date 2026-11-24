package cz.titulky.tv

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Destructive fixture: run only on a dedicated test installation. */
@RunWith(AndroidJUnit4::class)
class IPhoneTVPairingKeystoreTest {
    private val ctx: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = ctx.getSharedPreferences("playcaption_iphone_receiver", Context.MODE_PRIVATE)
    private val id = "audit-test-phone"
    private val legacy = "ab".repeat(32)

    @Before fun reset() { IPhoneTVPairing.clear(ctx) }
    @After fun cleanup() { IPhoneTVPairing.clear(ctx) }

    private fun mac(token: String, body: String): String {
        val key = ByteArray(32) { token.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }

    @Test fun legacyMigrationPreservesIdentityAndReplay() {
        assertTrue(prefs.edit().putString("phone_id", id).putString("token", legacy)
            .putLong("wire_sequence_PING", 100L).commit())
        IPhoneTVPairing.initialize(ctx)
        assertFalse(prefs.contains("token"))
        assertTrue(prefs.contains("token_v1"))
        assertNotEquals(legacy, prefs.getString("token_v1", null))
        assertTrue(IPhoneTVPairing.paired(ctx))
        assertEquals(100L, prefs.getLong("wire_sequence_PING", 0L))
        val now = System.currentTimeMillis().toString()
        val body = "PING\t$id\ttest-nonce"
        assertFalse(IPhoneTVPairing.verifyFresh(ctx, id, body, now, "100",
            mac(legacy, "$body\t$now\t100"), "PING"))
        assertTrue(IPhoneTVPairing.verifyFresh(ctx, id, body, now, "101",
            mac(legacy, "$body\t$now\t101"), "PING"))
        assertEquals(mac(legacy, "PONG\t$id\ttest-nonce"),
            IPhoneTVPairing.pongSignature(ctx, id, "test-nonce"))
    }

    @Test fun tamperedCiphertextCannotFallBack() {
        assertTrue(prefs.edit().putString("phone_id", id).putString("token", legacy).commit())
        IPhoneTVPairing.initialize(ctx)
        assertTrue(IPhoneTVPairing.paired(ctx))
        assertTrue(prefs.edit().putString("token_v1", "corrupted")
            .putString("token", legacy).commit())
        assertFalse(IPhoneTVPairing.paired(ctx))
        assertFalse(prefs.contains("token"))
        assertNull(IPhoneTVPairing.pongSignature(ctx, id, "test-nonce"))
    }

    @Test fun pairRetryAndClearUseEncryptedStorage() {
        val pin = IPhoneTVPairing.startCode()
        val token = requireNotNull(IPhoneTVPairing.confirm(ctx, pin, id))
        assertEquals(64, token.length)
        assertFalse(prefs.contains("token"))
        assertNotEquals(token, prefs.getString("token_v1", null))
        assertEquals(token, IPhoneTVPairing.confirm(ctx, pin, id))
        IPhoneTVPairing.clear(ctx)
        assertFalse(IPhoneTVPairing.paired(ctx))
        assertFalse(prefs.contains("token_v1"))
        assertNull(IPhoneTVPairing.pongSignature(ctx, id, "test-nonce"))
        assertNull(IPhoneTVPairing.confirm(ctx, pin, id))
    }
}
