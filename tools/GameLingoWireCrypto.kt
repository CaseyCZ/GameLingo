package cz.titulky.tv

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/** TEXT2 uses a purpose-separated key and a fresh random 96-bit nonce per datagram.
 * The outer HMAC authenticates timestamp/sequence; AES-GCM authenticates the payload and phone ID.
 * Wire bytes: nonce (12) || ciphertext || tag (16), standard Base64.
 */
internal object GameLingoWireCrypto {
    private const val PURPOSE = "GameLingo/TEXT2/AES-256-GCM/v1"
    private val random = SecureRandom()
    private fun key(secret: String): SecretKeySpec {
        require(secret.matches(Regex("[0-9a-fA-F]{64}|[0-9a-fA-F]{32}")))
        val raw = ByteArray(secret.length / 2) { i ->
            secret.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val derived = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(raw, "HmacSHA256"))
            doFinal(PURPOSE.toByteArray(Charsets.UTF_8))
        }
        return SecretKeySpec(derived, "AES")
    }
    private fun aad(id: String) = ("PCPAIR1\tTEXT2\t" + id).toByteArray(Charsets.UTF_8)
    fun seal(secret: String, id: String, payload: ByteArray): String =
        sealWithNonce(secret, id, payload, ByteArray(12).also(random::nextBytes))

    // Deterministic interoperability fixture only; production always calls seal().
    internal fun sealWithNonce(secret: String, id: String, payload: ByteArray, nonce: ByteArray): String {
        require(nonce.size == 12 && payload.size in 1..8192)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(id))
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(payload))
    }
    fun open(secret: String, id: String, encoded: String): ByteArray? = runCatching {
        require(encoded.length in 40..11000)
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size in 29..8220)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(secret), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad(id))
        cipher.doFinal(bytes, 12, bytes.size - 12)
    }.getOrNull()
}
