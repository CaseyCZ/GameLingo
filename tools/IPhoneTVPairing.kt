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
    private const val ENCRYPTED_TOKEN = "token_v1" // old single-phone format
    private const val PHONE_IDS = "phone_ids_v2"
    private const val MAX_PHONES = 32
    private const val SOURCE_LEASE_MS = 5000L
    private val authenticatedAt = mutableMapOf<String, Long>()
    private var activePhone: String? = null
    private var activeAt = 0L
    private var sourceRevision = 0L
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

    private fun validPhoneId(id: String): Boolean =
        id.length in 8..90 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' }

    internal fun phoneSlot(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun ids(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(PHONE_IDS, emptySet()).orEmpty().filter(::validPhoneId).toSet()

    private fun tokenSlot(id: String) = "token_v2_" + phoneSlot(id)
    private fun labelSlot(id: String) = "label_v2_" + phoneSlot(id)
    private fun sequenceSlot(id: String, lane: String) = "wire_sequence_v2_" + phoneSlot(id) + "_" + lane
    private fun platformLabel(label: String): String =
        if (label in listOf("iPhone", "Android")) label else "Telefon"

    /** Atomic migration of either prior single-phone format. An encrypted
     * record wins over a plaintext copy, including when it is corrupt. */
    private fun migrateLegacy(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains("token") && !p.contains(ENCRYPTED_TOKEN) && !p.contains("phone_id")) return
        val id = p.getString("phone_id", "").orEmpty()
        val editor = p.edit().remove("token").remove(ENCRYPTED_TOKEN).remove("phone_id")
        val known = ids(ctx)
        if (validPhoneId(id) && id !in known && known.size < MAX_PHONES) {
            val encrypted = if (p.contains(ENCRYPTED_TOKEN)) p.getString(ENCRYPTED_TOKEN, null)
                else p.getString("token", null)?.takeIf(::validToken)?.let { encryptToken(id, it) }
            if (encrypted != null && decryptToken(id, encrypted) != null) {
                editor.putStringSet(PHONE_IDS, known + id)
                    .putString(tokenSlot(id), encrypted).putString(labelSlot(id), "Telefon")
                replayLanes.forEach { lane ->
                    val old = p.getLong("wire_sequence_" + lane, 0L)
                    editor.putLong(sequenceSlot(id, lane), old)
                }
            }
        }
        replayLanes.forEach { editor.remove("wire_sequence_" + it) }
        if (!editor.commit()) {
            lastAuthenticatedMs = 0L
            storageWriteFailed = true
        }
    }

    private var storageWriteFailed = false

    fun initialize(ctx: Context) = synchronized(lock) { migrateLegacy(ctx) }

    private fun loadSecret(ctx: Context, phoneId: String): String? {
        migrateLegacy(ctx)
        if (storageWriteFailed || phoneId !in ids(ctx)) return null
        val encrypted = prefs(ctx).getString(tokenSlot(phoneId), null) ?: return null
        return decryptToken(phoneId, encrypted)
    }

    data class PairedPhone(val id: String, val label: String, val connected: Boolean)

    fun phones(ctx: Context): List<PairedPhone> = synchronized(lock) {
        migrateLegacy(ctx)
        val now = SystemClock.elapsedRealtime()
        ids(ctx).sorted().map { id ->
            val label = prefs(ctx).getString(labelSlot(id), "Telefon").orEmpty()
            PairedPhone(id, platformLabel(label) + " · " + id.takeLast(8),
                !storageWriteFailed && authenticatedAt[id]?.let { now - it < 10_000L } == true)
        }
    }

    private fun releaseSource(phoneId: String) {
        if (activePhone == phoneId) {
            activePhone = null
            activeAt = 0L
            sourceRevision++
        }
    }

    /** The first authenticated game-data sender owns the overlay until idle.
     * PING does not claim/extend the lease. A different phone's text cannot
     * interleave with the current phone's dialogue or choices. */
    internal fun extendsSourceLease(fields: List<String>): Boolean {
        fun present(index: Int) = !fields.getOrNull(index).isNullOrBlank()
        return when (fields.firstOrNull()) {
            "PCT1" -> present(3) || present(4) ||
                fields.getOrNull(9) == "1" ||
                (fields.getOrNull(5) == "" && fields.getOrNull(10) == "1")
            "PCV1" -> present(8) || (fields.getOrNull(2)?.toIntOrNull() ?: 0) in 1..2
            "PCO1" -> fields.getOrNull(1) == "1"
            "PCS1" -> present(3)
            else -> false // PCQ1 readiness queries and empty clears do not acquire ownership.
        }
    }

    fun claimGameData(phoneId: String, extendsLease: Boolean = true): Boolean = synchronized(lock) {
        if (storageWriteFailed || phoneId !in authenticatedAt) return@synchronized false
        // Accept an owner's clear without renewing the lease. When nobody
        // owns the output, a readiness query may still receive a reply.
        if (!extendsLease) return@synchronized activePhone == null || activePhone == phoneId
        val now = SystemClock.elapsedRealtime()
        if (activePhone != null && activePhone != phoneId && now - activeAt < SOURCE_LEASE_MS)
            return@synchronized false
        if (activePhone != phoneId) { activePhone = phoneId; sourceRevision++ }
        activeAt = now
        true
    }

    fun permitsStyle(phoneId: String): Boolean = synchronized(lock) {
        activePhone == null || activePhone == phoneId ||
            SystemClock.elapsedRealtime() - activeAt >= SOURCE_LEASE_MS
    }

    fun hasActiveSource(): Boolean = synchronized(lock) {
        activePhone != null && SystemClock.elapsedRealtime() - activeAt < SOURCE_LEASE_MS
    }

    fun overlayRevision(): Long = synchronized(lock) { sourceRevision }

    fun unpair(ctx: Context, phoneId: String): Boolean = synchronized(lock) {
        migrateLegacy(ctx)
        val known = ids(ctx)
        if (phoneId !in known) return@synchronized false
        val editor = prefs(ctx).edit().putStringSet(PHONE_IDS, known - phoneId)
            .remove(tokenSlot(phoneId)).remove(labelSlot(phoneId))
        replayLanes.forEach { editor.remove(sequenceSlot(phoneId, it)) }
        // Revoke in-memory authorization even if persistent storage fails.
        authenticatedAt.remove(phoneId)
        releaseSource(phoneId)
        if (lastSuccessPhoneId == phoneId) {
            lastSuccessPin = null
            lastSuccessPhoneId = null
            lastSuccessAtMs = 0L
        }
        lastAuthenticatedMs = authenticatedAt.values.maxOrNull() ?: 0L
        val saved = editor.commit()
        if (!saved) storageWriteFailed = true
        saved
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
    fun confirm(ctx: Context, input: String, phoneId: String, label: String = "Telefon"): String? = synchronized(lock) {
        migrateLegacy(ctx)
        val now = SystemClock.elapsedRealtime()
        if (lastSuccessAtMs > 0 && now - lastSuccessAtMs <= REPLY_RETRY_WINDOW_MS &&
            input == lastSuccessPin && phoneId == lastSuccessPhoneId) {
            // Never consume another PIN attempt or change the pairing secret.
            return@synchronized loadSecret(ctx, phoneId)
        }
        val valid = input.length == 6 && input.all { it.isDigit() } &&
            validPhoneId(phoneId) &&
            (phoneId in ids(ctx) || ids(ctx).size < MAX_PHONES) &&
            !storageWriteFailed &&
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
        val editor = prefs(ctx).edit().putStringSet(PHONE_IDS, ids(ctx) + phoneId)
            .putString(tokenSlot(phoneId), encrypted).putString(labelSlot(phoneId), platformLabel(label))
        replayLanes.forEach { editor.remove(sequenceSlot(phoneId, it)) }
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
        authenticatedAt.remove(phoneId)
        releaseSource(phoneId)
        lastAuthenticatedMs = authenticatedAt.values.maxOrNull() ?: 0L
        token
    }

    fun paired(ctx: Context): Boolean = synchronized(lock) {
        migrateLegacy(ctx)
        !storageWriteFailed && ids(ctx).any { loadSecret(ctx, it) != null }
    }

    fun clear(ctx: Context) = synchronized(lock) {
        val cleared = prefs(ctx).edit().clear().commit()
        // This alias belongs exclusively to the TV receiver, not the phone
        // sender. Removing it also makes an old ciphertext unusable.
        val keyDeleted = runCatching { keystore().deleteEntry(KEY_ALIAS) }.isSuccess
        storageWriteFailed = !cleared && !keyDeleted
        authenticatedAt.clear()
        activePhone = null
        activeAt = 0L
        sourceRevision++
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
        migrateLegacy(ctx)
        val p = prefs(ctx)
        val slot = sequenceSlot(phoneId, lane)
        if (sequence <= p.getLong(slot, 0L)) return@synchronized false
        val signed = "$content\t$timestampText\t$sequenceText"
        if (!verify(ctx, phoneId, signed, signatureHex)) return@synchronized false
        // Persist before dispatch, so captured traffic cannot be reused after
        // a receiver restart. A failed write is fail-closed.
        if (!p.edit().putLong(slot, sequence).commit()) return@synchronized false
        lastAuthenticatedMs = SystemClock.elapsedRealtime()
        authenticatedAt[phoneId] = lastAuthenticatedMs
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
