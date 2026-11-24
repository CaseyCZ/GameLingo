package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast

/** System consent is always requested anew. This does not bypass secure/DRM surfaces. */
class GameLingoScreenConsentActivity : Activity() {
    private val startupUi = Handler(Looper.getMainLooper())
    private var waitingForCapture = false
    private var startupDeadline = 0L
    private val checkStartup = object : Runnable {
        override fun run() {
            if (!waitingForCapture || isFinishing) return
            when {
                GameLingoScreenTranslationService.running -> {
                    waitingForCapture = false
                    moveTaskToBack(true)
                    finish()
                }
                GameLingoScreenTranslationService.startupFailed -> {
                    waitingForCapture = false
                    Toast.makeText(this@GameLingoScreenConsentActivity, GameLingoScreenTranslationService.status, Toast.LENGTH_LONG).show()
                    finish()
                }
                SystemClock.elapsedRealtime() >= startupDeadline -> {
                    waitingForCapture = false
                    GameLingoScreenTranslationService.status = "Služba snímání neodpověděla"
                    GameLingoScreenTranslationService.stop(this@GameLingoScreenConsentActivity)
                    Toast.makeText(this@GameLingoScreenConsentActivity, GameLingoScreenTranslationService.status, Toast.LENGTH_LONG).show()
                    finish()
                }
                else -> startupUi.postDelayed(this, 100L)
            }
        }
    }
    @Deprecated("TV back navigation")
    override fun onBackPressed() {
        if (waitingForCapture) {
            waitingForCapture = false
            GameLingoScreenTranslationService.stop(this)
        }
        super.onBackPressed()
    }
    override fun onDestroy() {
        startupUi.removeCallbacks(checkStartup)
        if (waitingForCapture) GameLingoScreenTranslationService.stop(this)
        super.onDestroy()
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state != null) return
        if (!Settings.canDrawOverlays(this) || RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running) {
            Toast.makeText(this, "Zastav ostatní režimy a povol překryv.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        GameLingoScreenTranslationService.resetDiagnostics()
        GameLingoScreenTranslationService.status = "Čekám na systémový souhlas"
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val request = if (Build.VERSION.SDK_INT >= 34)
                manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
            else manager.createScreenCaptureIntent()
            startActivityForResult(request, 9231)
        } catch (e: Exception) {
            GameLingoScreenTranslationService.status = "Dialog snímání: " + e.javaClass.simpleName
            Toast.makeText(this, "Snímání nelze spustit.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 9231) { finish(); return }
        if (resultCode == RESULT_OK && data != null) startSession(data)
        else {
            GameLingoScreenTranslationService.status = "Snímání nebylo povoleno"
            finish()
        }
    }
    private fun startSession(consent: Intent) {
        GameLingoScreenTranslationService.status = "Souhlas udělen"
        val request = Intent(this, GameLingoScreenTranslationService::class.java)
            .putExtra("result", RESULT_OK).putExtra("consent", consent)
            .putExtra("diagnostic", intent.getBooleanExtra("diagnostic", false))
        runCatching {
            startForegroundService(request)
            waitingForCapture = true
            startupDeadline = SystemClock.elapsedRealtime() + 15000L
            startupUi.post(checkStartup)
        }.onFailure {
            GameLingoScreenTranslationService.status = "Start služby: " + it.javaClass.simpleName
            Toast.makeText(this, "Překlad se nepodařilo spustit.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}

/** Experimental continuous OCR for capturable TV apps. Only one frame and one translation in flight. */
class GameLingoScreenTranslationService : Service() {
    private val ui = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var ocr: Ctecka? = null
    private var translator: Prekladac? = null
    private var active = false
    private var starting = false
    private var pendingStart: Runnable? = null
    private var paused = false
    private var source = "Čekám na první snímek"
    private var worker: HandlerThread? = null
    private var frameHandler: Handler? = null
    private val wantFrame = AtomicBoolean(false)
    private var startedAt = 0L
    private var diagnostic = false
    private var lastTextAt = 0L
    private var translatedText = ""
    private var lastRenderAt = 0L
    private var blankSince = 0L
    private var busy = false
    private var epoch = 0L
    private var candidate = ""
    private var shown = ""
    private var blankFrames = 0
    private var darkFrames = 0
    private var width = 1280
    private var height = 540
    private var roi = floatArrayOf(.05f,.68f,.95f,.98f)
    private var output = floatArrayOf(.5f,.32f,.78f,1f)
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            if (RemotePlayService.aktivni || PrijemService.bezi) { stopSelf(); return }
            if (!paused && !busy) wantFrame.set(true)
            if (!paused && received.get() == 0L && SystemClock.elapsedRealtime() - startedAt > 5000L)
                setSource("MediaProjection běží, ale nedorazil žádný snímek")
            if (!paused && translatedText.isNotEmpty() && SystemClock.elapsedRealtime() - lastTextAt > 5000L) {
                resetText(); render("")
            }
            if (active) ui.postDelayed(this, GameLingoOCRConfig.tvIntervalMs(false).coerceIn(500L, 1500L))
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == PAUSE) {
            if (!active) { stopSelf(); return START_NOT_STICKY }
            paused = !paused; resetText(); render("")
            status = if (paused) "Pozastaveno" else source
            notification()
            return START_NOT_STICKY
        }
        if (active || starting) return START_NOT_STICKY
        diagnostic = intent?.getBooleanExtra("diagnostic", false) == true
        val result = intent?.getIntExtra("result", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val consent: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("consent", Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra("consent")
        if (result != Activity.RESULT_OK || consent == null ||
            !Settings.canDrawOverlays(this) || RemotePlayService.aktivni || PrijemService.bezi) {
            status = "Chybí souhlas nebo překryv, případně běží jiný režim"
            startupFailed = true
            stopSelf(); return START_NOT_STICKY
        }
        starting = true
        try {
            status = "Aktivuji službu snímání"
            notification()
            if (Build.VERSION.SDK_INT >= 29) {
                val type = foregroundServiceType
                check(type and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION != 0) {
                    "Služba nemá typ mediaProjection (typ=$type)"
                }
            }
            foregroundState = "Aktivní · mediaProjection"
            android.util.Log.i("GameLingoCapture", foregroundState)
            // Let TV firmware propagate foreground state before consuming the one-shot consent.
            val task = Runnable {
                pendingStart = null
                if (starting) beginCapture(result, consent)
            }
            pendingStart = task
            ui.postDelayed(task, 500L)
        } catch (e: Exception) {
            foregroundState = "Chyba: " + e.javaClass.simpleName
            failStartup("Aktivace foreground služby", e)
        }
        return START_NOT_STICKY
    }
    private fun beginCapture(result: Int, consent: Intent) {
        try {
            check(Settings.canDrawOverlays(this) && !RemotePlayService.aktivni && !PrijemService.bezi) {
                "Překryv nebo režim se mezitím změnil."
            }
            GameLingoOCRConfig.load(this); OblastTitulku.nacti(this)
            if (OblastTitulku.rucne) {
                val r = OblastTitulku.oblast.copyOf()
                if (r.size == 4 && r.all { it.isFinite() && it in 0f..1f } && r[2]-r[0] > .1f && r[3]-r[1] > .05f) roi = r
            }
            // Keep the output outside the OCR zone. No translation of our own overlay.
            output = when {
                roi[1] >= .40f -> floatArrayOf(.5f, .32f, .78f, 1f)
                roi[3] <= .60f -> floatArrayOf(.5f, .98f, .78f, 1f)
                else -> throw IllegalArgumentException("Nastav modrou oblast do horní nebo dolní části obrazu.")
            }
            val savedOutput = GameLingoDirectTVLayout.green(this)
            if (savedOutput != null) {
                val safeAbove = savedOutput[1] <= roi[1] - .05f
                val safeBelow = savedOutput[1] - .22f * savedOutput[3] >= roi[3] + .03f
                if (safeAbove || safeBelow) output = savedOutput
            }
            val metrics = resources.displayMetrics
            width = minOf(1280, metrics.widthPixels).coerceAtLeast(320)
            height = (width.toDouble() * metrics.heightPixels / metrics.widthPixels.coerceAtLeast(1)).toInt().coerceAtLeast(180)
            resolution = "${width} × ${height}"
            status = "Zahajuji MediaProjection"
            projectionState = "Zahajuji"
            val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(result, consent)
                ?: throw IllegalStateException("Snímání nebylo povoleno.")
            projection = p
            projectionState = "Povoleno"
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { if (active) { status = "Systém ukončil snímání"; stopSelf() } }
                override fun onCapturedContentResize(width: Int, height: Int) {
                    capturedSize = "${width} × ${height}"
                }
            }, ui)
            val thread = HandlerThread("GameLingo-TV-frames").apply { start() }
            worker = thread
            val handler = Handler(thread.looper)
            frameHandler = handler
            val images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader = images
            wantFrame.set(true)
            images.setOnImageAvailableListener({ source -> receive(source) }, handler)
            display = p.createVirtualDisplay("GameLingo-screen", width, height, metrics.densityDpi.coerceAtLeast(1),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, images.surface, null, ui)
                ?: throw IllegalStateException("VirtualDisplay nebyl vytvořen")
            displayState = "Vytvořen"
            ocr = Ctecka()
            startedAt = SystemClock.elapsedRealtime()
            translator = Prekladac(this, GameLingoLanguages.target(this), GameLingoLanguages.game(this))
            active = true; starting = false; status = source
            // Only start the independent overlay after MediaProjection and VirtualDisplay exist.
            running = true
            OverlayService.spust(this)
            ui.post(tick)
        } catch (e: Exception) {
            failStartup("Start snímání", e)
        }
    }
    private fun failStartup(stage: String, e: Exception) {
        status = stage + ": " + e.javaClass.simpleName + " · " + (e.message ?: "").take(180)
        android.util.Log.e("GameLingoCapture", status, e)
        startupFailed = true
        starting = false
        stopSelf()
    }
    private fun resetText() {
        epoch++; candidate = ""; shown = ""; translatedText = ""
        lastTextAt = 0; blankFrames = 0; blankSince = 0
    }
    private fun setSource(value: String) {
        if (source != value) {
            source = value
            status = value
            notification()
        }
    }
    // Acquire and close every Image on the dedicated frame thread. Copy just
    // the ROI, accounting for row stride and the unmapped last-row padding.
    private fun receive(images: ImageReader) {
        try {
            val image = images.acquireLatestImage() ?: return
            val bitmap = try {
                received.incrementAndGet()
                if (!wantFrame.compareAndSet(true, false)) return
                val plane = image.planes[0]
                val buffer = plane.buffer.duplicate()
                val w = image.width; val h = image.height
                require(plane.pixelStride == 4 && plane.rowStride >= w * 4)
                val x = (roi[0] * w).toInt().coerceIn(0,w-1)
                val y = (roi[1] * h).toInt().coerceIn(0,h-1)
                val right = (roi[2] * w).toInt().coerceIn(x+1,w)
                val bottom = (roi[3] * h).toInt().coerceIn(y+1,h)
                val cw = right-x; val ch = bottom-y
                val pixels = IntArray(cw * ch)
                for (row in 0 until ch) {
                    val offset = (y+row)*plane.rowStride+x*4
                    require(offset >= 0 && offset + cw*4 <= buffer.limit())
                    for (col in 0 until cw) {
                        val p = offset+col*4
                        val r = buffer.get(p).toInt() and 255
                        val g = buffer.get(p+1).toInt() and 255
                        val b = buffer.get(p+2).toInt() and 255
                        pixels[row*cw+col] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                Bitmap.createBitmap(pixels,cw,ch,Bitmap.Config.ARGB_8888)
            } finally { image.close() }
            decoded.incrementAndGet()
            ui.post {
                if (!active || paused || busy || RemotePlayService.aktivni || PrijemService.bezi) bitmap.recycle()
                else process(bitmap)
            }
        } catch (e: Exception) { frameError = e.javaClass.simpleName + ": " + (e.message ?: "").take(120) }
    }

    private fun process(cropped: Bitmap) {
        var light = 0
        for (y in 0 until cropped.height step 8) for (x in 0 until cropped.width step 8) {
            val pixel = cropped.getPixel(x,y)
            if ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255) > 24) light++
        }
        darkFrames = if (light == 0) darkFrames + 1 else 0
        // Explicit opt-in, one delayed ROI preview in RAM only.
        if (diagnostic && preview == null && SystemClock.elapsedRealtime() - startedAt >= 10000L)
            preview = cropped.copy(Bitmap.Config.ARGB_8888, false)
        if (darkFrames >= 3) {
            cropped.recycle(); resetText(); render("")
            setSource("Oblast obrazu je tmavá nebo nedostupná")
            return
        }
        setSource("OCR titulků")
        busy = true
        val ticket = epoch
        val read = ocr ?: run { cropped.recycle(); busy=false; return }
        try {
            read.prectiVse(cropped, { raw ->
                cropped.recycle()
                ui.post {
                    busy = false
                    if (!active || paused || ticket != epoch) return@post
                    ocrCalls.incrementAndGet()
                    if (raw.isNotBlank()) ocrHits.incrementAndGet()
                    ocrState = if (raw.isBlank()) "Bez textu" else "Text rozpoznán"
                    if (diagnostic && raw.isNotBlank()) lastRaw = raw.take(300)
                    accept(raw)
                }
            }, { cropped.recycle(); ui.post { busy=false; ocrState = "Chyba OCR" } })
        } catch (e: Exception) {
            cropped.recycle(); busy=false; ocrState = "Chyba OCR: " + e.javaClass.simpleName
        }
    }
    private fun accept(raw: String) {
        val value = raw.trim().take(500)
        val now = android.os.SystemClock.elapsedRealtime()
        if (value.isBlank()) {
            if (++blankFrames >= 2 && blankSince == 0L) {
                blankSince = now; candidate = ""; shown = ""; translatedText = ""; render("")
            }
            return
        }
        blankFrames = 0; blankSince = 0; lastTextAt = now
        if (value == shown && translatedText.isNotEmpty()) {
            if (now - lastRenderAt >= 2500L) render(translatedText)
            return
        }
        candidate = value
        val p = translator ?: return
        val ticket = epoch
        busy = true
        translationState = "Překládám"
        try { p.preloz(value, { translated ->
            ui.post {
                if (active && !paused && ticket == epoch && translated.isNotBlank()) {
                    translationState = "Přeloženo"; translationHits.incrementAndGet()
                    if (diagnostic) lastTranslated = translated.take(300)
                    shown = value; translatedText = translated; render(translated)
                }
                busy = false
            }
        }, { ui.post { if (active && ticket == epoch) { translationState = "Chyba překladu"; render("") }; busy = false } }) } catch (e: Exception) { translationState = "Chyba: " + e.javaClass.simpleName; busy = false }
    }
    private fun render(text: String) {
        lastRenderAt = android.os.SystemClock.elapsedRealtime()
        if (PrijemService.bezi || RemotePlayService.aktivni) return
        OverlayService.posli(this, Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_SET_TEXT).putExtra(OverlayService.EXTRA_TEXT, text)
            .putExtra(OverlayService.EXTRA_DOBA, 4500L).putExtra(OverlayService.EXTRA_VLASTNI, output))
    }
    private fun notification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gamelingo_screen", "Samostatný překlad TV", NotificationManager.IMPORTANCE_LOW))
        fun action(name: String, code: Int) = PendingIntent.getService(this,code,Intent(this,GameLingoScreenTranslationService::class.java).setAction(name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this,"gamelingo_screen").setContentTitle("GameLingo · samostatná TV")
            .setContentText(if (paused) "Pozastaveno" else source).setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_media_pause, if (paused) "Pokračovat" else "Pozastavit", action(PAUSE,1))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Zastavit",action(STOP,2)).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(9231,n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(9231,n)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        val wasActive = active
        if (starting) startupFailed = true
        starting = false
        pendingStart?.let { ui.removeCallbacks(it) }; pendingStart = null
        active=false; running=false; wantFrame.set(false); epoch++; ui.removeCallbacks(tick)
        if (wasActive && !status.startsWith("Systém ukončil")) status = "Zastaveno · poslední stav: " + source
        if (wasActive) render("")
        runCatching { display?.release() }; display=null
        val oldReader = reader; reader=null
        val handler = frameHandler; frameHandler=null
        val thread = worker; worker=null
        if (handler != null) {
            handler.post { runCatching { oldReader?.setOnImageAvailableListener(null,null); oldReader?.close() }; thread?.quitSafely() }
        } else runCatching { oldReader?.close() }
        runCatching { projection?.stop() }; projection=null
        runCatching { ocr?.zavri() }; ocr=null
        runCatching { translator?.zavri() }; translator=null
        super.onDestroy()
    }
    companion object {
        @Volatile var running = false
            private set
        @Volatile var startupFailed = false
            private set
        @Volatile private var foregroundState = "Nezahájena"
        @Volatile var status = "Zastaveno"
            internal set
        private val received = AtomicLong()
        private val decoded = AtomicLong()
        private val ocrCalls = AtomicLong()
        private val ocrHits = AtomicLong()
        private val translationHits = AtomicLong()
        @Volatile private var projectionState = "Nezahájeno"
        @Volatile private var displayState = "Nevytvořen"
        @Volatile private var resolution = "—"
        @Volatile private var capturedSize = "—"
        @Volatile private var frameError = "—"
        @Volatile private var ocrState = "Čeká"
        @Volatile private var translationState = "Čeká"
        @Volatile private var lastRaw = ""
        @Volatile private var lastTranslated = ""
        @Volatile private var preview: Bitmap? = null
        private const val STOP = "gamelingo.screen.STOP"
        private const val PAUSE = "gamelingo.screen.PAUSE"
        internal fun resetDiagnostics() {
            received.set(0); decoded.set(0); ocrCalls.set(0); ocrHits.set(0); translationHits.set(0)
            startupFailed=false; foregroundState="Nezahájena"
            projectionState="Nezahájeno"; displayState="Nevytvořen"; resolution="—"; capturedSize="—"
            frameError="—"; ocrState="Čeká"; translationState="Čeká"; clearPreview()
        }
        private fun clearPreview() { preview=null; lastRaw=""; lastTranslated="" }
        fun report(): String = "Stav: $status\nForeground služba: $foregroundState\nSouhlas / projekce: $projectionState\nVirtualDisplay: $displayState" +
            "\nSnímání: $resolution · zdroj: $capturedSize\nPřijaté snímky: ${received.get()} · výřezy: ${decoded.get()}" +
            "\nChyba snímku: $frameError\nOCR: $ocrState (${ocrHits.get()}/${ocrCalls.get()})" +
            "\nPřekladač: $translationState (${translationHits.get()})" +
            "\nPřekryv: " + (if (OverlayService.chyba != null) "hlášená chyba" else "bez hlášené chyby") +
            (if (lastRaw.isNotBlank()) "\nOCR: $lastRaw" else "") +
            (if (lastTranslated.isNotBlank()) "\nPřeklad: $lastTranslated" else "")
        fun stop(c: Context) { c.stopService(Intent(c,GameLingoScreenTranslationService::class.java)) }
        fun open(a: Activity) {
            if (running) {
                AlertDialog.Builder(a).setTitle("Samostatná TV").setMessage("Překlad je spuštěný. " + status)
                    .setPositiveButton("Zastavit") { _,_ -> stop(a) }
                    .setNeutralButton("Pozastavit / pokračovat") { _,_ ->
                        if (running) a.startService(Intent(a,GameLingoScreenTranslationService::class.java).setAction(PAUSE))
                    }.setNegativeButton("Zpět",null).show()
            } else AlertDialog.Builder(a).setTitle("Samostatná TV")
                .setMessage("OCR viditelných titulků ve vybrané oblasti. Po povolení snímání otevři video. Překlad běží do zastavení. Chráněný obraz může být nedostupný.")
                .setPositiveButton("Spustit překlad TV") { _,_ -> a.startActivity(Intent(a,GameLingoScreenConsentActivity::class.java)) }
                .setNegativeButton("Zrušit",null).show()
        }
        fun diagnostics(a: Activity) {
            if (running) stop(a)
            val panel = android.widget.LinearLayout(a).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(20,8,20,8) }
            panel.addView(android.widget.TextView(a).apply { text = report(); textSize = 15f })
            preview?.let { bmp ->
                panel.addView(android.widget.ImageView(a).apply {
                    setImageBitmap(bmp); adjustViewBounds=true; contentDescription="Diagnostický výřez OCR"
                    maxHeight = (a.resources.displayMetrics.heightPixels * .22f).toInt()
                })
            }
            val overlayLog = android.widget.TextView(a).apply {
                text = OverlayService.overlayTestReport(); textSize = 13f
            }
            val overlayAction = GameLingoTVUi.action(a,
                if (OverlayService.overlayTestActive) "Vypnout test překryvu" else "Test překryvu") {
                if (running || RemotePlayService.aktivni || PrijemService.bezi) {
                    Toast.makeText(a,"Nejdříve zastav ostatní režimy.",Toast.LENGTH_LONG).show()
                } else {
                    val stopTest = OverlayService.overlayTestActive
                    val allowed = Settings.canDrawOverlays(a)
                    OverlayService.recordOverlayTest("Oprávnění před spuštěním: " + allowed)
                    if (!allowed && !stopTest) {
                        Toast.makeText(a,"Povol překryv v nastavení TV.",Toast.LENGTH_LONG).show()
                    } else {
                        val request = Intent(a,OverlayService::class.java).setAction(
                            if (stopTest) OverlayService.ACTION_STOP_TEST_OVERLAY else OverlayService.ACTION_TEST_OVERLAY)
                        runCatching { a.startForegroundService(request) }
                            .onSuccess { OverlayService.recordOverlayTest("Požadavek foreground service odeslán") }
                            .onFailure { OverlayService.recordOverlayTest("Start služby: chyba " + it.javaClass.simpleName) }
                    }
                    overlayLog.postDelayed({
                        overlayLog.text = OverlayService.overlayTestReport()
                        (overlayLog.parent as? android.widget.LinearLayout)?.let { parent ->
                            for (i in 0 until parent.childCount) {
                                val v = parent.getChildAt(i) as? android.widget.TextView ?: continue
                                if (v.text == "Test překryvu" || v.text == "Vypnout test překryvu")
                                    v.text = if (OverlayService.overlayTestActive) "Vypnout test překryvu" else "Test překryvu"
                            }
                        }
                    },800L)
                }
            }
            panel.addView(overlayAction)
            panel.addView(overlayLog)
            panel.addView(GameLingoTVUi.action(a,"Test překladového jádra") {
                if (running || RemotePlayService.aktivni || PrijemService.bezi) {
                    Toast.makeText(a,"Nejdříve zastav ostatní režimy.",Toast.LENGTH_LONG).show()
                } else {
                    val p = Prekladac(a,GameLingoLanguages.target(a),"en")
                    val done = AtomicBoolean(false)
                    val handler = Handler(Looper.getMainLooper())
                    fun finishTest(text: String) {
                        if (!done.compareAndSet(false,true)) return
                        handler.post {
                            runCatching { p.zavri() }
                            if (!a.isFinishing && !a.isDestroyed) AlertDialog.Builder(a)
                                .setTitle("Test překladu").setMessage(text).setPositiveButton("OK",null).show()
                        }
                    }
                    handler.postDelayed({ finishTest("Vypršel čas testu.") },15000)
                    try { p.preloz("This is a test of GameLingo subtitles.",{ finishTest(it) },{ finishTest("Překlad selhal.") }) }
                    catch (e: Exception) { finishTest("Chyba: " + e.javaClass.simpleName) }
                }
            })
            val dialog = AlertDialog.Builder(a).setTitle("Diagnostika samostatného OCR")
                .setView(android.widget.ScrollView(a).apply { addView(panel) })
                .setPositiveButton("Test snímání") { _,_ ->
                    AlertDialog.Builder(a).setMessage("Po souhlasu otevři video do 10 sekund. Výřez a poslední rozpoznaný text zůstanou pouze v paměti do vymazání nebo nové relace. Po návratu sem se snímání zastaví.")
                        .setPositiveButton("Spustit test") { _,_ -> a.startActivity(Intent(a,GameLingoScreenConsentActivity::class.java).putExtra("diagnostic",true)) }
                        .setNegativeButton("Zrušit",null).show()
                }.setNeutralButton("Vymazat náhled") { _,_ -> clearPreview(); diagnostics(a) }
                .setNegativeButton("Zpět",null).show()
            dialog.window?.setLayout((a.resources.displayMetrics.widthPixels * .72f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
