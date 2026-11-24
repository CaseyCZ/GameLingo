package cz.titulky.tv

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Local-only iPhone -> TV pairing. NEVER transfers Xbox or Microsoft credentials.
 * Six-digit PIN authorizes a unique per-phone secret; signed heartbeats and
 * subtitles prove that the saved phone still knows that secret.
 * Transport is local UDP, not encrypted. Only use on a trusted home network.
 */
object IPhoneTVPairing {
    private val random = SecureRandom()
    private val lock = Any()
    @Volatile private var code: String? = null
    @Volatile private var expiresAtMs = 0L
    @Volatile private var attempts = 0
    // The TV may receive the PIN but its UDP OK reply can be lost. Repeating
    // the same successful request briefly must return the SAME secret.
    // A newly generated PIN or an explicit unpair invalidates this cache.
    private var lastSuccessPin: String? = null
    private var lastSuccessPhoneId: String? = null
    private var lastSuccessAtMs = 0L
    private const val REPLY_RETRY_WINDOW_MS = 15_000L
    private const val KEY_ALIAS = "cz.caseycz.gamelingo.tv.pairing.v1"
    private const val ENCRYPTED_TOKEN = "token_v1"
    private const val FRESHNESS_MS = 120_000L
    private val replayLanes = setOf("PCT1", "PCV1", "PCO1", "STORY", "STYLE", "PING")
    @Volatile var lastAuthenticatedMs = 0L
        private set

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("playcaption_iphone_receiver", Context.MODE_PRIVATE)

    // The wrapping key never leaves Android Keystore. All callers of the
    // following helpers hold lock; never replace an existing key on decrypt.
    private fun keystore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun wrappingKey(create: Boolean): SecretKey? {
        val store = keystore()
        val existing = store.getKey(KEY_ALIAS, null)
        if (existing != null) return existing as? SecretKey
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256).build())
            generateKey()
        }
    }

    private fun validToken(token: String): Boolean =
        token.length == 64 && hexBytes(token)?.size == 32

    private fun aad(phoneId: String): ByteArray =
        ("GameLingo-TV-pairing-v1:" + phoneId).toByteArray(Charsets.UTF_8)

    private fun encryptToken(phoneId: String, token: String): String? = runCatching {
        require(validToken(token))
        val key = wrappingKey(true) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad(phoneId))
        require(cipher.iv.size == 12)
        Base64.encodeToString(cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP)
    }.getOrNull()

    private fun decryptToken(phoneId: String, encoded: String): String? = runCatching {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        require(data.size == 12 + 64 + 16)
        val key = wrappingKey(false) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
        cipher.updateAAD(aad(phoneId))
        String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
            .takeIf(::validToken)
    }.getOrNull()

    /** Migrate the old plaintext exactly once. If encryption fails, revoke it
     * instead of continuing to use a plaintext key. Keep replay marks intact. */
    private fun migrateLegacy(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains("token")) return
        val old = p.getString("token", null)
        val phoneId = p.getString("phone_id", "").orEmpty()
        val editor = p.edit().remove("token")
        // An encrypted record always wins, even when corrupt. No downgrade.
        if (!p.contains(ENCRYPTED_TOKEN) && old != null &&
            phoneId.length in 8..90 && validToken(old)) {
            val encrypted = encryptToken(phoneId, old)
            if (encrypted != null) editor.putString(ENCRYPTED_TOKEN, encrypted)
        }
        if (!editor.commit()) {
            lastAuthenticatedMs = 0L
            // The in-memory preference may have changed despite a failed disk
            // write. Do not authorize this process with that uncertain state.
            storageWriteFailed = true
        }
    }

    private var storageWriteFailed = false

    fun initialize(ctx: Context) = synchronized(lock) { migrateLegacy(ctx) }

    private fun loadSecret(ctx: Context, phoneId: String): String? {
        migrateLegacy(ctx)
        if (storageWriteFailed || phoneId.isBlank()) return null
        val p = prefs(ctx)
        if (p.getString("phone_id", "") != phoneId) return null
        val encrypted = p.getString(ENCRYPTED_TOKEN, null) ?: return null
        return decryptToken(phoneId, encrypted)
    }

    fun startCode(): String = synchronized(lock) {
        val pin = (random.nextInt(900_000) + 100_000).toString()
        code = pin
        expiresAtMs = SystemClock.elapsedRealtime() + 180_000L
        attempts = 0
        lastSuccessPin = null
        lastSuccessPhoneId = null
        lastSuccessAtMs = 0L
        pin
    }

    fun remainingSeconds(): Int = synchronized(lock) {
        if (code == null) 0
        else ((expiresAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000L).toInt()
    }

    /** Returns a 32-byte secret (hex) only after PIN succeeds. */
    fun confirm(ctx: Context, input: String, phoneId: String): String? = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        if (lastSuccessAtMs > 0 && now - lastSuccessAtMs <= REPLY_RETRY_WINDOW_MS &&
            input == lastSuccessPin && phoneId == lastSuccessPhoneId) {
            // Never consume another PIN attempt or change the pairing secret.
            return@synchronized loadSecret(ctx, phoneId)
        }
        val valid = input.length == 6 && input.all { it.isDigit() } &&
            phoneId.length in 8..90 &&
            code != null && SystemClock.elapsedRealtime() < expiresAtMs &&
            attempts < 5
        if (!valid) return@synchronized null
        attempts += 1
        if (input != code) {
            if (attempts >= 5) code = null
            return@synchronized null
        }
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        val encrypted = encryptToken(phoneId, token) ?: return@synchronized null
        val editor = prefs(ctx).edit().putString("phone_id", phoneId)
            .putString(ENCRYPTED_TOKEN, encrypted).remove("token")
        replayLanes.forEach { editor.remove("wire_sequence_" + it) }
        if (!editor.commit()) {
            storageWriteFailed = true
            lastAuthenticatedMs = 0L
            return@synchronized null
        }
        storageWriteFailed = false
        lastSuccessPin = input
        lastSuccessPhoneId = phoneId
        lastSuccessAtMs = now
        code = null
        expiresAtMs = 0
        attempts = 0
        // A successful PIN is only pairing, not an authenticated live
        // connection. Show "connected" only after a verified PING or TEXT.
        lastAuthenticatedMs = 0L
        token
    }

    fun paired(ctx: Context): Boolean = synchronized(lock) {
        val phoneId = prefs(ctx).getString("phone_id", "").orEmpty()
        loadSecret(ctx, phoneId) != null
    }

    fun clear(ctx: Context) = synchronized(lock) {
        val cleared = prefs(ctx).edit().clear().commit()
        // This alias belongs exclusively to the TV receiver, not the phone
        // sender. Removing it also makes an old ciphertext unusable.
        val keyDeleted = runCatching { keystore().deleteEntry(KEY_ALIAS) }.isSuccess
        storageWriteFailed = !cleared && !keyDeleted
        lastAuthenticatedMs = 0L
        code = null
        expiresAtMs = 0L
        lastSuccessPin = null
        lastSuccessPhoneId = null
        lastSuccessAtMs = 0L
    }

    /** A separate high-water mark per renderer lane avoids dropping a clear
     *  just because a different lane's datagram arrived first. */
    fun textLane(encoded: String): String? = runCatching {
        if (encoded.length >= 11000) return null
        val inner = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        when (val kind = inner.substringBefore('\t')) {
            "PCT1", "PCV1", "PCO1" -> kind
            "PCQ1", "PCS1" -> "STORY"
            else -> null
        }
    }.getOrNull()

    fun verifyFresh(ctx: Context, phoneId: String, content: String,
                    timestampText: String?, sequenceText: String?,
                    signatureHex: String, lane: String): Boolean = synchronized(lock) {
        if (lane !in replayLanes) return@synchronized false
        val timestamp = timestampText?.toLongOrNull() ?: return@synchronized false
        val sequence = sequenceText?.toLongOrNull() ?: return@synchronized false
        val now = System.currentTimeMillis()
        if (timestamp < now - FRESHNESS_MS || timestamp > now + FRESHNESS_MS || sequence <= 0)
            return@synchronized false
        val p = prefs(ctx)
        val slot = "wire_sequence_" + lane
        if (sequence <= p.getLong(slot, 0L)) return@synchronized false
        val signed = "$content\t$timestampText\t$sequenceText"
        if (!verify(ctx, phoneId, signed, signatureHex)) return@synchronized false
        // Persist before dispatch, so captured traffic cannot be reused after
        // a receiver restart. A failed write is fail-closed.
        if (!p.edit().putLong(slot, sequence).commit()) return@synchronized false
        lastAuthenticatedMs = SystemClock.elapsedRealtime()
        true
    }

    fun pongSignature(ctx: Context, phoneId: String, nonce: String): String? = synchronized(lock) {
        val key = hexBytes(loadSecret(ctx, phoneId) ?: return@synchronized null) ?: return@synchronized null
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal("PONG\t$phoneId\t$nonce".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun verify(ctx: Context, phoneId: String, content: String, signatureHex: String): Boolean {
        if (phoneId.isBlank() || signatureHex.length != 64) return false
        val keyHex = loadSecret(ctx, phoneId) ?: return false
        val key = hexBytes(keyHex) ?: return false
        val expected = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(content.toByteArray(Charsets.UTF_8))
        }
        val received = hexBytes(signatureHex) ?: return false
        return MessageDigest.isEqual(expected, received)
    }

    fun connectedRecently(): Boolean =
        lastAuthenticatedMs > 0 &&
            SystemClock.elapsedRealtime() - lastAuthenticatedMs < 10_000L

    private fun hexBytes(hex: String): ByteArray? = try {
        if (hex.length % 2 != 0) null
        else ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    } catch (_: Exception) { null }
}
