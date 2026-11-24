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
    private fun tokenSlot(id: String) = "token_v2_" + IPhoneTVPairing.phoneSlot(id)
    private fun sequenceSlot(id: String, lane: String) = "wire_sequence_v2_" + IPhoneTVPairing.phoneSlot(id) + "_" + lane

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
        assertTrue(prefs.contains(tokenSlot(id)))
        assertNotEquals(legacy, prefs.getString(tokenSlot(id), null))
        assertTrue(IPhoneTVPairing.paired(ctx))
        assertEquals(100L, prefs.getLong(sequenceSlot(id, "PING"), 0L))
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
        assertTrue(prefs.edit().putString(tokenSlot(id), "corrupted")
            .putString("phone_id", id).putString("token", legacy).commit())
        assertFalse(IPhoneTVPairing.paired(ctx))
        assertFalse(prefs.contains("token"))
        assertNull(IPhoneTVPairing.pongSignature(ctx, id, "test-nonce"))
    }

    @Test fun twoPhonesKeepIndependentKeysReplayAndRevocation() {
        val second = "audit-test-android"
        val firstPin = IPhoneTVPairing.startCode()
        val firstKey = requireNotNull(IPhoneTVPairing.confirm(ctx, firstPin, id, "iPhone"))
        val secondPin = IPhoneTVPairing.startCode()
        val secondKey = requireNotNull(IPhoneTVPairing.confirm(ctx, secondPin, second, "Android"))
        assertNotEquals(firstKey, secondKey)
        assertEquals(setOf(id, second), IPhoneTVPairing.phones(ctx).map { it.id }.toSet())
        val now = System.currentTimeMillis().toString()
        fun verify(phone: String, key: String, n: String): Boolean {
            val body = "TEXT\t$phone\tpayload"
            return IPhoneTVPairing.verifyFresh(ctx, phone, body, now, n,
                mac(key, "$body\t$now\t$n"), "PCT1")
        }
        assertTrue(verify(id, firstKey, "100"))
        assertTrue(verify(second, secondKey, "100"))
        assertFalse(verify(id, firstKey, "100"))
        assertTrue(IPhoneTVPairing.claimGameData(id))
        assertFalse(IPhoneTVPairing.claimGameData(second))
        assertFalse(IPhoneTVPairing.permitsStyle(second))
        assertTrue(IPhoneTVPairing.unpair(ctx, id))
        assertFalse(verify(id, firstKey, "101"))
        assertTrue(verify(second, secondKey, "101"))
        assertTrue(IPhoneTVPairing.claimGameData(second))
        assertEquals(listOf(second), IPhoneTVPairing.phones(ctx).map { it.id })
        assertFalse(prefs.contains(tokenSlot(id)))
        assertTrue(prefs.contains(tokenSlot(second)))
        // Rotating just this phone must not invalidate any other record.
        val replacement = requireNotNull(IPhoneTVPairing.confirm(ctx,
            IPhoneTVPairing.startCode(), second, "Android"))
        assertNotEquals(secondKey, replacement)
        assertFalse(verify(second, secondKey, "102"))
        assertTrue(verify(second, replacement, "1"))
    }

    @Test fun emptyPacketsAndReadinessDoNotClaimSource() {
        val second = "audit-test-android"
        val firstKey = requireNotNull(IPhoneTVPairing.confirm(ctx, IPhoneTVPairing.startCode(), id))
        val secondKey = requireNotNull(IPhoneTVPairing.confirm(ctx, IPhoneTVPairing.startCode(), second))
        val now = System.currentTimeMillis().toString()
        fun authenticate(phone: String, key: String) {
            val body = "TEXT\t$phone\tpayload"
            assertTrue(IPhoneTVPairing.verifyFresh(ctx, phone, body, now, "1",
                mac(key, "$body\t$now\t1"), "PCT1"))
        }
        authenticate(id, firstKey)
        authenticate(second, secondKey)
        fun extends(packet: String) = IPhoneTVPairing.extendsSourceLease(packet.split('\t'))
        assertFalse(extends("PCT1\t1\t0\t\t"))
        assertFalse(extends("PCV1\t1\t0\t0\t0\t1\t1\t-1\t"))
        assertFalse(extends("PCO1\t0\t0\t0\t1\t1"))
        assertFalse(extends("PCQ1\tquery"))
        assertTrue(extends("PCT1\t2\t6000\tHello\t"))
        assertTrue(extends("PCV1\t2\t1\t0\t0\t1\t1\t-1\t"))
        assertTrue(extends("PCO1\t1\t0\t0\t1\t1"))
        assertTrue(IPhoneTVPairing.claimGameData(id, false))
        assertFalse(IPhoneTVPairing.hasActiveSource())
        assertTrue(IPhoneTVPairing.claimGameData(second))
        assertFalse(IPhoneTVPairing.claimGameData(id, false))
        assertTrue(IPhoneTVPairing.claimGameData(second, false))
        assertFalse(IPhoneTVPairing.permitsStoryReadiness(id))
        assertTrue(IPhoneTVPairing.permitsStoryReadiness(second))
        android.os.SystemClock.sleep(5100)
        val revision = IPhoneTVPairing.overlayRevision()
        assertTrue(IPhoneTVPairing.permitsStoryReadiness(id))
        assertFalse(IPhoneTVPairing.hasActiveSource())
        assertEquals(revision, IPhoneTVPairing.overlayRevision())
        assertFalse(IPhoneTVPairing.claimGameData(id, false))
        assertTrue(IPhoneTVPairing.claimGameData(id, true))
        assertFalse(IPhoneTVPairing.permitsStoryReadiness(second))
    }

    @Test fun pairRetryAndClearUseEncryptedStorage() {
        val pin = IPhoneTVPairing.startCode()
        val token = requireNotNull(IPhoneTVPairing.confirm(ctx, pin, id))
        assertEquals(64, token.length)
        assertFalse(prefs.contains("token"))
        assertNotEquals(token, prefs.getString(tokenSlot(id), null))
        assertEquals(token, IPhoneTVPairing.confirm(ctx, pin, id))
        IPhoneTVPairing.clear(ctx)
        assertFalse(IPhoneTVPairing.paired(ctx))
        assertFalse(prefs.contains(tokenSlot(id)))
        assertNull(IPhoneTVPairing.pongSignature(ctx, id, "test-nonce"))
        assertNull(IPhoneTVPairing.confirm(ctx, pin, id))
    }
}
