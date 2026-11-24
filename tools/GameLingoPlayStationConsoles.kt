package cz.titulky.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.metallic.chiaki.lib.Target
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * More than one registered PlayStation, without changing the proven Chiaki
 * RemotePlayService. Existing remote_play prefs are still the active profile.
 *
 * Additional PSN IDs and pairing keys are stored as one AES-256/GCM blob using
 * a device-local non-exportable Android Keystore key, not in plain SharedPrefs.
 */
object GameLingoPlayStationConsoles {
    data class Console(val ip: String, val name: String, val selected: Boolean, val type: String)

    private const val STORE = "gamelingo_ps_consoles_v1"
    private const val ALIAS = "cz.caseycz.gamelingo.psconsoles.aes256gcm"
    private const val RECORDS = "records"
    private const val ACTIVE = "active"

    private fun prefs(c: Context) = c.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
             .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
             .setKeySize(256)
             .setUserAuthenticationRequired(false)
             .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val data = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + data, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val all = Base64.decode(value, Base64.NO_WRAP)
        require(all.size >= 12 + 16) { "Poškozená data" }
        val iv = all.copyOfRange(0, 12)
        val data = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun read(c: Context): JSONArray {
        val value = prefs(c).getString(RECORDS, null) ?: return JSONArray()
        return JSONArray(decrypt(value))
    }

    private fun save(c: Context, array: JSONArray) {
        val value = encrypt(array.toString())
        check(prefs(c).edit().putString(RECORDS, value).commit()) {
            "Nepodařilo se uložit PlayStation"
        }
    }

    fun list(c: Context): List<Console> = runCatching {
        val array = read(c)
        val selected = prefs(c).getString(ACTIVE, null)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val ip = item.getString("ip")
            Console(ip, item.optString("name", "PlayStation"),
                    selected == ip,
                    if (item.optInt("target", Target.PS5_1.value) >= 1_000_000) "PS5" else "PS4")
        }
    }.getOrDefault(emptyList())

    fun capture(c: Context, name: String, ip: String): Boolean = runCatching {
        val current = RemotePlayUdaje(c)
        val account = current.accountId
        val reg = current.registKey
        val key = current.rpKey
        require(ip.isNotBlank() && !account.isNullOrBlank() &&
                reg != null && key != null) { "Chybí ověřené údaje párování" }
        val array = read(c)
        val filtered = JSONArray()
        for (index in 0 until array.length()) {
            val record = array.getJSONObject(index)
            if (record.optString("ip") != ip) filtered.put(record)
        }
        filtered.put(JSONObject()
            .put("ip", ip)
            .put("name", name.take(64))
            .put("target", current.consoleTarget.value)
            .put("accountID", account)
            .put("registKey", Base64.encodeToString(reg, Base64.NO_WRAP))
            .put("rpKey", Base64.encodeToString(key, Base64.NO_WRAP)))
        save(c, filtered)
        prefs(c).edit().putString(ACTIVE, ip).apply()
        true
    }.getOrDefault(false)

    fun select(c: Context, ip: String): Boolean = runCatching {
        val array = read(c)
        val profile = (0 until array.length()).map { array.getJSONObject(it) }
            .firstOrNull { it.getString("ip") == ip } ?: return false
        val newReg = Base64.decode(profile.getString("registKey"), Base64.NO_WRAP)
        val newKey = Base64.decode(profile.getString("rpKey"), Base64.NO_WRAP)
        require(newReg.isNotEmpty() && newKey.isNotEmpty()) {
            "Chybí uložené klíče"
        }
        val active = RemotePlayUdaje(c)
        active.consoleTarget = Target.values().firstOrNull {
            it.value == profile.optInt("target", Target.PS5_1.value)
        } ?: Target.PS5_1
        active.accountId = profile.getString("accountID")
        active.ipKonzole = ip
        active.registKey = newReg
        active.rpKey = newKey
        prefs(c).edit().putString(ACTIVE, ip).apply()
        true
    }.getOrDefault(false)

    fun remove(c: Context, ip: String): Boolean = runCatching {
        val array = read(c)
        val filtered = JSONArray()
        for (i in 0 until array.length()) {
            val record = array.getJSONObject(i)
            if (record.optString("ip") != ip) filtered.put(record)
        }
        save(c, filtered)
        if (prefs(c).getString(ACTIVE, null) == ip) {
            prefs(c).edit().remove(ACTIVE).apply()
            // Do not revoke a currently running Remote Play session; a
            // future connection must select a saved or newly paired console.
        }
        true
    }.getOrDefault(false)
}
