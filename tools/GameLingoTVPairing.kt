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
 * Match the iPhone's 128-bit GameLingo TV receiver handshake (PCPAIR2).
 *
 * Android mobile only. Google TV already supports PAIR/PING on UDP 41989;
 * never transmit Microsoft/PSN credentials. A receiver secret is stored via
 * Android Keystore AES-GCM, never in logs or cleartext SharedPreferences.
 */
object GameLingoTVPairing {
    private const val PREFS = "gamelingo_tv_receiver_v2"
    private const val KEY_ALIAS = "cz.caseycz.gamelingo.receiver.v2"
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

    /** Legacy plaintext is never allowed in version 2. */
    fun legacyEnabled(c: Context): Boolean = false

    /** All outbound game data uses the same authenticated envelope. */
    fun signedPacket(ip: String, payload: ByteArray): ByteArray {
        val context = appContext ?: throw IllegalStateException("GameLingo TV není připravena")
        val secret = token(context, ip)
            ?: throw IllegalStateException("Nejdříve spáruj GameLingo TV")
        val encoded = GameLingoWireCrypto.seal(secret, payload, pairingId(context))
        require(encoded.length < 11000) { "Příliš dlouhá TV zpráva" }
        val clientId = pairingId(context)
        val body = freshBody("TEXT\t$clientId\t$encoded")
        return ("PCPAIR2\t$body\t" + sign(secret, body)).toByteArray(Charsets.UTF_8)
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
     * 128-bit pairing or authenticated PING, without revealing credentials.
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

    /** Verifies a TV receiver using the same signed PCPAIR2 PING as iPhone. */
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
                val packetText = "PCPAIR2\t$message\t" + sign(secret, message)
                DatagramSocket().use { socket ->
                    socket.soTimeout = 3_000
                    val address = InetAddress.getByName(tv.ip)
                    val data = packetText.toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(data, data.size, address, receiverPort(tv.ip)))
                    val buf = ByteArray(2048)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    val expected = "PCPAIR2\tPONG\t$nonce\t" + sign(secret, "PONG\t$clientId\t$nonce")
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
        require(GameLingoWireCrypto.validCode(pin)) { "Zadej bezpečnostní kód z TV." }
        val nonce = GameLingoWireCrypto.randomHex(16)
        val proof = GameLingoWireCrypto.pairProof(pin, clientId, "Android", nonce)
        val secret = GameLingoWireCrypto.sessionKey(pin, clientId, "Android", nonce)
        val port = receiverPort(ip)
        DatagramSocket().use { socket ->
            socket.soTimeout = 2500
            val address = InetAddress.getByName(ip)
            socket.connect(address, port)
            val payload = "PCPAIR2\tPAIR\t$clientId\tAndroid\t$nonce\t$proof".toByteArray(Charsets.UTF_8)
            repeat(4) {
                socket.send(DatagramPacket(payload, payload.size))
                val packet = DatagramPacket(ByteArray(2048), 2048)
                try {
                    socket.receive(packet)
                    val fields = String(packet.data, 0, packet.length, Charsets.UTF_8).split("\t")
                    if (fields.size == 6 && fields[0] == "PCPAIR2" && fields[1] == "OK" &&
                        fields[2] == clientId && fields[3] == "Android" && fields[4] == nonce &&
                        GameLingoWireCrypto.equalHex(fields[5], GameLingoWireCrypto.replyProof(secret, clientId, "Android", nonce))) return secret
                } catch (_: java.net.SocketTimeoutException) { }
            }
            throw IllegalArgumentException("TV nepotvrdila bezpečné párování. Vytvoř nový kód.")
        }
    }

    fun show(activity: Activity, tv: TvProfil, initialPin: String = "", onPaired: () -> Unit = {}) {
        if (activity.isFinishing) return
        val pinInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(48))
            hint = "32 znaků / QR"
            setText(initialPin.takeIf { GameLingoWireCrypto.validCode(it) }.orEmpty())
            textSize = 26f
            gravity = Gravity.CENTER
            contentDescription = "Bezpečnostní kód z GameLingo TV"
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Spárovat GameLingo TV")
            .setMessage("Televize: " + tv.ip + "\nNa TV otevři Překlad z telefonu a vygeneruj bezpečnostní kód nebo načti QR. " +
                "Ověř adresu a potvrď párování.")
            .setView(pinInput)
            .setNegativeButton("Zrušit", null)
            .setPositiveButton("Spárovat", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = pinInput.text.toString().filterNot { it.isWhitespace() || it == '-' }.lowercase(java.util.Locale.ROOT)
                if (!GameLingoWireCrypto.validCode(pin)) {
                    pinInput.error = "Zadej všech 32 hexadecimálních znaků"
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


/** Protocol v2. A 128-bit random, single-use out-of-band secret is NOT a PIN.
 * HMAC-SHA256 proof, RFC 5869 HKDF-SHA256 and AES-256-GCM (96-bit nonce).
 * No secret or session key is sent in UDP. Do not use with a low-entropy PIN.
 */
internal object GameLingoWireCrypto {
    private val random = SecureRandom()
    fun randomHex(size: Int) = hex(ByteArray(size).also(random::nextBytes))
    fun validCode(code: String) = code.matches(Regex("[0-9a-f]{32}"))
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun bytes(hex: String): ByteArray = ByteArray(hex.length / 2) {
        hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
    fun equalHex(a: String, b: String): Boolean =
        a.length == b.length && a.length % 2 == 0 &&
        runCatching { MessageDigest.isEqual(bytes(a), bytes(b)) }.getOrDefault(false)
    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    private fun hmac(key: ByteArray, data: String) = hmac(key, data.toByteArray(Charsets.UTF_8))
    private fun hkdf(key: ByteArray, salt: ByteArray, info: String): ByteArray =
        hmac(hmac(salt, key), info.toByteArray(Charsets.UTF_8) + byteArrayOf(1))
    fun pairProof(code: String, id: String, label: String, nonce: String): String =
        hex(hmac(bytes(code), "GameLingo PCPAIR2 PAIR\t$id\t$label\t$nonce"))
    fun sessionKey(code: String, id: String, label: String, nonce: String): String =
        hex(hkdf(bytes(code), bytes(nonce), "GameLingo PCPAIR2 session\t$id\t$label"))
    fun replyProof(secret: String, id: String, label: String, nonce: String): String =
        hex(hmac(bytes(secret), "GameLingo PCPAIR2 OK\t$id\t$label\t$nonce"))
    private fun textKey(secret: String) = hkdf(bytes(secret), ByteArray(32), "GameLingo PCPAIR2 text phone-to-TV")
    private fun aad(id: String) = "GameLingo PCPAIR2 TEXT\t$id".toByteArray(Charsets.UTF_8)
    fun seal(secret: String, payload: ByteArray, id: String): String {
        require(payload.size <= 8192)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(textKey(secret), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(id))
        return Base64.encodeToString(nonce + cipher.doFinal(payload), Base64.NO_WRAP)
    }
    fun open(secret: String, encoded: String, id: String): String? = runCatching {
        require(encoded.length < 11100)
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size in 28..8220)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(textKey(secret), "AES"),
            GCMParameterSpec(128, combined.copyOfRange(0, 12)))
        cipher.updateAAD(aad(id))
        String(cipher.doFinal(combined.copyOfRange(12, combined.size)), Charsets.UTF_8)
    }.getOrNull()
}
