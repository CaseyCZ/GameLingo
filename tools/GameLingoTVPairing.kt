package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.Toast
import android.util.Base64
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Match the iPhone's six-digit GameLingo TV receiver handshake (PCPAIR1).
 *
 * Android mobile only. Google TV already supports PAIR/PING on UDP 41989;
 * never transmit Microsoft/PSN credentials. A receiver secret is stored via
 * Android Keystore AES-GCM, never in logs or cleartext SharedPreferences.
 */
object GameLingoTVPairing {
    private const val PREFS = "gamelingo_tv_receiver"
    private const val KEY_ALIAS = "cz.caseycz.gamelingo.receiver.v1"
    private const val PORT = 41989
    private val random = SecureRandom()
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(c: Context) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pairingId(c: Context): String {
        val p = prefs(c)
        p.getString("client_id", null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        p.edit().putString("client_id", generated).apply()
        return generated
    }

    private fun hashIp(ip: String): String =
        MessageDigest.getInstance("SHA-256").digest(ip.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(raw: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(raw.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        require(data.size > 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun validToken(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private fun token(c: Context, ip: String): String? {
        val encrypted = prefs(c).getString("tv_" + hashIp(ip), null) ?: return null
        return decrypt(encrypted)?.takeIf(::validToken)
    }

    fun paired(c: Context, ip: String): Boolean =
        ip.isNotBlank() && token(c, ip) != null

    fun forget(c: Context, ip: String) {
        prefs(c).edit().remove("tv_" + hashIp(ip)).apply()
    }

    private fun handshake(ip: String, pin: String, clientId: String): String {
        require(pin.matches(Regex("[0-9]{6}"))) { "Zadej šest číslic z TV." }
        DatagramSocket().use { socket ->
            socket.soTimeout = 10_000
            val address = InetAddress.getByName(ip)
            val payload = "PCPAIR1\tPAIR\t$pin\t$clientId".toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(payload, payload.size, address, PORT))
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            require(packet.address == address) { "Odpověď nepřišla od vybrané TV." }
            val fields = String(packet.data, 0, packet.length, Charsets.UTF_8).split("\t")
            if (fields.size >= 2 && fields[0] == "PCPAIR1" && fields[1] == "ERROR")
                throw IllegalArgumentException("TV kód odmítla. Vytvoř nový kód.")
            require(fields.size == 4 && fields[0] == "PCPAIR1" &&
                fields[1] == "OK" && fields[2] == clientId && validToken(fields[3])) {
                "Televize vrátila neplatnou odpověď."
            }
            return fields[3]
        }
    }

    fun show(activity: Activity, tv: TvProfil, onPaired: () -> Unit = {}) {
        if (activity.isFinishing) return
        val pinInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            hint = "000000"
            textSize = 26f
            gravity = Gravity.CENTER
            contentDescription = "Šestimístný kód z GameLingo TV"
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Spárovat GameLingo TV")
            .setMessage("Na TV otevři Překlad z telefonu a vygeneruj šestimístný kód. " +
                "Zadej ho zde pro ověření této televize.")
            .setView(pinInput)
            .setNegativeButton("Zrušit", null)
            .setPositiveButton("Spárovat", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = pinInput.text.toString().trim()
                if (!pin.matches(Regex("[0-9]{6}"))) {
                    pinInput.error = "Je nutných šest číslic"
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val id = pairingId(activity)
                Thread {
                    val result = runCatching {
                        val secret = handshake(tv.ip, pin, id)
                        val encrypted = encrypt(secret)
                        prefs(activity).edit()
                            .putString("tv_" + hashIp(tv.ip), encrypted).apply()
                    }
                    main.post {
                        if (!activity.isFinishing) {
                            result.onSuccess {
                                dialog.dismiss()
                                onPaired()
                                Toast.makeText(activity,
                                    "TV je spárovaná. Spojení se ověří při přenosu.",
                                    Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                AlertDialog.Builder(activity)
                                    .setMessage(err.message ?: "TV neodpověděla. Ověř IP a síť.")
                                    .setPositiveButton("OK", null).show()
                            }
                        }
                    }
                }.apply { name = "gamelingo-tv-pin"; isDaemon = true }.start()
            }
        }
        dialog.show()
    }
}
