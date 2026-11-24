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
import android.os.SystemClock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
    @Volatile private var appContext: Context? = null
    private var lastWireSequence = 0L

    @Synchronized
    private fun freshBody(body: String): String {
        val now = System.currentTimeMillis()
        lastWireSequence = maxOf(lastWireSequence + 1, now * 1000L)
        return "$body\t$now\t$lastWireSequence"
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /** Compatibility applies to the TV receiver only; new senders never downgrade. */
    fun legacyEnabled(c: Context): Boolean =
        prefs(c).getBoolean("legacy_unsigned_receiver", false)

    fun setLegacyEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("legacy_unsigned_receiver", enabled).apply()
    }

    /** All outbound game data uses the same authenticated envelope. */
    fun signedPacket(ip: String, payload: ByteArray): ByteArray {
        val context = appContext ?: throw IllegalStateException("GameLingo TV není připravena")
        val secret = token(context, ip)
            ?: throw IllegalStateException("Nejdříve spáruj GameLingo TV")
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        require(encoded.length < 11000) { "Příliš dlouhá TV zpráva" }
        val clientId = pairingId(context)
        val body = freshBody("TEXT\t$clientId\t$encoded")
        return ("PCPAIR1\t$body\t" + sign(secret, body)).toByteArray(Charsets.UTF_8)
    }

    /** Resolve the story socket on its worker thread, not the UI thread. */
    fun storyPort(ip: String): Int = receiverPort(ip)

    fun sendSigned(socket: DatagramSocket, ip: String, address: InetAddress,
                   payload: ByteArray, port: Int) {
        val packet = signedPacket(ip, payload)
        socket.send(DatagramPacket(packet, packet.size, address, port))
    }
    private val availablePorts = 41989..41993
    private val portLock = Any()
    @Volatile private var lastPortIp = ""
    @Volatile private var lastPort = PORT
    @Volatile private var checkedPortAt = 0L
    @Volatile private var queryingPort = false

    /** Fast nonblocking port lookup used by the actual Android subtitle
     * sender (not only by the pairing screen). Refreshes in the background
     * on target change and every 30 seconds to handle TV service restarts.
     */
    fun portFor(ip: String): Int {
        val now = SystemClock.elapsedRealtime()
        synchronized(portLock) {
            if (!queryingPort &&
                (lastPortIp != ip || now - checkedPortAt > 30_000L)) {
                queryingPort = true
                checkedPortAt = now
                Thread {
                    try { receiverPort(ip) }
                    finally { synchronized(portLock) { queryingPort = false } }
                }.apply { name = "gamelingo-tv-port"; isDaemon = true }.start()
            }
        }
        return if (lastPortIp == ip) lastPort else PORT
    }

    /**
     * The GameLingo TV receiver moves to a free UDP port if PlayCaption
     * already listens on the standard one. Discover that port BEFORE any
     * six-digit pairing or authenticated PING, without revealing credentials.
     * Legacy receivers without GLPORT1 keep their original port.
     */
    private fun receiverPort(ip: String): Int {
        val cached = if (lastPortIp == ip) lastPort else PORT
        val discovered = runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1_500
                val address = InetAddress.getByName(ip)
                val query = "GLPORT1\tQUERY".toByteArray(Charsets.UTF_8)
                for (port in availablePorts) {
                    socket.send(DatagramPacket(query, query.size, address, port))
                }
                val buffer = ByteArray(256)
                while (true) {
                    val reply = DatagramPacket(buffer, buffer.size)
                    socket.receive(reply)
                    if (reply.address != address) continue
                    val data = String(reply.data, 0, reply.length, Charsets.UTF_8)
                        .split("\t")
                    val port = data.getOrNull(2)?.toIntOrNull()
                    if (data.size == 3 && data[0] == "GLPORT1" &&
                        data[1] == "PORT" && port != null &&
                        port in availablePorts && reply.port == port) {
                        return@use port
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                PORT
            }
        }.getOrDefault(cached)
        lastPortIp = ip
        lastPort = discovered
        checkedPortAt = SystemClock.elapsedRealtime()
        return discovered
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastVerifiedIp = ""
    @Volatile private var lastVerifiedAt = 0L

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

    /** Only a fresh authenticated PING/PONG is a live receiver connection. */
    fun connected(ip: String): Boolean =
        ip == lastVerifiedIp && lastVerifiedAt > 0L &&
            SystemClock.elapsedRealtime() - lastVerifiedAt < 10_000L

    fun forget(c: Context, ip: String) {
        prefs(c).edit().remove("tv_" + hashIp(ip)).apply()
        if (lastVerifiedIp == ip) {
            lastVerifiedIp = ""
            lastVerifiedAt = 0L
        }
    }

    private fun sign(secret: String, value: String): String {
        val bytes = ByteArray(32) { i -> secret.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(bytes, "HmacSHA256"))
            doFinal(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    /** Verifies a TV receiver using the same signed PCPAIR1 PING as iPhone. */
    fun verify(activity: Activity, tv: TvProfil, onResult: (Boolean) -> Unit) {
        val secret = token(activity, tv.ip)
        if (secret == null) {
            onResult(false)
            return
        }
        val clientId = pairingId(activity)
        Thread {
            val result = runCatching {
                val nonce = UUID.randomUUID().toString()
                val message = freshBody("PING\t$clientId\t$nonce")
                val packetText = "PCPAIR1\t$message\t" + sign(secret, message)
                DatagramSocket().use { socket ->
                    socket.soTimeout = 3_000
                    val address = InetAddress.getByName(tv.ip)
                    val data = packetText.toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(data, data.size, address, receiverPort(tv.ip)))
                    val buf = ByteArray(2048)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    val expected = "PCPAIR1\tPONG\t$nonce\t" + sign(secret, "PONG\t$clientId\t$nonce")
                    reply.address == address && MessageDigest.isEqual(
                        expected.toByteArray(Charsets.UTF_8),
                        reply.data.copyOfRange(reply.offset, reply.offset + reply.length))
                }
            }.getOrDefault(false)
            main.post {
                // A late reply must not restore connected state after local forget
                // or re-pairing with a different secret.
                val stillPaired = token(activity, tv.ip) == secret
                val verified = result && stillPaired
                if (verified) {
                    lastVerifiedIp = tv.ip
                    lastVerifiedAt = SystemClock.elapsedRealtime()
                } else if (lastVerifiedIp == tv.ip && stillPaired) {
                    lastVerifiedIp = ""
                    lastVerifiedAt = 0L
                }
                if (!activity.isFinishing) onResult(verified)
            }
        }.apply { name = "gamelingo-tv-ping"; isDaemon = true }.start()
    }

    private fun handshake(ip: String, pin: String, clientId: String): String {
        require(pin.matches(Regex("[0-9]{6}"))) { "Zadej šest číslic z TV." }
        DatagramSocket().use { socket ->
            socket.soTimeout = 10_000
            val address = InetAddress.getByName(ip)
            val payload = "PCPAIR1\tPAIR\t$pin\t$clientId\tAndroid".toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(payload, payload.size, address, receiverPort(ip)))
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

    fun show(activity: Activity, tv: TvProfil, initialPin: String = "", onPaired: () -> Unit = {}) {
        if (activity.isFinishing) return
        val pinInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            hint = "000000"
            setText(initialPin.takeIf { it.matches(Regex("[0-9]{6}")) }.orEmpty())
            textSize = 26f
            gravity = Gravity.CENTER
            contentDescription = "Šestimístný kód z GameLingo TV"
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Spárovat GameLingo TV")
            .setMessage("Televize: " + tv.ip + "\nNa TV otevři Překlad z telefonu a vygeneruj šestimístný kód. " +
                "Ověř adresu a potvrď párování.")
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
                                    "TV přijala kód. Ověřuji spojení…",
                                    Toast.LENGTH_SHORT).show()
                                verify(activity, tv) { connected ->
                                    Toast.makeText(activity,
                                        if (connected) "GameLingo TV je připojená."
                                        else "Kód uložen, ale TV zatím neodpovídá.",
                                        Toast.LENGTH_LONG).show()
                                }
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
