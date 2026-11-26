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
                    Toast.makeText(this@GameLingoScreenConsentActivity, GameLingoLanguages.ui(this@GameLingoScreenConsentActivity, GameLingoScreenTranslationService.status), Toast.LENGTH_LONG).show()
                    finish()
                }
                SystemClock.elapsedRealtime() >= startupDeadline -> {
                    waitingForCapture = false
                    GameLingoScreenTranslationService.status = "Služba snímání neodpověděla"
                    GameLingoScreenTranslationService.stop(this@GameLingoScreenConsentActivity)
                    Toast.makeText(this@GameLingoScreenConsentActivity, GameLingoLanguages.ui(this@GameLingoScreenConsentActivity, GameLingoScreenTranslationService.status), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, GameLingoLanguages.ui(this, "Zastav ostatní režimy a povol překryv."), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, GameLingoLanguages.ui(this, "Snímání nelze spustit."), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, GameLingoLanguages.ui(this, "Překlad se nepodařilo spustit."), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}

/** Experimental continuous OCR for capturable TV apps. Only one frame and one translation in flight. */
class GameLingoScreenTranslationService : Service() {
    private val ui = Handler(Looper.getMainLooper())
    private val phases = newPhaseMetrics()
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var ocr: Ctecka? = null
    private var translator: Prekladac? = null
    @Volatile private var active = false
    private var starting = false
    private var pendingStart: Runnable? = null
    @Volatile private var paused = false
    private var source = "Čekám na první snímek"
    private var worker: HandlerThread? = null
    private var frameHandler: Handler? = null
    private val wantFrame = java.util.concurrent.atomic.AtomicInteger(0)
    private var startedAt = 0L
    private val lastFrameReceivedAt = AtomicLong()
    private var sampleRequestedAt = 0L
    private var ocrInFlightAt = 0L
    private var diagnostic = false
    private var lastTextAt = 0L
    private var translatedText = ""
    private val dialogueWindow = GameLingoDialogueWindow()
    private var lastRenderAt = 0L
    private var blankSince = 0L
    private var ocrBusy = false
    private val blueFrameGate = GameLingoFrameGate(650L)
    private val yellowFrameGate = GameLingoFrameGate(850L)
    private var translationBusy = false
    private var lastAttempt = ""
    private var lastAttemptAt = 0L
    private var epoch = 0L
    private var candidate = ""
    // Reuse the same two-read dialogue stabilization as console OCR.
    private val dialogueStability = StabilitaTextu()
    private var shown = ""
    private var blankFrames = 0
    private var darkFrames = 0
    private var width = 1280
    private var height = 540
    private var roi = floatArrayOf(.10f,.80f,.90f,.98f)
    private var output = floatArrayOf(.5f,.78f,.78f,1f)
    private var sourceLanguage = "en"
    private var dialogueEnabled = true
    private var coverOriginal = false
    private var storyMode = false
    private var durationPercent = 100
    private var hintsEnabled = false
    private val foreground by lazy { Popredi(this) }
    private val launcherPackage by lazy {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)
            ?.activityInfo?.packageName
    }
    private var observedPackage: String? = null
    @Volatile private var blockedHome = false
    private var hintText = ""
    private var hintEpoch = 0L
    private var hintBusy = false
    private var hintLastAt = 0L
    private var hintAttempt = ""
    private var hintAttemptAt = 0L
    private var useAutomaticOutput = true
    private var cleanCapture = false
    private var capturePending = false
    private var sampleGeneration = 0L
    private var currentBox: FloatArray? = null
    private var currentRows = 1
    private var candidateAt = 0L
    private var shownAt = 0L
    private var shownUntil = 0L
    private var storySequence = 0L
    private var storyPage: CteciFronta.Stranka? = null
    private val story = PribehovaRelace({ lines, ok, fail ->
        val p = translator
        if (p == null) fail()
        else {
            val original=lines.joinToString("\n")
            val source=sourceLanguage
            val target=GameLingoLanguages.target(this)
            p.prelozPribeh(original, { result -> ui.post {
                val corrected=GameLingoGlossaryStore.apply(this,original,result,source,target)
                if(active && !paused) GameLingoTranslationHistory.append("Dialog",source,target,original,corrected)
                ok(corrected)
            } }, { ui.post { fail() } })
        }
    }, { translationState = it })
    private var yellowEnabled = false
    private var yellowTextMode = false
    private var yellowRoi = floatArrayOf(.1f,.1f,.9f,.5f)
    private var orangeOutput = floatArrayOf(.07f,.43f,.50f,.60f,1f)
    private var blueSamplesSinceYellow = 0
    private var lastYellowSampleAt = 0L
    private var yellowEpoch = 0L
    private var yellowCandidate = emptyList<String>()
    private var yellowShown = emptyList<String>()
    private var yellowSelected = -1
    private var yellowLastAt = 0L
    private var yellowRequest = false
    private var yellowAttemptAt = 0L
    private var yellowLastAttempt = emptyList<String>()
    // Partial results are session-local and belong to one yellow revision.
    private var yellowPartialEpoch = -1L
    private val yellowPartial = mutableListOf<String>()
    private var automaticCapture = false
    @Volatile private var automaticReader: GameLingoAutomaticOCR? = null
    private val automaticGeneration = AtomicLong()
    private fun stopAutomaticCapture() {
        automaticGeneration.incrementAndGet()
        automaticReader?.close()
        automaticReader = null
        if(automaticCapture) OverlayService.posli(this,Intent(this,OverlayService::class.java)
            .setAction(OverlayService.ACTION_SET_LOCAL_ZONES).putExtra(OverlayService.EXTRA_ZONE_PACKET,"PCZ1\t1.7778\t1\t0"))
    }
    private fun automaticCaptureTick() {
        if(paused || blockedHome) return
        val engine=automaticReader?.takeIf{it.alive()} ?: run {
            stopAutomaticCapture()
            val generation=automaticGeneration.get()
            val language=GameLingoLanguages.revision
            GameLingoAutomaticOCR(this,GameLingoAutomaticConfig.zones(this),
                {active&&!paused&&!blockedHome&&generation==automaticGeneration.get()&&
                    language==GameLingoLanguages.revision&&!RemotePlayService.aktivni&&!PrijemService.bezi},
                {packet -> OverlayService.posli(this,Intent(this,OverlayService::class.java)
                    .setAction(OverlayService.ACTION_SET_LOCAL_ZONES).putExtra(OverlayService.EXTRA_ZONE_PACKET,packet.encode()))}
            ).also{automaticReader=it}
        }
        val now=SystemClock.elapsedRealtime()
        if(!capturePending&&wantFrame.get()!=0&&sampleRequestedAt!=0L&&now-sampleRequestedAt>=1500L)cancelSample()
        if(engine.readyForFrame()&&!capturePending&&wantFrame.get()==0)requestSample(3)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            if (RemotePlayService.aktivni || PrijemService.bezi) { stopSelf(); return }
            val app = foreground.balicek()
            if (app != null && app != observedPackage) {
                observedPackage = app
                stopAutomaticCapture()
                cancelSample(); resetText(); clearYellow(); clearHint(); render("")
            }
            blockedHome = app != null && (app == launcherPackage || app == packageName)
            if (blockedHome) {
                watchdogState = "Čekám na podporovaný zdroj"
                setSource("Čekám na otevření videa nebo vstupu TV")
                if (active) ui.postDelayed(this, 350L)
                return
            }
            if (automaticCapture) {
                automaticCaptureTick()
                if (active) ui.postDelayed(this,350L)
                return
            }
            if (!paused) {
                val now = SystemClock.elapsedRealtime()
                val frameAt = lastFrameReceivedAt.get().takeIf { it > 0L } ?: startedAt
                watchdogState = GameLingoCaptureWatchdog.state(now - frameAt, ocrBusy,
                    if (ocrBusy) now - ocrInFlightAt else 0L)
                // Retry a lost sampling opportunity, not an active ML Kit call.
                if (!ocrBusy && !capturePending && wantFrame.get() != 0 &&
                    sampleRequestedAt != 0L && now - sampleRequestedAt >= 1500L) cancelSample()
            } else watchdogState = "Pozastaveno"
            if (hintLastAt != 0L && SystemClock.elapsedRealtime()-hintLastAt > 1800L) clearHint()
            if (!paused && !ocrBusy && !capturePending && wantFrame.get() == 0) {
                val blueEnabled = dialogueEnabled || hintsEnabled
                val now = SystemClock.elapsedRealtime()
                val yellowReady = yellowEnabled && GameLingoOCRConfig.standaloneYellowReady(now - lastYellowSampleAt)
                if (blueEnabled || yellowReady) {
                    val region = GameLingoOCRCadence.standaloneRegion(blueEnabled, yellowReady,
                        blueSamplesSinceYellow, now - lastYellowSampleAt)
                    if (region == 1) blueSamplesSinceYellow = (blueSamplesSinceYellow + 1).coerceAtMost(2)
                    else { blueSamplesSinceYellow = 0; lastYellowSampleAt = now }
                    requestSample(region)
                }
            }
            if (!paused && storyMode) {
                story.nastavCasovani(CasovaniPribehu.priZpozdeni(durationPercent,
                    (story.pocet - if (storyPage != null) 1 else 0).coerceAtLeast(0)))
                val page = story.krok(SystemClock.elapsedRealtime(), false,
                    yellowShown.isNotEmpty() && !yellowTextMode)
                if (page?.token != storyPage?.token || page?.index != storyPage?.index) {
                    storyPage = page
                    render(page?.text ?: "", page?.let { (it.doMs-SystemClock.elapsedRealtime()).coerceAtLeast(1L) } ?: 0L)
                }
            }
            if (!paused && !storyMode && shownUntil != 0L && SystemClock.elapsedRealtime() >= shownUntil) {
                shownUntil = 0L; translatedText = ""; render("")
            }
            if (!paused && yellowLastAt != 0L && SystemClock.elapsedRealtime() - yellowLastAt > 1800L) {
                clearYellow()
            }
            if (!paused && watchdogState != "Bez zjištěného výpadku")
                setSource(watchdogState)
            if (!paused && lastTextAt != 0L && SystemClock.elapsedRealtime() - lastTextAt > 1800L) {
                // Losing the OCR source invalidates pending work, not the reading
                // time already granted to a displayed ordinary dialogue.
                if (storyMode) { resetText(); render("") }
                else { hideAfterSourceGone(); resetText(preserveDisplay = true) }
            }
            if (active) ui.postDelayed(this, GameLingoOCRConfig.tvIntervalMs(false).coerceIn(if (cleanCapture) 450L else 200L, 800L))
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == PAUSE) {
            if (!active) { stopSelf(); return START_NOT_STICKY }
            paused = !paused; stopAutomaticCapture(); cancelSample(); resetText(); render(""); clearYellow(); clearHint()
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
            GameLingoOCRConfig.load(this); OblastTitulku.nacti(this); Volby.nacti(this)
            val settings = Nastaveni(this)
            dialogueEnabled = settings.dialogy
            coverOriginal = settings.prekryvat
            storyMode = settings.pribehovy
            durationPercent = settings.casPribehuProcenta.coerceIn(CasovaniPribehu.MIN_PROCENT, CasovaniPribehu.MAX_PROCENT)
            hintsEnabled = settings.napovedy
            yellowEnabled = Volby.zapnuto
            yellowTextMode = Volby.rezimText
            automaticCapture = GameLingoAutomaticConfig.enabled(this) && !diagnostic
            check(automaticCapture || dialogueEnabled || yellowEnabled || hintsEnabled) { "Zapni dialogy, nápovědu nebo žlutou oblast v nastavení Překlad." }
            // Same saved regions and automatic dialogue crop as Remote Play.
            roi = if (OblastTitulku.rucne) GameLingoDirectTVLayout.blue(this)
                else if (GameLingoOCRConfig.expandSearch && GameLingoOCRConfig.mode != "classic") floatArrayOf(0f,.50f,1f,1f)
                else GameLingoOCRConfig.preset(this) ?: floatArrayOf(.05f,.70f,.95f,1f)
            yellowRoi = GameLingoDirectTVLayout.yellow(this)
            orangeOutput = GameLingoDirectTVLayout.orange(this)
            val inputs = listOfNotNull(roi.takeIf { dialogueEnabled }, yellowRoi.takeIf { yellowEnabled })
            fun overlaps(r: FloatArray) = inputs.any { GameLingoDirectTVLayout.intersects(it, r) }
            val savedOutput = GameLingoDirectTVLayout.green(this)
            useAutomaticOutput = savedOutput == null
            output = savedOutput ?: floatArrayOf(.5f, if (coverOriginal) roi[3] else (roi[1]-.02f).coerceAtLeast(.05f), .78f,1f)
            // Respect the selected layout; clean samples are required when the overlay may cover input.
            cleanCapture = coverOriginal || hintsEnabled || (dialogueEnabled &&
                (useAutomaticOutput || overlaps(GameLingoDirectTVLayout.greenBounds(output)))) ||
                (yellowEnabled && overlaps(orangeOutput))
            if(automaticCapture) cleanCapture = true
            layoutState = "Modrá: " + roi.joinToString("/") { (it*100).toInt().toString() } +
                " · zelená Y: " + (output[1]*100).toInt() + " % · žlutá: " +
                if (yellowEnabled) yellowRoi.joinToString("/") { (it*100).toInt().toString() } else "vypnuta"
            sourceLanguage = GameLingoLanguages.game(this)
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
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
            wantFrame.set(0)
            images.setOnImageAvailableListener({ source -> receive(source) }, handler)
            display = p.createVirtualDisplay("GameLingo-screen", width, height, metrics.densityDpi.coerceAtLeast(1),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, images.surface, null, ui)
                ?: throw IllegalStateException("VirtualDisplay nebyl vytvořen")
            displayState = "Vytvořen"
            ocr = Ctecka().apply { cilovyJazyk = GameLingoLanguages.target(this@GameLingoScreenTranslationService) }
            startedAt = SystemClock.elapsedRealtime()
            currentPhases = phases
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
    private fun resetText(preserveDisplay: Boolean = false) {
        epoch++; candidate = ""; shown = ""
        dialogueStability.reset()
        blueFrameGate.reset()
        candidateAt = 0L; currentBox = null; currentRows = 1
        if (!preserveDisplay) {
            translatedText = ""; shownAt = 0L; shownUntil = 0L
            dialogueWindow.reset()
            blueSamplesSinceYellow = 0; lastYellowSampleAt = SystemClock.elapsedRealtime()
        }
        story.reset(); storyPage = null
        lastAttempt = ""; lastAttemptAt = 0L
        lastTextAt = 0; blankFrames = 0; blankSince = 0
    }
    private fun setSource(value: String) {
        if (source != value) {
            source = value
            status = value
            notification()
        }
    }
    private fun requestSample(region: Int) {
        sampleRequestedAt = SystemClock.elapsedRealtime()
        if (!cleanCapture || !OverlayService.suppressStandaloneCapture(true)) {
            OverlayService.suppressStandaloneCapture(false)
            wantFrame.compareAndSet(0,region)
            return
        }
        capturePending = true
        val generation = ++sampleGeneration
        // Give the compositor time to remove our overlay before acquiring a fresh frame.
        ui.postDelayed({
            if (active && !paused && capturePending && generation == sampleGeneration)
                wantFrame.compareAndSet(0,region)
        }, 90L)
        // Always restore visibility if the projection stops delivering frames.
        ui.postDelayed({
            if (capturePending && generation == sampleGeneration) cancelSample()
        }, 350L)
    }
    private fun cancelSample() {
        sampleGeneration++; capturePending = false; wantFrame.set(0); sampleRequestedAt = 0L
        OverlayService.suppressStandaloneCapture(false)
    }
    // Acquire and close every Image on the dedicated frame thread. Copy just
    // the ROI, accounting for row stride and the unmapped last-row padding.
    private fun receive(images: ImageReader) {
        try {
            val image = images.acquireLatestImage() ?: return
            var yellow = false
            var fingerprint = 0L
            var cropStarted = 0L
            val bitmap = try {
                received.incrementAndGet()
                lastFrameReceivedAt.set(SystemClock.elapsedRealtime())
                val requested = wantFrame.getAndSet(0)
                if (requested == 0) return
                if (requested == 3) {
                    // The image lives only through the synchronous selected-ROI copy.
                    // Protected/black sources are not bypassed or stored.
                    try {
                        automaticReader?.offer(image.width,image.height){x,y,w,h->
                            val plane=image.planes[0];val data=plane.buffer.duplicate()
                            require(plane.pixelStride==4 && plane.rowStride>=image.width*4)
                            IntArray(w*h){i->
                                val p=(y+i/w)*plane.rowStride+(x+i%w)*4
                                val r=data.get(p).toInt() and 255
                                val g=data.get(p+1).toInt() and 255
                                val b=data.get(p+2).toInt() and 255
                                (255 shl 24) or (r shl 16) or (g shl 8) or b
                            }
                        }
                    } finally {
                        ui.post{if(capturePending)cancelSample()}
                    }
                    return
                }
                cropStarted = SystemClock.elapsedRealtime()
                yellow = requested == 2
                val region = if (yellow) yellowRoi else roi
                val plane = image.planes[0]
                val buffer = plane.buffer.duplicate()
                val w = image.width; val h = image.height
                require(plane.pixelStride == 4 && plane.rowStride >= w * 4)
                val x = (region[0] * w).toInt().coerceIn(0,w-1)
                val y = (region[1] * h).toInt().coerceIn(0,h-1)
                val right = (region[2] * w).toInt().coerceIn(x+1,w)
                val bottom = (region[3] * h).toInt().coerceIn(y+1,h)
                val cw = right-x; val ch = bottom-y
                val pixels = IntArray(cw * ch)
                fingerprint = GameLingoFrameGate.seed(cw, ch)
                for (row in 0 until ch) {
                    val offset = (y+row)*plane.rowStride+x*4
                    require(offset >= 0 && offset + cw*4 <= buffer.limit())
                    for (col in 0 until cw) {
                        val p = offset+col*4
                        val r = buffer.get(p).toInt() and 255
                        val g = buffer.get(p+1).toInt() and 255
                        val b = buffer.get(p+2).toInt() and 255
                        val pixel = (255 shl 24) or (r shl 16) or (g shl 8) or b
                        pixels[row*cw+col] = pixel
                        fingerprint = GameLingoFrameGate.mix(fingerprint, pixel)
                    }
                }
                Bitmap.createBitmap(pixels,cw,ch,Bitmap.Config.ARGB_8888)
            } finally { image.close() }
            decoded.incrementAndGet()
            val postedAt = SystemClock.elapsedRealtime()
            phases.record("Kopie výřezu", postedAt - cropStarted)
            ui.post {
                if (active && !paused) phases.record("Čekání výřezu na UI", SystemClock.elapsedRealtime() - postedAt)
                if (capturePending) cancelSample()
                if (!active || paused || blockedHome || ocrBusy || RemotePlayService.aktivni || PrijemService.bezi) bitmap.recycle()
                else if (yellow) processYellow(bitmap, fingerprint) else process(bitmap, fingerprint)
            }
        } catch (e: Exception) { frameError = e.javaClass.simpleName + ": " + (e.message ?: "").take(120) }
    }

    private fun process(cropped: Bitmap, fingerprint: Long) {
        val settled = !storyMode && !hintsEnabled && !diagnostic && blankFrames == 0 &&
            !translationBusy && candidate.isNotBlank() && candidate == shown
        val now = SystemClock.elapsedRealtime()
        if (settled && blueFrameGate.canSkip(fingerprint, now)) {
            lastTextAt = now
            cropped.recycle()
            return
        }
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
        ocrBusy = true
        val ticket = epoch
        val ocrStarted = SystemClock.elapsedRealtime()
        ocrInFlightAt = ocrStarted
        val read = ocr ?: run { cropped.recycle(); ocrBusy=false; return }
        try {
            read.prectiPosledniTitulek(cropped, hotovo = { result ->
                fun normalize(box: android.graphics.Rect?) = box?.let { floatArrayOf(
                    roi[0]+it.left.toFloat()/cropped.width*(roi[2]-roi[0]),
                    roi[1]+it.top.toFloat()/cropped.height*(roi[3]-roi[1]),
                    roi[0]+it.right.toFloat()/cropped.width*(roi[2]-roi[0]),
                    roi[1]+it.bottom.toFloat()/cropped.height*(roi[3]-roi[1])) }
                val normalized = normalize(result.box)
                val hintBox = normalize(result.napovedaBox)
                val raw = result.text
                val hint = result.napoveda
                cropped.recycle()
                ui.post {
                    ocrBusy = false
                    lastOcrMs = SystemClock.elapsedRealtime() - ocrStarted
                    phases.record("Dialog OCR", lastOcrMs)
                    if (!active || paused || blockedHome || ticket != epoch) return@post
                    ocrCalls.incrementAndGet()
                    if (raw.isNotBlank()) ocrHits.incrementAndGet()
                    ocrState = if (raw.isBlank()) "Bez textu" else "Text rozpoznán"
                    if (diagnostic && raw.isNotBlank()) lastRaw = raw.take(300)
                    if (storyMode && hintsEnabled && !hint.isNullOrBlank() && hintBox != null) acceptHint(hint,hintBox)
                    if (dialogueEnabled) {
                        if (raw.isNotBlank()) { currentBox = normalized; currentRows = result.radku.coerceAtLeast(1) }
                        accept(raw)
                        if (!storyMode && raw.isNotBlank() && GameLingoDialogueText.normalize(raw) == candidate)
                            blueFrameGate.record(fingerprint, ocrStarted)
                        else blueFrameGate.reset()
                    }
                    if (!storyMode && hintsEnabled && !hint.isNullOrBlank() && hintBox != null) acceptHint(hint,hintBox)
                }
            }, chyba = { cropped.recycle(); ui.post { ocrBusy=false; blueFrameGate.reset(); ocrState = "Chyba OCR" } },
                sourceLanguage = sourceLanguage, standaloneScreen = true)
        } catch (e: Exception) {
            cropped.recycle(); ocrBusy=false; blueFrameGate.reset(); ocrState = "Chyba OCR: " + e.javaClass.simpleName
        }
    }
    private fun hideAfterSourceGone() {
        if (!storyMode && GameLingoOCRConfig.hideOnSourceGone(this) && shownUntil != 0L) {
            val now = SystemClock.elapsedRealtime()
            shownUntil = minOf(shownUntil, now + GameLingoOCRConfig.sourceGoneDelay(this) * 1000L)
            if (shownUntil <= now) { shownUntil = 0L; translatedText = ""; render("") }
        }
    }
    private fun accept(raw: String) {
        // Keep the story reader's existing input; ordinary dialogue ignores
        // purely visual line wrapping and repeated whitespace.
        val value = if (storyMode) raw.trim().take(240) else GameLingoDialogueText.normalize(raw)
        val now = SystemClock.elapsedRealtime()
        if (value.isBlank()) {
            if (++blankFrames >= 2 && blankSince == 0L) {
                blankSince = now
                if (storyMode) story.zmizelo(++storySequence,now)
                else { dialogueStability.reset(); hideAfterSourceGone() }
                // Invalidate in-flight translation without shortening or extending
                // the existing display deadline. Explicit stop/context changes still clear immediately.
                // Forget the source marker, not the displayed text or its deadline.
                // A repeated line after confirmed absence is a new occurrence.
                epoch++; candidate = ""; shown = ""; lastAttempt = ""; lastAttemptAt = 0L
            }
            return
        }
        blankFrames = 0; blankSince = 0; lastTextAt = now
        if (storyMode) {
            if (value != candidate) {
                candidate = value
                story.prijmiOcr(++storySequence, listOf(value), intArrayOf(), false, now)
            }
            return
        }
        // Confirm suspicious shrinkage / unfinished extensions before replacing
        // the recognized source. Distinct complete replies need no extra wait.
        // Do not use fuzzy shared-word matching to suppress meaningful changes.
        if (!dialogueStability.prijmi(value, radku = currentRows)) return
        if (value != candidate) {
            // A -> B -> A is a new generation, even when the spelling matches
            // a request that is still running. Do not cancel the reading timer.
            epoch++
            lastAttempt = ""; lastAttemptAt = 0L
            candidateAt = now
            candidate = value
            // A new OCR source invalidates only the previous source marker.
            // Keep the already displayed text and its deadline until a valid
            // replacement arrives (or its own timer expires).
            if (shown != value) shown = ""
        }
        // Do not keep restarting the timer for the same OCR text.
        if (value == shown) return
        translateLatest()
    }
    private fun dialogueReady(now: Long): Boolean =
        active && !paused && !blockedHome && !storyMode && candidate.isNotBlank() &&
            candidate != shown && lastTextAt != 0L && now - lastTextAt <= 1800L &&
            (candidate != lastAttempt || now - lastAttemptAt >= 1000L) &&
            !GameLingoOCRConfig.expired(this, now - candidateAt)

    /** Independent OCR; ordinary standalone work shares one provider admission slot. */
    private fun translateLatest() {
        if (!active || paused || blockedHome || translationBusy || candidate.isBlank()) return
        if (!storyMode && (yellowRequest || hintBusy)) return
        val now = SystemClock.elapsedRealtime()
        if (lastTextAt == 0L || now - lastTextAt > 1800L || candidate == shown) return
        if (candidate == lastAttempt && now - lastAttemptAt < 1000L) return
        val p = translator ?: return
        val value = candidate
        val started = candidateAt
        if (GameLingoOCRConfig.expired(this,now-started)) return
        val ticket = epoch
        phases.record("Dialog čekání po OCR", now - started)
        lastAttempt = value; lastAttemptAt = now
        translationBusy = true
        translationState = "Překládám"
        fun completed(translated: String?) {
            ui.post {
                translationBusy = false
                lastTranslationMs = SystemClock.elapsedRealtime() - now
                phases.record("Dialog překlad včetně služby", lastTranslationMs)
                val current = active && !paused && !blockedHome && ticket == epoch && value == candidate &&
                    lastTextAt != 0L && SystemClock.elapsedRealtime() - lastTextAt <= 1800L
                if (current && !translated.isNullOrBlank() && !GameLingoOCRConfig.expired(this@GameLingoScreenTranslationService,SystemClock.elapsedRealtime()-started)) {
                    translationState = "Přeloženo"; translationHits.incrementAndGet()
                    val corrected=GameLingoGlossaryStore.apply(this@GameLingoScreenTranslationService,value,translated,sourceLanguage,GameLingoLanguages.target(this@GameLingoScreenTranslationService))
                    GameLingoTranslationHistory.append("Dialog",sourceLanguage,GameLingoLanguages.target(this@GameLingoScreenTranslationService),value,corrected)
                    if (diagnostic) lastTranslated = corrected.take(300)
                    shown = value; translatedText = corrected
                    val displayText = dialogueWindow.render(value, corrected,
                        GameLingoOCRConfig.showPreviousDialogue(this@GameLingoScreenTranslationService) && !coverOriginal && !useAutomaticOutput,
                        SystemClock.elapsedRealtime(), sourceLanguage + "|" + GameLingoLanguages.target(this@GameLingoScreenTranslationService),
                        previousCount = GameLingoOCRConfig.previousDialogueCount(this@GameLingoScreenTranslationService))
                    shownAt = SystemClock.elapsedRealtime()
                    val duration = GameLingoOCRConfig.displayMs(this@GameLingoScreenTranslationService,((1800L+60L*corrected.length)*durationPercent/100L).coerceIn(800L,15000L))
                    shownUntil = shownAt + duration
                    render(displayText, duration)
                    phases.record("Dialog od přijetí textu k předání", SystemClock.elapsedRealtime() - started)
                } else if (current) {
                    translationState = if(GameLingoOCRConfig.expired(this@GameLingoScreenTranslationService,SystemClock.elapsedRealtime()-started)) "Starý výsledek zahozen" else "Chyba překladu"
                    // A failed replacement neither clears nor renews the old caption.
                } else {
                    translationState = "Starý výsledek zahozen"
                }
                // Restart only for a changed generation. The newest candidate
                // may have the same spelling as the obsolete request.
                if (active && !paused && !blockedHome && ticket != epoch) translateLatest()
                if (!storyMode && !translationBusy) translateYellow()
            }
        }
        try { p.preloz(value, { completed(it) }, { completed(null) }) }
        catch (e: Exception) { completed(null) }
    }
    private fun clearHint() {
        hintEpoch++; hintText=""; hintLastAt=0L; hintAttempt=""; hintAttemptAt=0L
        renderHint("",null)
    }
    private fun renderHint(text: String, box: FloatArray?) {
        if (PrijemService.bezi || RemotePlayService.aktivni) return
        val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SET_NAPOVEDA)
            .putExtra(OverlayService.EXTRA_TEXT,text)
        if (box != null) intent.putExtra(OverlayService.EXTRA_BOX,box)
        OverlayService.posli(this,intent)
    }
    private fun acceptHint(text: String, box: FloatArray) {
        val now = SystemClock.elapsedRealtime()
        hintLastAt=now
        if (text != hintText) {
            hintText=text; hintEpoch++; hintAttempt=""; hintAttemptAt=0L
            renderHint("",null)
        }
        if (hintBusy || (text == hintAttempt && now-hintAttemptAt < 1500L)) return
        if (!storyMode && (translationBusy || yellowRequest || dialogueReady(now))) {
            translateLatest()
            return
        }
        val p = translator ?: return
        val ticket = hintEpoch
        hintBusy=true; hintAttempt=text; hintAttemptAt=now
        fun finish(result: String?) {
            ui.post {
                hintBusy=false
                if (active && !paused && !blockedHome && ticket == hintEpoch && text == hintText &&
                    SystemClock.elapsedRealtime()-hintLastAt <= 1800L && !result.isNullOrBlank()) {
                    renderHint(result,box)
                    hintAttemptAt=Long.MAX_VALUE
                }
                if (!storyMode) {
                    translateLatest()
                    if (!translationBusy) translateYellow()
                }
            }
        }
        try { p.preloz(text,{ finish(it) },{ finish(null) }) } catch (e: Exception) { finish(null) }
    }
    private fun clearYellow() {
        yellowEpoch++
        yellowFrameGate.reset()
        yellowPartial.clear(); yellowPartialEpoch = -1L
        yellowCandidate = emptyList(); yellowShown = emptyList(); yellowSelected = -1
        yellowLastAt = 0L; yellowLastAttempt = emptyList(); yellowAttemptAt = 0L
        renderYellow(emptyList(), -1)
    }
    private fun renderYellow(items: List<String>, selected: Int) {
        if (PrijemService.bezi || RemotePlayService.aktivni) return
        OverlayService.posli(this, Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_SET_VOLBY)
            .putExtra(OverlayService.EXTRA_VOLBY, items.toTypedArray())
            .putExtra(OverlayService.EXTRA_VYBRANA, selected)
            .putExtra(OverlayService.EXTRA_OBLAST, yellowRoi)
            .putExtra(OverlayService.EXTRA_RAMECEK, false)
            .putExtra(OverlayService.EXTRA_VYSTUP_VOLEB, orangeOutput))
    }
    private fun processYellow(cropped: Bitmap, fingerprint: Long) {
        val started = SystemClock.elapsedRealtime()
        if (!diagnostic && !yellowRequest && yellowShown.isNotEmpty() &&
            yellowFrameGate.canSkip(fingerprint, started)) {
            yellowLastAt = started
            cropped.recycle()
            return
        }
        val read = ocr ?: run { cropped.recycle(); return }
        setSource("OCR herních textů")
        ocrBusy = true
        ocrInFlightAt = started
        val ticket = yellowEpoch
        try {
            read.prectiVolby(cropped, { lines ->
                val cleaned = lines.filter { it.first.count { c -> c.isLetter() } >= 2 }.take(8)
                val selected = if (yellowTextMode) Volby.VYBRANA_TEXT else yellowHighlight(cropped, cleaned.map { it.second })
                val words = if (yellowTextMode) listOf(cleaned.joinToString(" ") { it.first }.trim()).filter { it.isNotBlank() }
                    else cleaned.map { it.first.trim() }
                cropped.recycle()
                ui.post {
                    ocrBusy = false
                    phases.record("Herní text OCR", SystemClock.elapsedRealtime() - started)
                    if (!active || paused || ticket != yellowEpoch) return@post
                    if (words.isEmpty() || (!yellowTextMode && selected < 0 && words != yellowCandidate)) {
                        yellowFrameGate.reset()
                        return@post
                    }
                    yellowFrameGate.record(fingerprint, started)
                    yellowLastAt = SystemClock.elapsedRealtime()
                    if (words != yellowCandidate) {
                        yellowEpoch++
                        yellowPartial.clear(); yellowPartialEpoch = -1L
                        yellowLastAttempt = emptyList(); yellowAttemptAt = 0L
                        yellowCandidate = words; yellowShown = emptyList(); renderYellow(emptyList(),-1)
                    }
                    if (selected >= 0 || yellowTextMode) yellowSelected = selected
                    if (yellowShown.isNotEmpty()) renderYellow(yellowShown,yellowSelected)
                    else translateYellow()
                }
            }, { cropped.recycle(); ui.post { ocrBusy=false; yellowFrameGate.reset() } })
        } catch (e: Exception) { cropped.recycle(); ocrBusy=false; yellowFrameGate.reset() }
    }
    // Same brightness-based selection principle as the game choice reader.
    private fun yellowHighlight(b: Bitmap, boxes: List<android.graphics.Rect>): Int {
        if (boxes.size < 2) return -1
        val averages = boxes.map { r ->
            var sum = 0L; var count = 0
            for (y in r.top.coerceIn(0,b.height) until r.bottom.coerceIn(0,b.height) step 3)
                for (x in 0 until b.width step 5) {
                    val c=b.getPixel(x,y); sum += ((c shr 16 and 255)+(c shr 8 and 255)+(c and 255)); count++
                }
            if (count == 0) 0.0 else sum.toDouble()/count
        }
        val ordered = averages.indices.sortedByDescending { averages[it] }
        return if (averages[ordered[0]]-averages[ordered[1]] >= 40.0) ordered[0] else -1
    }
    private fun translateYellow() {
        val value = yellowCandidate
        val now = SystemClock.elapsedRealtime()
        if (!active || paused || blockedHome || yellowRequest || yellowShown.isNotEmpty() || value.isEmpty() || now-yellowLastAt > 1800L ||
            (value == yellowLastAttempt && now-yellowAttemptAt < 1000L)) return
        if (!storyMode && (translationBusy || hintBusy || dialogueReady(now))) {
            translateLatest()
            return
        }
        val p = translator ?: return
        val ticket = yellowEpoch
        yellowRequest = true; yellowLastAttempt = value; yellowAttemptAt = now
        if (yellowPartialEpoch != ticket) { yellowPartial.clear(); yellowPartialEpoch = ticket }
        val results = yellowPartial.toMutableList()
        fun current() = active && !paused && !blockedHome && ticket == yellowEpoch && value == yellowCandidate &&
            SystemClock.elapsedRealtime()-yellowLastAt <= 1800L
        fun finish() {
            yellowRequest=false
            if (!storyMode) translateLatest()
            if (active && !paused && !blockedHome && ticket != yellowEpoch) translateYellow()
        }
        fun next(index: Int) {
            if (!current()) { finish(); return }
            if (index == value.size) {
                yellowShown = results.toList()
                yellowPartial.clear(); yellowPartialEpoch = -1L
                GameLingoTranslationHistory.append(if(yellowTextMode)"Herní text" else "Volby",sourceLanguage,GameLingoLanguages.target(this),value.joinToString("\n"),yellowShown.joinToString("\n"))
                renderYellow(yellowShown, yellowSelected)
                finish(); return
            }
            // Yield between items, never in the middle of a provider call.
            // Preserve the accepted prefix even when the persistent cache is disabled.
            if (!storyMode && dialogueReady(SystemClock.elapsedRealtime())) {
                yellowPartial.clear(); yellowPartial.addAll(results)
                yellowPartialEpoch = ticket
                yellowAttemptAt = 0L
                yellowRequest = false
                translateLatest()
                return
            }
            val itemStarted = SystemClock.elapsedRealtime()
            try {
                p.preloz(value[index], { result ->
                    ui.post {
                        phases.record("Herní text překlad položky", SystemClock.elapsedRealtime() - itemStarted)
                        if (!current() || result.isBlank()) finish()
                        else { results.add(GameLingoGlossaryStore.apply(this@GameLingoScreenTranslationService,value[index],result,sourceLanguage,GameLingoLanguages.target(this@GameLingoScreenTranslationService))); next(index+1) }
                    }
                }, { ui.post { finish() } })
            } catch (e: Exception) { finish() }
        }
        next(results.size)
    }
    private fun render(text: String, duration: Long = 1800L) {
        lastRenderAt = android.os.SystemClock.elapsedRealtime()
        if (PrijemService.bezi || RemotePlayService.aktivni) return
        val intent = Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_SET_TEXT).putExtra(OverlayService.EXTRA_TEXT, text)
            .putExtra(OverlayService.EXTRA_DOBA, duration)
        val box = currentBox
        if (useAutomaticOutput && box != null && !storyMode) {
            intent.putExtra(OverlayService.EXTRA_BOX, box).putExtra(OverlayService.EXTRA_RADKU, currentRows)
        } else intent.putExtra(OverlayService.EXTRA_VLASTNI, output)
        OverlayService.posli(this, intent)
    }
    private fun notification() {
        fun tr(key: String) = GameLingoLanguages.ui(this, key)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gamelingo_screen", tr("Samostatný překlad TV"), NotificationManager.IMPORTANCE_LOW))
        fun action(name: String, code: Int) = PendingIntent.getService(this,code,Intent(this,GameLingoScreenTranslationService::class.java).setAction(name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this,"gamelingo_screen").setContentTitle(tr("GameLingo · samostatná TV"))
            .setContentText(localizedStatus(this, if (paused) "Pozastaveno" else source)).setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_media_pause, tr(if (paused) "Pokračovat" else "Pozastavit"), action(PAUSE,1))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,tr("Zastavit"),action(STOP,2)).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(9231,n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(9231,n)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        val wasActive = active
        if (starting) startupFailed = true
        starting = false
        pendingStart?.let { ui.removeCallbacks(it) }; pendingStart = null
        stopAutomaticCapture()
        cancelSample(); story.reset(); storyPage=null
        active=false; running=false; wantFrame.set(0); epoch++; ui.removeCallbacks(tick)
        if (wasActive && !status.startsWith("Systém ukončil")) status = "Zastaveno · poslední stav: " + source
        if (wasActive) { render(""); clearYellow(); clearHint() }
        val hadDisplay = display != null
        val releaseDisplay = runCatching { display?.release() }
        display=null
        if (hadDisplay) displayState = if (releaseDisplay.isSuccess) "Uvolněn" else "Chyba při uvolnění"
        val oldReader = reader; reader=null
        val handler = frameHandler; frameHandler=null
        val thread = worker; worker=null
        if (handler != null) {
            handler.post { runCatching { oldReader?.setOnImageAvailableListener(null,null); oldReader?.close() }; thread?.quitSafely() }
        } else runCatching { oldReader?.close() }
        val hadProjection = projection != null
        val releaseProjection = runCatching { projection?.stop() }
        projection=null
        if (hadProjection) projectionState = if (releaseProjection.isSuccess) "Ukončena" else "Chyba při ukončení"
        val stopForegroundResult = runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        if (foregroundState.startsWith("Aktivní")) foregroundState =
            if (stopForegroundResult.isSuccess) "Ukončena" else "Chyba při ukončení"
        runCatching { ocr?.zavri() }; ocr=null
        runCatching { translator?.zavri() }; translator=null
        super.onDestroy()
    }
    companion object {
        private fun newPhaseMetrics() = GameLingoPhaseMetrics(listOf(
            "Kopie výřezu", "Čekání výřezu na UI", "Dialog OCR", "Dialog čekání po OCR",
            "Dialog překlad včetně služby", "Dialog od přijetí textu k předání",
            "Herní text OCR", "Herní text překlad položky"))
        @Volatile private var currentPhases = newPhaseMetrics()
        private fun tr(c: Context?, key: String) = if (c == null) key else GameLingoLanguages.ui(c, key)
        // Localize UI only; preserve internal status values and exception details.
        fun localizedStatus(c: Context?, value: String): String {
            val prefix = listOf("Zastaveno · poslední stav:", "Dialog snímání:", "Start služby:",
                "Aktivace foreground služby:", "Start snímání:", "Chyba OCR:", "Chyba:")
                .firstOrNull { value.startsWith(it + " ") }
            if (prefix == null) return tr(c, value)
            val suffix = value.removePrefix(prefix + " ")
            return tr(c, prefix) + " " + (if (prefix == "Zastaveno · poslední stav:") tr(c, suffix) else suffix)
        }
        fun phaseSummary(c: Context? = null) = tr(c, "Samostatná TV – průměr/P95 (předání není potvrzené zobrazení):") + "\n" +
            currentPhases.summary() + "\n" + GameLingoNetworkPerformance.summary()
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
        @Volatile private var watchdogState = "Čeká"
        @Volatile private var ocrState = "Čeká"
        @Volatile private var translationState = "Čeká"
        @Volatile private var layoutState = "—"
        @Volatile private var lastOcrMs = 0L
        @Volatile private var lastTranslationMs = 0L
        @Volatile private var lastRaw = ""
        @Volatile private var lastTranslated = ""
        @Volatile private var preview: Bitmap? = null
        private const val STOP = "gamelingo.screen.STOP"
        private const val PAUSE = "gamelingo.screen.PAUSE"
        internal fun resetDiagnostics() {
            received.set(0); decoded.set(0); ocrCalls.set(0); ocrHits.set(0); translationHits.set(0)
            startupFailed=false; foregroundState="Nezahájena"
            projectionState="Nezahájeno"; displayState="Nevytvořen"; resolution="—"; capturedSize="—"
            frameError="—"; watchdogState="Čeká"; ocrState="Čeká"; translationState="Čeká"; layoutState="—"
            lastOcrMs=0L; lastTranslationMs=0L; currentPhases=newPhaseMetrics(); clearPreview()
        }
        private fun clearPreview() { preview=null; lastRaw=""; lastTranslated="" }
        fun report(c: Context? = null): String {
            fun label(key: String) = tr(c, key)
            fun state(value: String) = localizedStatus(c, value)
            return label("Stav:") + " " + state(status) +
                "\n" + label("Foreground služba:") + " " + state(foregroundState) +
                "\n" + label("Souhlas / projekce:") + " " + state(projectionState) +
                "\nVirtualDisplay: " + state(displayState) +
                "\n" + label("Snímání:") + " $resolution · " + label("zdroj:") + " $capturedSize" +
                "\n" + label("Přijaté snímky:") + " ${received.get()} · " + label("výřezy:") + " ${decoded.get()}" +
                "\n" + label("Chyba snímku:") + " $frameError\n" + label("Hlídač:") + " " + state(watchdogState) +
                "\nOCR: " + state(ocrState) + " (${ocrHits.get()}/${ocrCalls.get()})" +
                "\n" + label("Překladač:") + " " + state(translationState) + " (${translationHits.get()})" +
                "\nOCR: $lastOcrMs ms · " + label("překlad:") + " $lastTranslationMs ms\n" + label("Rozložení:") + " $layoutState" +
                "\n" + phaseSummary(c) +
                "\n" + label("Překryv:") + " " + label(if (OverlayService.chyba != null) "hlášená chyba" else "bez hlášené chyby") +
                (if (lastRaw.isNotBlank()) "\nOCR: $lastRaw" else "") +
                (if (lastTranslated.isNotBlank()) "\n" + label("Překlad:") + " $lastTranslated" else "")
        }
        fun stop(c: Context) { c.stopService(Intent(c,GameLingoScreenTranslationService::class.java)) }
        fun open(a: Activity) {
            if (running) {
                AlertDialog.Builder(a).setTitle(GameLingoLanguages.ui(a, "Samostatná TV")).setMessage(GameLingoLanguages.ui(a, "Překlad je spuštěný.") + " " + localizedStatus(a, status))
                    .setPositiveButton(GameLingoLanguages.ui(a, "Zastavit")) { _,_ -> stop(a) }
                    .setNeutralButton(GameLingoLanguages.ui(a, "Pozastavit / pokračovat")) { _,_ ->
                        if (running) a.startService(Intent(a,GameLingoScreenTranslationService::class.java).setAction(PAUSE))
                    }.setNegativeButton(GameLingoLanguages.ui(a, "Zpět"),null).show()
            } else GameLingoTVUi.select(a, GameLingoLanguages.ui(a, "Samostatná TV"), arrayOf(
                GameLingoLanguages.ui(a, "Spustit překlad TV · vybrat profil"), GameLingoLanguages.ui(a, "Rozložení oblastí"), GameLingoLanguages.ui(a, "Profily her a aplikací"))) { index ->
                when (index) {
                    0 -> GameLingoTVProfiles.chooseForStart(a) {
                        a.startActivity(Intent(a,GameLingoScreenConsentActivity::class.java))
                    }
                    1 -> GameLingoDirectTVLayout.show(a) { open(a) }
                    2 -> GameLingoTVProfiles.show(a)
                }
            }
        }
        fun diagnostics(a: Activity) {
            if (running) stop(a)
            val panel = android.widget.LinearLayout(a).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(20,8,20,8) }
            val reportView = android.widget.TextView(a).apply { text = report(a); textSize = 15f }
            panel.addView(reportView)
            // stopService is asynchronous; refresh when onDestroy has completed.
            // Preserve final counters for diagnosis, not stale active-state labels.
            val reportRefresh = object : Runnable {
                override fun run() {
                    if (!reportView.isAttachedToWindow) return
                    reportView.text = report(a)
                    reportView.postDelayed(this,500L)
                }
            }
            reportView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) { reportView.post(reportRefresh) }
                override fun onViewDetachedFromWindow(v: android.view.View) { reportView.removeCallbacks(reportRefresh) }
            })
            preview?.let { bmp ->
                panel.addView(android.widget.ImageView(a).apply {
                    setImageBitmap(bmp); adjustViewBounds=true; contentDescription=GameLingoLanguages.ui(a, "Diagnostický výřez OCR")
                    maxHeight = (a.resources.displayMetrics.heightPixels * .22f).toInt()
                })
            }
            val overlayLog = android.widget.TextView(a).apply {
                text = OverlayService.overlayTestReport(); textSize = 13f
            }
            val overlayAction = GameLingoTVUi.action(a,
                if (OverlayService.overlayTestActive) GameLingoLanguages.ui(a, "Vypnout test překryvu") else GameLingoLanguages.ui(a, "Test překryvu")) {
                if (running || RemotePlayService.aktivni || PrijemService.bezi) {
                    Toast.makeText(a,GameLingoLanguages.ui(a, "Nejdříve zastav ostatní režimy."),Toast.LENGTH_LONG).show()
                } else {
                    val stopTest = OverlayService.overlayTestActive
                    val allowed = Settings.canDrawOverlays(a)
                    OverlayService.recordOverlayTest("Oprávnění před spuštěním: " + allowed)
                    if (!allowed && !stopTest) {
                        Toast.makeText(a,GameLingoLanguages.ui(a, "Povol překryv v nastavení TV."),Toast.LENGTH_LONG).show()
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
                                if (v.text == GameLingoLanguages.ui(a, "Test překryvu") || v.text == GameLingoLanguages.ui(a, "Vypnout test překryvu"))
                                    v.text = if (OverlayService.overlayTestActive) GameLingoLanguages.ui(a, "Vypnout test překryvu") else GameLingoLanguages.ui(a, "Test překryvu")
                            }
                        }
                    },800L)
                }
            }
            panel.addView(overlayAction)
            panel.addView(overlayLog)
            panel.addView(GameLingoTVUi.action(a,GameLingoLanguages.ui(a, "Test překladového jádra")) {
                if (running || RemotePlayService.aktivni || PrijemService.bezi) {
                    Toast.makeText(a,GameLingoLanguages.ui(a, "Nejdříve zastav ostatní režimy."),Toast.LENGTH_LONG).show()
                } else {
                    val p = Prekladac(a,GameLingoLanguages.target(a),"en")
                    val done = AtomicBoolean(false)
                    val handler = Handler(Looper.getMainLooper())
                    fun finishTest(text: String) {
                        if (!done.compareAndSet(false,true)) return
                        handler.post {
                            runCatching { p.zavri() }
                            if (!a.isFinishing && !a.isDestroyed) AlertDialog.Builder(a)
                                .setTitle(GameLingoLanguages.ui(a, "Test překladu")).setMessage(text).setPositiveButton("OK",null).show()
                        }
                    }
                    handler.postDelayed({ finishTest(GameLingoLanguages.ui(a, "Vypršel čas testu.")) },15000)
                    try { p.preloz("This is a test of GameLingo subtitles.",{ finishTest(it) },{ finishTest(GameLingoLanguages.ui(a, "Překlad selhal.")) }) }
                    catch (e: Exception) { finishTest(GameLingoLanguages.ui(a, "Chyba:") + " " + e.javaClass.simpleName) }
                }
            })
            var page: android.app.Dialog?=null
            panel.addView(GameLingoTVUi.action(a,GameLingoLanguages.ui(a, "Test snímání")) {
                page?.dismiss()
                    AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a, "Po souhlasu otevři video do 10 sekund. Výřez a poslední rozpoznaný text zůstanou pouze v paměti do vymazání nebo nové relace. Po návratu sem se snímání zastaví."))
                        .setPositiveButton(GameLingoLanguages.ui(a, "Spustit test")) { _,_ -> a.startActivity(Intent(a,GameLingoScreenConsentActivity::class.java).putExtra("diagnostic",true)) }
                        .setNegativeButton(GameLingoLanguages.ui(a, "Zrušit"),null).show()
            })
            panel.addView(GameLingoTVUi.action(a,GameLingoLanguages.ui(a, "Vymazat náhled")){page?.dismiss();clearPreview();diagnostics(a)})
            page=GameLingoTVUi.page(a,GameLingoLanguages.ui(a, "Diagnostika samostatného OCR"),panel,helpTopic="diagnostics")
        }
    }
}
