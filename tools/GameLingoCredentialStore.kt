package cz.titulky.tv

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local encryption for inherited active console credentials.
 * Existing plaintext entries migrate atomically at first read; invalid ciphertext
 * never falls back to plaintext. Only nonsecret metadata stays in normal prefs.
 */
internal object GameLingoCredentialStore {
    private const val ALIAS = "cz.caseycz.gamelingo.console.credentials.v1"
    private const val PREFIX = "glenc1:"
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Credential key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }
    private fun encrypt(scope: String, field: String, value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD("$scope/$field".toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    @Synchronized fun read(prefs: SharedPreferences, scope: String, field: String): String? = runCatching {
        val raw = prefs.getString(field, null) ?: return null
        if (!raw.startsWith(PREFIX)) {
            check(prefs.edit().putString(field, encrypt(scope, field, raw)).commit())
            return raw
        }
        val bytes = Base64.decode(raw.removePrefix(PREFIX), Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD("$scope/$field".toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }.getOrNull()

    @Synchronized fun write(prefs: SharedPreferences, scope: String, values: Map<String, String?>,
                            expiry: Long? = null): Boolean = runCatching {
        val edit = prefs.edit()
        for ((field, value) in values) {
            if (value == null) edit.remove(field)
            else edit.putString(field, encrypt(scope, field, value))
        }
        if (expiry != null) edit.putLong("pristupMsDo", expiry)
        edit.commit()
    }.getOrDefault(false)
}
