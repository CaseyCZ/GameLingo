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
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast

/** System consent is always requested anew. This does not bypass secure/DRM surfaces. */
class GameLingoScreenConsentActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state != null) return
        if (!Settings.canDrawOverlays(this) || RemotePlayService.aktivni || PrijemService.bezi) {
            Toast.makeText(this, "Zastav ostatní režimy a povol překryv.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        try {
            startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 9231)
        } catch (_: Exception) {
            Toast.makeText(this, "Televize nepodporuje snímání obrazovky.", Toast.LENGTH_LONG).show(); finish()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9231 && resultCode == RESULT_OK && data != null) {
            runCatching {
                startForegroundService(Intent(this, GameLingoScreenTranslationService::class.java)
                    .putExtra("result", resultCode).putExtra("consent", data))
            }.onFailure { Toast.makeText(this, "Snímání se nepodařilo spustit.", Toast.LENGTH_LONG).show() }
            moveTaskToBack(true)
        }
        finish()
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
    private var paused = false
    private var busy = false
    private var epoch = 0L
    private var candidate = ""
    private var shown = ""
    private var blankFrames = 0
    private var darkFrames = 0
    private var width = 960
    private var height = 540
    private var roi = floatArrayOf(.05f,.68f,.95f,.98f)
    private var output = floatArrayOf(.5f,.32f,.78f,1f)
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            runCatching { sample() }.onFailure { busy = false }
            if (active) ui.postDelayed(this, GameLingoOCRConfig.tvIntervalMs(false).coerceIn(500L, 1500L))
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == PAUSE) {
            if (!active) { stopSelf(); return START_NOT_STICKY }
            paused = !paused; epoch++; candidate = ""; shown = ""; render("")
            notification()
            return START_NOT_STICKY
        }
        if (active) return START_NOT_STICKY
        notification()
        val result = intent?.getIntExtra("result", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val consent: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("consent", Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra("consent")
        if (result != Activity.RESULT_OK || consent == null || !Settings.canDrawOverlays(this) ||
            RemotePlayService.aktivni || PrijemService.bezi) { stopSelf(); return START_NOT_STICKY }
        try {
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
            val metrics = resources.displayMetrics
            width = minOf(960, metrics.widthPixels).coerceAtLeast(320)
            height = (width.toDouble() * metrics.heightPixels / metrics.widthPixels).toInt().coerceAtLeast(180)
            val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(result, consent)
                ?: throw IllegalStateException("Snímání nebylo povoleno.")
            projection = p
            p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, ui)
            val images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader = images
            display = p.createVirtualDisplay("GameLingo-screen", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, images.surface, null, ui)
            ocr = Ctecka()
            translator = Prekladac(this, GameLingoLanguages.target(this), GameLingoLanguages.game(this))
            active = true; running = true
            OverlayService.spust(this)
            ui.post(tick)
        } catch (_: Exception) {
            Toast.makeText(this, "Snímání nelze spustit. Ověř oprávnění a modrou oblast.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun sample() {
        val image = reader?.acquireLatestImage() ?: return
        if (paused || busy || RemotePlayService.aktivni || PrijemService.bezi) { image.close(); return }
        val full = try {
            val p = image.planes[0]
            val rowWidth = p.rowStride / p.pixelStride
            if (p.pixelStride != 4 || rowWidth < width) return
            Bitmap.createBitmap(rowWidth, height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(p.buffer) }
        } finally { image.close() }
        var light = 0
        for (y in 0 until height step 24) for (x in 0 until width step 24) {
            val pixel = full.getPixel(x,y)
            if ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255) > 24) light++
        }
        darkFrames = if (light == 0) darkFrames + 1 else 0
        if (darkFrames >= 6) {
            full.recycle()
            Toast.makeText(this, "Obraz je černý nebo chráněný. Snímání zastaveno.", Toast.LENGTH_LONG).show()
            stopSelf(); return
        }
        val x = (roi[0]*width).toInt().coerceIn(0,width-2)
        val y = (roi[1]*height).toInt().coerceIn(0,height-2)
        val w = ((roi[2]-roi[0])*width).toInt().coerceIn(1,width-x)
        val h = ((roi[3]-roi[1])*height).toInt().coerceIn(1,height-y)
        val cropped = Bitmap.createBitmap(full,x,y,w,h)
        if (cropped !== full) full.recycle()
        busy = true
        val ticket = epoch
        val read = ocr ?: run { cropped.recycle(); busy=false; return }
        read.prectiVse(cropped, { raw ->
            cropped.recycle()
            ui.post {
                if (!active || paused || ticket != epoch) { busy=false; return@post }
                val value = raw.trim()
                if (value.isBlank()) {
                    if (++blankFrames >= 2) { candidate=""; shown=""; render("") }
                    busy=false; return@post
                }
                blankFrames=0
                if (value == shown) { busy=false; return@post }
                if (value != candidate) { candidate=value; busy=false; return@post }
                val p = translator ?: run { busy=false; return@post }
                p.preloz(value, { translated ->
                    ui.post {
                        if (active && !paused && ticket == epoch && translated.isNotBlank()) {
                            shown=value; render(translated)
                        }
                        busy=false
                    }
                }, { ui.post { if (active && ticket==epoch) render(""); busy=false } })
            }
        }, { cropped.recycle(); ui.post { busy=false } })
    }
    private fun render(text: String) {
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
            .setContentText(if (paused) "Pozastaveno" else "Snímání obrazovky – OCR").setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_media_pause, if (paused) "Pokračovat" else "Pozastavit", action(PAUSE,1))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Zastavit",action(STOP,2)).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(9231,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(9231,n)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        active=false; running=false; epoch++; ui.removeCallbacks(tick)
        render("")
        runCatching { display?.release() }; display=null
        runCatching { reader?.close() }; reader=null
        runCatching { projection?.stop() }; projection=null
        runCatching { ocr?.zavri() }; ocr=null
        runCatching { translator?.zavri() }; translator=null
        super.onDestroy()
    }
    companion object {
        @Volatile var running = false
            private set
        private const val STOP = "gamelingo.screen.STOP"
        private const val PAUSE = "gamelingo.screen.PAUSE"
        fun stop(c: Context) { c.stopService(Intent(c,GameLingoScreenTranslationService::class.java)) }
        fun open(a: Activity) {
            if (running) {
                AlertDialog.Builder(a).setTitle("Samostatná TV").setMessage("Překlad obrazovky je spuštěný.")
                    .setPositiveButton("Zastavit") { _,_ -> stop(a) }.setNegativeButton("Zpět",null).show()
            } else AlertDialog.Builder(a).setTitle("Samostatná TV – test")
                .setMessage("Překlad viditelných titulků přes OCR. Vyžaduje souhlas se snímáním celé obrazovky. Chráněný obraz může být černý; jeho ochranu aplikace neobchází.")
                .setPositiveButton("Spustit") { _,_ -> a.startActivity(Intent(a,GameLingoScreenConsentActivity::class.java)) }
                .setNegativeButton("Zrušit",null).show()
        }
    }
}
