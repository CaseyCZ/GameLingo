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
 * Match the iPhone's authenticated GameLingo TV receiver handshake (PCPAIR1).
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
                (lastPortIp != ip || checkedPortAt == 0L || now - checkedPortAt > 30_000L)) {
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
                val deadline = SystemClock.elapsedRealtime() + 1_500L
                val address = InetAddress.getByName(ip)
                val query = "GLPORT1\tQUERY".toByteArray(Charsets.UTF_8)
                for (port in availablePorts) {
                    socket.send(DatagramPacket(query, query.size, address, port))
                }
                val buffer = ByteArray(256)
                while (true) {
                    // A stream of invalid replies cannot extend discovery forever.
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) return@use cached
                    socket.soTimeout = remaining.toInt().coerceAtLeast(1)
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
    @Volatile private var zoneCapableIp = ""
    fun supportsZones(ip: String) = connected(ip) && zoneCapableIp == ip
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
        require(secret.length in setOf(32, 64))
        val bytes = ByteArray(secret.length / 2) { i -> secret.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(bytes, "HmacSHA256"))
            doFinal(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    private val activeProbeLock = Any()
    private var activeProbeInFlight = false
    private var lastActiveProbeIp = ""
    private var lastActiveProbeAt = 0L

    // Remote TV appearance is separate from phone typography and local TV settings.
    internal fun appearancePrefs(c: Context): android.content.SharedPreferences {
        val store = TvProfily(c)
        val id = if (store.pouzeTelefon) null else store.posledniId
        return if (id != null && store.najdi(id) != null) store.nastaveni(id)
            else c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)
    }
    // Shared protocol values; TV builds intentionally omit phone typography.
    private val remoteCaptionColors = setOf("white", "yellow", "cyan", "green", "purple")
    private val remoteCaptionBackgrounds = setOf("none", "dark", "translucent")
    fun captionColor(c: Context): String = appearancePrefs(c).getString("remoteCaptionColor", "white")
        ?.takeIf { it in remoteCaptionColors } ?: "white"
    fun captionBackground(c: Context): String = appearancePrefs(c).getString("remoteCaptionBackground", "dark")
        ?.takeIf { it in remoteCaptionBackgrounds } ?: "dark"

    fun saveCaptionStyle(c: Context, color: String, background: String) {
        if (color !in remoteCaptionColors || background !in remoteCaptionBackgrounds) return
        appearancePrefs(c).edit().putString("remoteCaptionColor", color).putString("remoteCaptionBackground", background).apply()
    }

    private fun selectedTarget(c: Context, ip: String, generation: Long): Boolean {
        val store = TvProfily(c)
        return !store.pouzeTelefon && store.posledniId?.let { store.najdi(it)?.ip } == ip &&
            Propojeni.Vysilac.cilIp == ip && Propojeni.Vysilac.outputGeneration() == generation
    }

    // Called off the UI thread. No unsigned fallback or new sender identity.
    private fun sendCaptionStyle(c: Context, ip: String, generation: Long, secret: String,
                                 socket: DatagramSocket, address: InetAddress, port: Int) {
        if (!selectedTarget(c, ip, generation) || token(c, ip) != secret) return
        val body = freshBody("STYLE\t${pairingId(c)}\t${captionColor(c)}\t${captionBackground(c)}")
        val packet = ("PCPAIR1\t$body\t" + sign(secret, body)).toByteArray(Charsets.UTF_8)
        socket.send(DatagramPacket(packet, packet.size, address, port))
    }

    /** Send appearance only to the currently selected, authenticated receiver. */
    fun publishCaptionStyle(c: Context, onSent: (() -> Unit)? = null) {
        val context = c.applicationContext
        val ip = Propojeni.Vysilac.cilIp ?: return
        val generation = Propojeni.Vysilac.outputGeneration()
        if (!selectedTarget(context, ip, generation) || !connected(ip)) return
        Thread {
            val secret = token(context, ip) ?: return@Thread
            val sent = runCatching {
                val port = receiverPort(ip)
                val address = InetAddress.getByName(ip)
                DatagramSocket().use { socket -> sendCaptionStyle(context, ip, generation, secret, socket, address, port) }
                true
            }.getOrDefault(false)
            if (sent) main.post {
                if (selectedTarget(context, ip, generation) && token(context, ip) == secret) onSent?.invoke()
            }
        }.apply { name = "gamelingo-tv-style"; isDaemon = true }.start()
    }

    /** Actual sender heartbeat, only during an active translation session.
     * UDP send success and unsigned port discovery are not liveness proof. */
    fun verifyActive(ip: String, generation: Long) {
        val context = appContext ?: return
        val now = SystemClock.elapsedRealtime()
        synchronized(activeProbeLock) {
            if (activeProbeInFlight || (lastActiveProbeIp == ip && now - lastActiveProbeAt < 5000L)) return
            activeProbeInFlight = true
            lastActiveProbeIp = ip
            lastActiveProbeAt = now
        }
        Thread {
            try {
                val secret = token(context, ip) ?: return@Thread
                if (!RemotePlayService.aktivni || Propojeni.Vysilac.cilIp != ip ||
                    Propojeni.Vysilac.outputGeneration() != generation) return@Thread
                val clientId = pairingId(context)
                val result = runCatching {
                    val nonce = "Z1-" + UUID.randomUUID().toString()
                    val message = freshBody("PING\t$clientId\t$nonce")
                    val data = ("PCPAIR1\t$message\t" + sign(secret, message)).toByteArray(Charsets.UTF_8)
                    val address = InetAddress.getByName(ip)
                    val port = portFor(ip)
                    DatagramSocket().use { socket ->
                        socket.soTimeout = 3000
                        socket.send(DatagramPacket(data, data.size, address, port))
                        val reply = DatagramPacket(ByteArray(2048), 2048)
                        socket.receive(reply)
                        val expected = "PCPAIR1\tPONG\t$nonce\t" + sign(secret, "PONG\t$clientId\t$nonce")
                        val extended = expected + "\tPCZ1\t" + sign(secret,"PONG\t$clientId\t$nonce|PCZ1")
                        val bytes=reply.data.copyOfRange(reply.offset,reply.offset+reply.length)
                        val correctPeer=reply.address==address&&reply.port==port
                        val capable=correctPeer&&MessageDigest.isEqual(extended.toByteArray(Charsets.UTF_8),bytes)
                        val verified = capable || correctPeer&&MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8),bytes)
                        if (verified) runCatching {
                            sendCaptionStyle(context, ip, generation, secret, socket, address, port)
                        }
                        verified to capable
                    }
                }.getOrDefault(false to false)
                main.post {
                    if (!RemotePlayService.aktivni || Propojeni.Vysilac.cilIp != ip ||
                        Propojeni.Vysilac.outputGeneration() != generation || token(context, ip) != secret) return@post
                    if (result.first) {
                        zoneCapableIp = if(result.second)ip else ""
                        lastVerifiedIp = ip
                        lastVerifiedAt = SystemClock.elapsedRealtime()
                    } else {
                        if (lastVerifiedIp == ip) { lastVerifiedIp = ""; lastVerifiedAt = 0L }
                        // Force the next active send to rediscover the receiver port.
                        synchronized(portLock) { if (lastPortIp == ip) checkedPortAt = 0L }
                    }
                }
            } finally { synchronized(activeProbeLock) { activeProbeInFlight = false } }
        }.apply { name = "gamelingo-tv-live-ping"; isDaemon = true }.start()
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
                    val port = receiverPort(tv.ip)
                    socket.send(DatagramPacket(data, data.size, address, port))
                    val buf = ByteArray(2048)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    val expected = "PCPAIR1\tPONG\t$nonce\t" + sign(secret, "PONG\t$clientId\t$nonce")
                    reply.address == address && reply.port == port && MessageDigest.isEqual(
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
        require(pin.matches(Regex("[0-9a-f]{32}"))) { "Zadej celý párovací kód z TV." }
        val nonce = UUID.randomUUID().toString()
        val context = "$clientId\tAndroid\t$nonce"
        val proof = sign(pin, "PAIR2\t$context")
        val token = sign(pin, "KEY2\t$context")
        val payload = "PCPAIR1\tPAIR2\t$context\t$proof".toByteArray(Charsets.UTF_8)
        val expected = "PCPAIR1\tOK2\t$clientId\t$nonce\t" + sign(token, "OK2\t$context")
        val address = InetAddress.getByName(ip)
        val port = receiverPort(ip)
        DatagramSocket().use { socket ->
            socket.connect(address, port)
            socket.soTimeout = 2500
            repeat(3) {
                socket.send(DatagramPacket(payload, payload.size, address, port))
                val packet = DatagramPacket(ByteArray(2048), 2048)
                try {
                    socket.receive(packet)
                    if (packet.address == address && packet.port == port && MessageDigest.isEqual(
                            expected.toByteArray(Charsets.UTF_8), packet.data.copyOfRange(packet.offset, packet.offset + packet.length))) return token
                } catch (_: java.net.SocketTimeoutException) { }
            }
        }
        throw IllegalArgumentException("TV nepotvrdila bezpečné párování. Vytvoř nový kód.")
    }

    fun show(activity: Activity, tv: TvProfil, initialPin: String = "", onPaired: () -> Unit = {}) {
        if (activity.isFinishing) return
        val pinInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(48))
            hint = "xxxxxxxx xxxxxxxx xxxxxxxx xxxxxxxx"
            setText(initialPin.takeIf { it.matches(Regex("[0-9a-f]{32}")) }.orEmpty())
            textSize = GameLingoVisualKit.sp(activity, 15f)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            contentDescription = GameLingoLanguages.ui(activity, "Párovací kód z GameLingo TV")
            setSingleLine(true)
        }
        val dialog=android.app.Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val d:(Int)->Int={GameLingoVisualKit.dp(activity,it)}
        val root=android.widget.LinearLayout(activity).apply {
            orientation=android.widget.LinearLayout.VERTICAL
            setPadding(d(18),d(18),d(18),d(24));background=GameLingoVisualKit.pageBackground(activity)
        }
        val header=android.widget.LinearLayout(activity).apply {gravity=Gravity.CENTER_VERTICAL}
        header.addView(GameLingoVisualKit.heading(activity,GameLingoLanguages.ui(activity, "Párování televize")),android.widget.LinearLayout.LayoutParams(0,-2,1f))
        header.addView(GameLingoHelp.button(activity,"pairing"),GameLingoHelp.layout(activity,d(36),d(44)))
        root.addView(header)
        root.addView(GameLingoVisualKit.platformPanel(activity,"TV",tv.jmeno,tv.ip))
        val card=android.widget.LinearLayout(activity).apply {
            orientation=android.widget.LinearLayout.VERTICAL
            val pad=d(13);setPadding(pad,pad,pad,pad);background=GameLingoVisualKit.card(activity)
            addView(android.widget.TextView(activity).apply {
                text=GameLingoLanguages.ui(activity, "Na TV otevři Překlad z telefonu. Načti QR nebo opiš 32 znaků kódu. Titulky jsou podepsané, ne šifrované.")
                gravity=Gravity.CENTER;textSize = GameLingoVisualKit.sp(activity, 14f);setTextColor(android.graphics.Color.parseColor("#92A4C7"))
            })
            pinInput.setTextColor(android.graphics.Color.WHITE)
            pinInput.setHintTextColor(android.graphics.Color.parseColor("#92A4C7"))
            pinInput.background=android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#172033"));cornerRadius=d(11).toFloat()
            }
            addView(pinInput,android.widget.LinearLayout.LayoutParams(-1,d(58)).apply{topMargin=d(13)})
        }
        root.addView(card,android.widget.LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(13)})
        val pair=GameLingoVisualKit.cta(activity,GameLingoLanguages.ui(activity, "Spárovat")) {}
        root.addView(pair,android.widget.LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(13)})
        root.addView(GameLingoTVUi.action(activity,GameLingoLanguages.ui(activity, "Zrušit")){dialog.dismiss()})
        dialog.setContentView(android.widget.ScrollView(activity).apply{isFillViewport=true;addView(root)})
        pair.setOnClickListener {
                val pin = pinInput.text.toString().filterNot { it.isWhitespace() || it == '-' }.lowercase(java.util.Locale.ROOT)
                if (!pin.matches(Regex("[0-9a-f]{32}"))) {
                    pinInput.error = GameLingoLanguages.ui(activity, "Zadej všech 32 znaků kódu")
                    return@setOnClickListener
                }
                pair.isEnabled = false;pair.alpha=.45f
                val id = pairingId(activity)
                Thread {
                    val result = runCatching {
                        val secret = handshake(tv.ip, pin, id)
                        val encrypted = encrypt(secret)
                        prefs(activity).edit()
                            .putString("tv_" + hashIp(tv.ip), encrypted).commit().also { check(it) }
                    }
                    main.post {
                        if (!activity.isFinishing) {
                            result.onSuccess {
                                dialog.dismiss()
                                onPaired()
                                Toast.makeText(activity,
                                    GameLingoLanguages.ui(activity, "TV přijala kód. Ověřuji spojení…"),
                                    Toast.LENGTH_SHORT).show()
                                verify(activity, tv) { connected ->
                                    Toast.makeText(activity,
                                        if (connected) GameLingoLanguages.ui(activity, "GameLingo TV je připojená.")
                                        else GameLingoLanguages.ui(activity, "Kód uložen, ale TV zatím neodpovídá."),
                                        Toast.LENGTH_LONG).show()
                                }
                            }.onFailure { err ->
                                pair.isEnabled = true;pair.alpha=1f
                                AlertDialog.Builder(activity)
                                    .setMessage(GameLingoLanguages.ui(activity, err.message ?: "TV neodpověděla. Ověř IP a síť."))
                                    .setPositiveButton("OK", null).show()
                            }
                        }
                    }
                }.apply { name = "gamelingo-tv-pin"; isDaemon = true }.start()
        }
        dialog.show()
        dialog.window?.setLayout(-1,-1)
    }
}
