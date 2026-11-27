package cz.titulky.tv

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.graphics.Path
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** Mobile-only camera source; no console, microphone, photo files or screen capture. */
class GameLingoCameraActivity : ComponentActivity() {
    companion object {
        private const val CAMERA_PERMISSION = 701
        fun open(activity: Activity) {
            if (RemotePlayService.aktivni || PrijemService.bezi) {
                Toast.makeText(activity, GameLingoLanguages.ui(activity,
                    "Nejdříve zastav překlad z konzole nebo telefonu."), Toast.LENGTH_LONG).show()
                return
            }
            activity.getSharedPreferences("gamelingo_devices", Activity.MODE_PRIVATE)
                .edit().putBoolean("cameraSourceSelected", true).apply()
            activity.startActivity(Intent(activity, GameLingoCameraActivity::class.java))
        }
    }

    private data class CameraSurface(val start: Int, val end: Int, val horizontal: Boolean)
    private data class CameraAppearance(val texture: Bitmap?, val weight: Int)
    // Normalized clockwise OCR corners; map a flat layout back to the source plane.
    private data class CameraQuad(val points: FloatArray) {
        fun projection(box: RectF, imageBounds: RectF): Matrix? {
            if (points.size != 8 || box.width() <= 0 || box.height() <= 0) return null
            val dst = FloatArray(8) { i -> if (i % 2 == 0)
                imageBounds.left + points[i] * imageBounds.width()
                else imageBounds.top + points[i] * imageBounds.height() }
            if (dst.any { !it.isFinite() }) return null
            for (i in 0 until 4) {
                val a = i * 2; val b = ((i + 1) % 4) * 2; val c = ((i + 2) % 4) * 2
                val cross = (dst[b]-dst[a])*(dst[c+1]-dst[b+1]) - (dst[b+1]-dst[a+1])*(dst[c]-dst[b])
                if (cross <= .001f) return null
            }
            val src = floatArrayOf(box.left, box.top, box.right, box.top,
                box.right, box.bottom, box.left, box.bottom)
            return Matrix().takeIf { it.setPolyToPoly(src, 0, dst, 0, 4) }
        }
    }
    private data class CameraBlock(val text: String, val rect: RectF, val angle: Float = 0f,
                                   val background: Int = Color.BLACK, val lineCount: Int = 1, val ink: Int? = null,
                                   val surface: CameraSurface? = null, val quad: CameraQuad? = null,
                                   val appearance: CameraAppearance = CameraAppearance(null, 0))
    private data class CameraOverlay(val text: String, val rect: RectF, val angle: Float,
                                     val background: Int, val lineCount: Int, val ink: Int?, val surface: CameraSurface?,
                                     val quad: CameraQuad?, val appearance: CameraAppearance)

    // Preserve an observed ink color only when repeated samples provide readable contrast.
    private fun sampleInk(bitmap: Bitmap, rect: RectF, background: Int): Int? {
        val bg = Color.luminance(background)
        val bins = HashMap<Int, IntArray>()
        fun contrast(color: Int): Float {
            val light = Color.luminance(color)
            return (maxOf(light, bg) + .05f) / (minOf(light, bg) + .05f)
        }
        for (row in 0 until 8) for (col in 0 until 24) {
            val x = rect.left + rect.width() * (col + .5f) / 24
            val y = rect.top + rect.height() * (row + .5f) / 8
            val rgb = bitmap.getPixel((x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1),
                (y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1))
            if (contrast(rgb) < 4.5f) continue
            val r = Color.red(rgb); val g = Color.green(rgb); val b = Color.blue(rgb)
            val key = (r / 32) * 64 + (g / 32) * 8 + b / 32
            val bin = bins.getOrPut(key) { IntArray(4) }
            bin[0]++; bin[1] += r; bin[2] += g; bin[3] += b
        }
        val best = bins.values.maxByOrNull { it[0] } ?: return null
        if (best[0] < 8) return null
        val rgb = Color.rgb(best[1] / best[0], best[2] / best[0], best[3] / best[0])
        return rgb.takeIf { contrast(it) >= 4.5f }
    }

    private fun columnOrder(blocks: List<CameraBlock>): List<CameraBlock> {
        if (blocks.size < 4) return blocks
        val sorted = blocks.sortedBy { it.rect.left }
        var edge = sorted.first().rect.right
        var split = 0
        var largestGap = .045f
        for (i in 1 until sorted.size) {
            val gap = sorted[i].rect.left - edge
            if (i >= 2 && sorted.size - i >= 2 && gap > largestGap) {
                largestGap = gap; split = i
            }
            edge = maxOf(edge, sorted[i].rect.right)
        }
        if (split == 0) return blocks
        val boundary = sorted[split].rect.left
        return columnOrder(blocks.filter { it.rect.left < boundary }) +
            columnOrder(blocks.filter { it.rect.left >= boundary })
    }

    // Approximate smooth backgrounds only when four edges support the ramp.
    // Bound work to 48 samples and avoid extrapolating into neighboring text.
    private fun reconstructedSurface(bitmap: Bitmap, rect: RectF, background: Int, ink: Int?): CameraSurface? {
        if (rect.width() <= .015f || rect.height() <= .015f ||
            rect.left < 0f || rect.top < 0f || rect.right > 1f || rect.bottom > 1f) return null
        fun sample(x: Float, y: Float): Int = bitmap.getPixel(
            (x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1),
            (y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1))
        fun distance(a: Int, b: Int): Int = maxOf(
            kotlin.math.abs(Color.red(a) - Color.red(b)),
            kotlin.math.abs(Color.green(a) - Color.green(b)),
            kotlin.math.abs(Color.blue(a) - Color.blue(b)))
        fun median(values: List<Int>): Int {
            fun channel(extract: (Int) -> Int) = values.map(extract).sorted()[values.size / 2]
            return Color.rgb(channel { Color.red(it) }, channel { Color.green(it) }, channel { Color.blue(it) })
        }
        fun blend(a: Int, b: Int, t: Float): Int {
            fun mix(x: Int, y: Int) = (x + (y - x) * t + .5f).toInt()
            return Color.rgb(mix(Color.red(a), Color.red(b)), mix(Color.green(a), Color.green(b)),
                mix(Color.blue(a), Color.blue(b)))
        }
        val ts = (0 until 12).map { (it + .5f) / 12 }
        val left = ts.map { sample(rect.left + rect.width() * .04f, rect.top + rect.height() * it) }
        val right = ts.map { sample(rect.right - rect.width() * .04f, rect.top + rect.height() * it) }
        val top = ts.map { sample(rect.left + rect.width() * it, rect.top + rect.height() * .08f) }
        val bottom = ts.map { sample(rect.left + rect.width() * it, rect.bottom - rect.height() * .08f) }
        val foreground = ink ?: if (Color.luminance(background) > .179f) Color.BLACK else Color.WHITE
        fun contrast(color: Int): Float {
            val a = Color.luminance(color); val b = Color.luminance(foreground)
            return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        }
        fun candidate(first: List<Int>, last: List<Int>, crossA: List<Int>, crossB: List<Int>,
                      horizontal: Boolean): CameraSurface? {
            val a = median(first); val b = median(last)
            if (distance(a, b) !in 12..100 ||
                first.count { distance(it, a) <= 22 } < 9 ||
                last.count { distance(it, b) <= 22 } < 9 ||
                contrast(a) < 4.5f || contrast(b) < 4.5f) return null
            val agreement = ts.indices.count { i ->
                val expected = blend(a, b, ts[i])
                distance(crossA[i], expected) <= 35 && distance(crossB[i], expected) <= 35
            }
            if (agreement < 8) return null
            return CameraSurface(a, b, horizontal)
        }
        return candidate(top, bottom, left, right, false) ?: candidate(left, right, top, bottom, true)
    }

    // Bounded ink-color mask, stroke estimate, and local donor restoration.
    // Preserve the non-mask texture; fall back when the mask or donors are unreliable.
    private fun textureAndWeight(bitmap: Bitmap, rect: RectF, ink: Int?, lineCount: Int,
                                 restore: Boolean): CameraAppearance {
        if (ink == null || rect.left < 0 || rect.top < 0 || rect.right > 1 || rect.bottom > 1)
            return CameraAppearance(null, 0)
        val w = minOf(96, (rect.width() * 256).toInt())
        val h = minOf(48, (rect.height() * 256).toInt())
        if (w < 24 || h < 10 || h / lineCount.coerceAtLeast(1) < 8) return CameraAppearance(null, 0)
        fun distance(a: Int, b: Int) = maxOf(
            kotlin.math.abs(Color.red(a)-Color.red(b)), kotlin.math.abs(Color.green(a)-Color.green(b)),
            kotlin.math.abs(Color.blue(a)-Color.blue(b)))
        val source = IntArray(w*h) { i ->
            val x = rect.left + rect.width() * (i%w + .5f)/w
            val y = rect.top + rect.height() * (i/w + .5f)/h
            bitmap.getPixel((x*bitmap.width).toInt().coerceIn(0,bitmap.width-1),
                (y*bitmap.height).toInt().coerceIn(0,bitmap.height-1))
        }
        val inkMask = BooleanArray(source.size) { distance(source[it], ink) <= 38 }
        val count = inkMask.count { it }
        if (count < maxOf(8,w*h/25) || count > w*h*45/100) return CameraAppearance(null,0)
        val thickness = ArrayList<Int>()
        for (y in 1 until h-1) for (x in 1 until w-1) if (inkMask[y*w+x]) {
            var l=x; var r=x; var t=y; var b=y
            while (l>0 && inkMask[y*w+l-1]) l--
            while (r+1<w && inkMask[y*w+r+1]) r++
            while (t>0 && inkMask[(t-1)*w+x]) t--
            while (b+1<h && inkMask[(b+1)*w+x]) b++
            thickness.add(minOf(r-l+1,b-t+1))
        }
        thickness.sort()
        val ratio = (thickness.getOrNull(thickness.size/2) ?: 1).toFloat()/h*lineCount.coerceAtLeast(1)
        val weight = if (ratio >= .22f) 2 else if (ratio >= .14f) 1 else 0
        if (!restore) return CameraAppearance(null,weight)
        val mask=inkMask.copyOf()
        for (y in 0 until h) for (x in 0 until w) if (inkMask[y*w+x]) {
            for (dy in -1..1) for (dx in -1..1) {
                val xx=x+dx; val yy=y+dy
                if (xx in 0 until w && yy in 0 until h) mask[yy*w+xx]=true
            }
        }
        if (mask.count { it } > w*h*65/100) return CameraAppearance(null,weight)
        val donors=source.indices.filter { !mask[it] }.map { source[it] }
        val first=donors.firstOrNull() ?: return CameraAppearance(null,weight)
        if (donors.count { distance(it,first)>24 } < donors.size/5) return CameraAppearance(null,weight)
        val result=source.copyOf()
        for (y in 0 until h) for (x in 0 until w) if (mask[y*w+x]) {
            var found: Int?=null
            for (radius in 1..8) {
                val candidates=arrayOf(x to y-radius,x to y+radius,x-radius to y,x+radius to y)
                for ((xx,yy) in candidates) if (xx in 0 until w && yy in 0 until h && !mask[yy*w+xx]) {
                    found=source[yy*w+xx]; break
                }
                if (found != null) break
            }
            result[y*w+x]=found ?: return CameraAppearance(null,weight)
        }
        return CameraAppearance(Bitmap.createBitmap(result,w,h,Bitmap.Config.ARGB_8888),weight)
    }

    private fun sampleBackground(bitmap: Bitmap, rect: RectF): Int {
        val bins = HashMap<Int, IntArray>()
        fun sample(x: Float, y: Float) {
            val px = (x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val py = (y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val rgb = bitmap.getPixel(px, py)
            val r = Color.red(rgb); val g = Color.green(rgb); val b = Color.blue(rgb)
            val key = (r / 32) * 64 + (g / 32) * 8 + b / 32
            val bin = bins.getOrPut(key) { IntArray(4) }
            bin[0]++; bin[1] += r; bin[2] += g; bin[3] += b
        }
        val dx = min(rect.width() * .08f, .008f)
        val dy = min(rect.height() * .12f, .008f)
        for (step in 0 until 12) {
            val t = (step + .5f) / 12
            val x = rect.left + rect.width() * t
            val y = rect.top + rect.height() * t
            sample(x, rect.top + dy); sample(x, rect.bottom - dy)
            sample(rect.left + dx, y); sample(rect.right - dx, y)
        }
        val best = bins.values.maxByOrNull { it[0] } ?: return Color.BLACK
        return Color.rgb(best[1] / best[0], best[2] / best[0], best[3] / best[0])
    }

    private data class Region(val full: Boolean = true, val centerY: Float = .78f,
                              val height: Float = .24f, val centerX: Float = .5f,
                              val width: Float = .9f) {
        fun rect(): RectF {
            if (full) return RectF(0f, 0f, 1f, 1f)
            val h = height.coerceIn(.08f, 1f)
            val y = (centerY - h / 2).coerceIn(0f, 1f - h)
            val w = width.coerceIn(.15f, 1f)
            val x = (centerX - w / 2).coerceIn(0f, 1f - w)
            return RectF(x, y, x + w, y + h)
        }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languageIdentifier = LanguageIdentification.getClient()
    private val ocrBusy = AtomicBoolean(false)
    @Volatile private var region = Region()
    @Volatile private var generation = 0
    @Volatile private var configRevision = 0
    @Volatile private var running = false
    private var lastSample = 0L // analysis worker only
    @Volatile private var analysisCost = 0L
    @Volatile private var stableFrames = 0
    private var previousOCR = "" // main thread
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var liveUseCase: Preview? = null
    private lateinit var livePreview: PreviewView
    private var camera: Camera? = null
    private var translator: Prekladac? = null
    private var source = "auto"
    private var translatorSource: String? = null
    private val cameraLanguages get() = listOf(GameLingoLanguages.Language("auto", tr("Automaticky"))) + GameLingoLanguages.targetLanguages
    private var target = "cs"
    private var paused = false
    private var torch = false
    private var torchPending = false
    private var torchFailed = false
    private var zoom = 1f
    private var candidate = ""
    private var emptyFrames = 0
    private var lastTranslated = ""
    private var translatedText = ""
    private var translatedLines = emptyList<String>()
    private val overlayCache = LinkedHashMap<String, String>()
    private var translationRequest = 0
    private var previousVisible = emptySet<String>()
    private var eligibleVisible = emptySet<String>()
    private var overlaySource: String? = null
    private var currentBlocks = emptyList<CameraBlock>()
    private var overlayEnabled = true
    private var tvTarget: TvProfil? = null
    private var tvEnabled = false
    private var tvProbing = false
    private var tvGeneration = 0
    private var lastTVSent = 0L
    private var translating = false
    private var retryAt = 0L
    private lateinit var preview: CameraFrame
    private lateinit var output: TextView
    private lateinit var outputViewport: ScrollView
    private lateinit var pauseButton: Button
    private lateinit var torchButton: ImageButton
    private lateinit var fullButton: Button
    private lateinit var areaButton: Button
    private lateinit var previewStatus: TextView
    private lateinit var previewContainer: FrameLayout
    private lateinit var overlayButton: Button
    private var tvButton: TextView? = null
    private lateinit var regionControls: LinearLayout
    private lateinit var languageButton: Button
    private fun tr(text: String) = GameLingoLanguages.ui(this, text)
    private fun px(value: Int) = GameLingoVisualKit.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("gamelingo_camera", MODE_PRIVATE)
        source = prefs.getString("source", "auto")?.takeIf { code -> cameraLanguages.any { it.code == code } } ?: "auto"
        target = prefs.getString("target", GameLingoLanguages.target(this))?.takeIf { code -> GameLingoLanguages.targetLanguages.any { it.code == code } } ?: GameLingoLanguages.target(this)
        fun coordinate(key: String, fallback: Float) = prefs.getFloat(key, fallback).takeIf { it.isFinite() } ?: fallback
        region = Region(prefs.getBoolean("full", true), coordinate("centerY", .78f),
            coordinate("height", .24f), coordinate("centerX", .5f), coordinate("width", .9f))
        overlayEnabled = prefs.getBoolean("overlay", true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(12), px(16), px(16))
            background = GameLingoVisualKit.pageBackground(this@GameLingoCameraActivity)
        }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = tr(label); isAllCaps = false; setTextColor(Color.WHITE)
            background = GameLingoVisualKit.card(this@GameLingoCameraActivity)
            minHeight = px(44); setOnClickListener { action() }
        }
        fun row(label: String, max: Int, initial: Int, change: (Int) -> Unit): LinearLayout =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@GameLingoCameraActivity).apply {
                    text = tr(label); setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(px(125), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(SeekBar(this@GameLingoCameraActivity).apply {
                    this.max = max; progress = initial; contentDescription = tr(label)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                            if (fromUser) change(value)
                        }
                        override fun onStartTrackingTouch(bar: SeekBar?) {}
                        override fun onStopTrackingTouch(bar: SeekBar?) {}
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        val title = TextView(this).apply {
            text = tr("Překlad kamerou"); textSize = 24f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        torchButton = ImageButton(this).apply {
            contentDescription = tr("Svítilna")
            background = GameLingoVisualKit.card(this@GameLingoCameraActivity)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            isEnabled = false
            setOnClickListener {
            val c = camera ?: return@setOnClickListener
            if (!c.cameraInfo.hasFlashUnit() || torchPending) return@setOnClickListener
            val desired = c.cameraInfo.torchState.value != TorchState.ON
            torchPending = true; torchFailed = false
            updateTorchButton()
            val future = c.cameraControl.enableTorch(desired)
            future.addListener({
                if (camera === c) {
                    torchFailed = !runCatching { future.get(); true }.getOrDefault(false)
                    torch = c.cameraInfo.torchState.value == TorchState.ON
                    torchPending = false
                    updateTorchButton()
                }
            }, ContextCompat.getMainExecutor(this))
            }
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(View(this@GameLingoCameraActivity), LinearLayout.LayoutParams(px(48), px(48)))
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(torchButton, LinearLayout.LayoutParams(px(48), px(48)))
        }
        root.addView(header)
        updateTorchButton()
        languageButton = button("Jazyky") { chooseLanguages() }
        updateLanguages()
        root.addView(languageButton)
        tvButton = TextView(this).apply {
            text = if (TvProfily(this@GameLingoCameraActivity).pouzeTelefon) tr("Telefon") else "Google TV / Android TV"
            gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }
        root.addView(tvButton)
        val modes = LinearLayout(this)
        fullButton = button("Celý obraz") { updateRegion(region.copy(full = true)); updateMode() }
        areaButton = button("Vybraná oblast") { updateRegion(region.copy(full = false)); updateMode() }
        modes.addView(fullButton, LinearLayout.LayoutParams(0, px(44), 1f))
        modes.addView(areaButton, LinearLayout.LayoutParams(0, px(44), 1f))
        root.addView(modes)
        overlayButton = button("Překlad přes původní text") {
            overlayEnabled = !overlayEnabled
            overlayButton.text = (if (overlayEnabled) "✓  " else "") + tr("Překlad přes původní text")
            getSharedPreferences("gamelingo_camera", MODE_PRIVATE).edit().putBoolean("overlay", overlayEnabled).apply()
            resetTranslation()
        }
        overlayButton.text = (if (overlayEnabled) "✓  " else "") + tr("Překlad přes původní text")
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
            background = GameLingoVisualKit.card(this@GameLingoCameraActivity, selected = true)
        }
        previewCard.addView(TextView(this).apply {
            text = tr("Náhled obrazovky")
            setTextColor(Color.WHITE); textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, px(8))
        })
        livePreview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        preview = CameraFrame(this).apply {
            region = this@GameLingoCameraActivity.region
            contentDescription = tr("Vyber oblast, potom uprav její polohu a velikost v náhledu.")
            onRegionCommitted = { value -> updateRegion(value) }
        }
        previewStatus = TextView(this).apply {
            text = tr("Čekám na první snímek")
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setPadding(px(8), px(5), px(8), px(5))
        }
        previewContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(livePreview, FrameLayout.LayoutParams(-1, -1))
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(previewStatus, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        previewCard.addView(previewContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(340)))
        previewContainer.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) updatePreviewHeight()
        }
        // The native preview runs independently of recognition. If it has
        // not begun streaming yet, show the latest analyzed image instead.
        livePreview.previewStreamState.observe(this) { stream ->
            preview.showBitmap = paused || stream != PreviewView.StreamState.STREAMING
            preview.invalidate()
        }
        regionControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@GameLingoCameraActivity).apply {
                text = tr("Vyber oblast, potom uprav její polohu a velikost v náhledu.")
                setTextColor(Color.parseColor("#92A4C7")); textSize = 13f
                setPadding(0, px(8), 0, px(4))
            })
            addView(button("Obnovit oblast") { updateRegion(Region(full = false)) })
        }
        previewCard.addView(regionControls)
        root.addView(previewCard)
        root.addView(overlayButton)
        updateMode()
        output = TextView(this).apply {
            gravity = Gravity.CENTER; minHeight = px(52); setPadding(px(8), px(8), px(8), px(8))
            textSize = 20f * GameLingoPhoneSubtitleStyle.scale(this@GameLingoCameraActivity)
            GameLingoPhoneSubtitleStyle.apply(this@GameLingoCameraActivity, this)
            setTextIsSelectable(true)
        }
        outputViewport = ScrollView(this).apply {
            isFillViewport = true
            isNestedScrollingEnabled = true
            addView(output, ViewGroup.LayoutParams(-1, -2))
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
        root.addView(outputViewport, LinearLayout.LayoutParams(-1, px(108)))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(6), px(16), px(8))
            background = GameLingoVisualKit.pageBackground(this@GameLingoCameraActivity)
        }
        controls.addView(row("Přiblížení", 30, 0) {
            zoom = 1f + it / 10f
            camera?.let { c -> c.cameraControl.setZoomRatio(zoom.coerceAtMost(c.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)) }
        })
        val actions = LinearLayout(this)
        pauseButton = button("Pozastavit") {
            paused = !paused
            pauseButton.text = tr(if (paused) "Pokračovat" else "Pozastavit")
            if (paused) releaseCamera(keepFrame = true) else startCamera()
        }
        actions.addView(pauseButton, LinearLayout.LayoutParams(0, px(48), 1f))
        controls.addView(actions)
        root.addView(TextView(this).apply {
            text = tr("Online překlad může odesílat rozpoznaný text externím poskytovatelům.")
            setTextColor(Color.parseColor("#92A4C7")); textSize = 12f
        })
        controls.addView(button("Zastavit překlad") { finish() })
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@GameLingoCameraActivity).apply { addView(root) },
                LinearLayout.LayoutParams(-1, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(-1, -2))
        })
    }

    private fun updateLanguages() {
        languageButton.text = GameLingoLanguages.name(cameraLanguages, source) +
            " → " + GameLingoLanguages.name(GameLingoLanguages.targetLanguages, target)
    }

    private fun chooseLanguages() {
        android.app.AlertDialog.Builder(this).setTitle(tr("Jazyky"))
            .setItems(arrayOf(tr("Jazyk textu"), tr("Cílový jazyk"))) { _, which ->
                val options = if (which == 0) cameraLanguages else GameLingoLanguages.targetLanguages
                android.app.AlertDialog.Builder(this)
                    .setTitle(tr(if (which == 0) "Jazyk textu" else "Cílový jazyk"))
                    .setItems(options.map { it.title }.toTypedArray()) { _, index ->
                        if (which == 0) source = options[index].code else target = options[index].code
                        getSharedPreferences("gamelingo_camera", MODE_PRIVATE).edit().putString("source", source).putString("target", target).apply()
                        updateLanguages()
                        resetTranslation()
                        translator?.zavri(); translator = null
                    }.show()
            }.show()
    }

    private fun updatePreviewHeight() {
        val width = previewContainer.width.takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels - px(56)).coerceAtLeast(1)
        val aspect = 16f / 9f
        val height = if (region.full) (width * aspect).toInt() else px(340)
        val params = previewContainer.layoutParams
        if (params.height != height) {
            params.height = height
            previewContainer.layoutParams = params
        }
    }

    private fun updateMode() {
        updatePreviewHeight()
        fullButton.background = GameLingoVisualKit.card(this, selected = region.full)
        areaButton.background = GameLingoVisualKit.card(this, selected = !region.full)
        fullButton.isSelected = region.full
        areaButton.isSelected = !region.full
        regionControls.visibility = if (region.full) View.GONE else View.VISIBLE
        overlayButton.visibility = if (region.full) View.VISIBLE else View.GONE
    }

    private fun updateRegion(value: Region) {
        if (region != value) {
            region = value
            resetTranslation()
            getSharedPreferences("gamelingo_camera", MODE_PRIVATE).edit()
                .putBoolean("full", value.full)
                .putFloat("centerX", value.centerX).putFloat("centerY", value.centerY)
                .putFloat("width", value.width).putFloat("height", value.height).apply()
        }
        preview.region = value
        preview.invalidate()
    }

    override fun onStart() {
        super.onStart()
        if (RemotePlayService.aktivni || PrijemService.bezi) { finish(); return }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
        } else if (!paused) startCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == CAMERA_PERMISSION) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                if (!paused && !isFinishing) startCamera()
            } else unavailable()
        }
    }

    private fun unavailable() {
        if (!isFinishing) {
            Toast.makeText(this, tr("Fotoaparát není dostupný."), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateTorchButton() {
        torchButton.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true && !torchPending
        torchButton.isSelected = torch
        torchButton.setImageDrawable(TorchIcon(px(24), torch, torchFailed,
            if (!torchButton.isEnabled && !torchPending) Color.GRAY else if (torchFailed) Color.YELLOW else if (torch) Color.CYAN else Color.WHITE))
    }

    private class TorchIcon(private val size: Int, private val on: Boolean,
                            private val failed: Boolean, color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun draw(canvas: Canvas) {
            canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            if (failed) {
                val warning = Path().apply {
                    moveTo(12f, 3f); lineTo(22f, 21f); lineTo(2f, 21f); close()
                }
                canvas.drawPath(warning, paint)
                canvas.drawLine(12f, 9f, 12f, 14f, paint)
                canvas.drawPoint(12f, 17.5f, paint)
            } else {
                val body = Path().apply {
                    moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 11f)
                    lineTo(15f, 14f); lineTo(15f, 22f); lineTo(9f, 22f)
                    lineTo(9f, 14f); lineTo(7f, 11f); close()
                }
                canvas.drawPath(body, paint)
                canvas.drawLine(7f, 11f, 17f, 11f, paint)
                if (on) {
                    canvas.drawLine(12f, 1f, 12f, 4f, paint)
                    canvas.drawLine(5f, 2f, 7f, 4f, paint)
                    canvas.drawLine(19f, 2f, 17f, 4f, paint)
                }
            }
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun startCamera() {
        if (running || paused || isFinishing ||
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        running = true
        val token = ++generation
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!running || generation != token || isFinishing) return@addListener
            runCatching {
                val p = future.get()
                provider = p
                val use = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis = use
                use.setAnalyzer(worker) { frame -> analyze(frame, token) }
                val live = Preview.Builder().setTargetResolution(Size(1280, 720)).build()
                live.setSurfaceProvider(livePreview.surfaceProvider)
                liveUseCase = live
                preview.showBitmap = true
                livePreview.visibility = View.VISIBLE
                previewStatus.visibility = if (preview.bitmap == null) View.VISIBLE else View.GONE
                camera = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, live, use)
                // Restore continuous AF (when supported) and automatic metering;
                // never lock the focus distance for live OCR.
                camera?.cameraControl?.cancelFocusAndMetering()
                if (!TvProfily(this).pouzeTelefon) enableTV()
                camera?.let { c ->
                    c.cameraInfo.torchState.observe(this) { value ->
                        if (camera === c) {
                            torch = value == TorchState.ON
                            updateTorchButton()
                        }
                    }
                }
                updateTorchButton()
            }.onFailure { unavailable() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(frame: ImageProxy, token: Int) {
        val now = SystemClock.elapsedRealtime()
        val base = if (region.full) 350L else 450L
        val sampleInterval = if (region.full) maxOf(base, analysisCost * 5 / 4, if (stableFrames >= 3) 550L else base).coerceAtMost(700L) else base
        if (!running || token != generation || now - lastSample < sampleInterval || !ocrBusy.compareAndSet(false, true)) {
            frame.close(); return
        }
        lastSample = now
        val current = configRevision
        val area = region.rect()
        try {
            val rotation = frame.imageInfo.rotationDegrees
            val raw = frame.toBitmap()
            val bitmap = if (rotation == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true)
            if (bitmap !== raw) raw.recycle()
            val left = (area.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val top = (area.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val right = (area.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = (area.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
            val cropped = if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) bitmap
                else Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            recognizer.process(InputImage.fromBitmap(cropped, 0)).addOnCompleteListener { task ->
                analysisCost = (analysisCost * 7 + (SystemClock.elapsedRealtime() - now) * 3) / 10
                if (cropped !== bitmap) cropped.recycle()
                if (!running || token != generation || current != configRevision || isFinishing) {
                    bitmap.recycle(); ocrBusy.set(false); return@addOnCompleteListener
                }
                try {
                val blocks = mutableListOf<CameraBlock>()
                var remaining = 1200
                if (task.isSuccessful) {
                    fun addBlock(original: String, box: android.graphics.Rect?, corners: Array<android.graphics.Point>?, lineCount: Int) {
                        if (box == null || original.isBlank() || remaining <= 0 || blocks.size >= 20) return
                        val text = original.take(remaining)
                        remaining -= text.length + 1
                        val rect = RectF(
                            (left + box.left).toFloat() / bitmap.width,
                            (top + box.top).toFloat() / bitmap.height,
                            (left + box.right).toFloat() / bitmap.width,
                            (top + box.bottom).toFloat() / bitmap.height)
                        val angle = if (corners != null && corners.size >= 2)
                            Math.toDegrees(kotlin.math.atan2((corners[1].y - corners[0].y).toDouble(),
                                (corners[1].x - corners[0].x).toDouble())).toFloat() else 0f
                        val background = if (region.full) runCatching { sampleBackground(bitmap, rect) }.getOrDefault(Color.BLACK) else Color.BLACK
                        val ink = if (region.full) runCatching { sampleInk(bitmap, rect, background) }.getOrNull() else null
                        val quad = if (region.full && corners != null && corners.size == 4) {
                            val points = FloatArray(8) { i -> if (i % 2 == 0)
                                (left + corners[i/2].x).toFloat()/bitmap.width
                                else (top + corners[i/2].y).toFloat()/bitmap.height }
                            CameraQuad(points).takeIf { points.all { p -> p in 0f..1f } &&
                                it.projection(rect, RectF(0f,0f,1f,1f)) != null }
                        } else null
                        val horizontal = kotlin.math.abs(angle) < 3.44f &&
                            (quad?.points?.let { kotlin.math.abs(it[3]-it[1]) < .003f &&
                                kotlin.math.abs(it[6]-it[0]) < .003f } ?: true)
                        val surface = if (region.full && horizontal)
                            runCatching { reconstructedSurface(bitmap, rect, background, ink) }.getOrNull() else null
                        val appearance = if (region.full)
                            runCatching { textureAndWeight(bitmap, rect, ink, lineCount, horizontal && surface == null) }
                                .getOrDefault(CameraAppearance(null,0)) else CameraAppearance(null,0)
                        if (region.full && quad == null && corners != null && corners.size >= 4) {
                            val width = kotlin.math.hypot((corners[1].x - corners[0].x).toDouble(),
                                (corners[1].y - corners[0].y).toDouble()).toFloat() / bitmap.width
                            val height = kotlin.math.hypot((corners[3].x - corners[0].x).toDouble(),
                                (corners[3].y - corners[0].y).toDouble()).toFloat() / bitmap.height
                            val cx = rect.centerX(); val cy = rect.centerY()
                            if (width > 0f && height > 0f) rect.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
                        }
                        blocks.add(CameraBlock(text, rect, angle, background, lineCount.coerceAtLeast(1), ink, surface, quad, appearance))
                    }
                    if (region.full) {
                        for (block in task.result.textBlocks.take(20)) {
                            // Older ML Kit installations report zero when confidence is unavailable.
                            val scores = block.lines.map { it.confidence }.filter { it > 0f }
                            if (scores.isNotEmpty() && scores.average() < .30) continue
                            addBlock(block.text.trim().replace(Regex("\\s+"), " "), block.boundingBox, block.cornerPoints, block.lines.size)
                        }
                    } else {
                        for (line in task.result.textBlocks.flatMap { it.lines }.take(20)) {
                            addBlock(line.text, line.boundingBox, line.cornerPoints, 1)
                        }
                    }
                }
                // Keep a smaller fallback frame; native PreviewView still displays full live video.
                val longest = maxOf(bitmap.width, bitmap.height)
                val fallback = if (region.full && longest > 720) Bitmap.createScaledBitmap(bitmap,
                    (bitmap.width * 720 / longest).coerceAtLeast(1),
                    (bitmap.height * 720 / longest).coerceAtLeast(1), true) else bitmap
                val previous = preview.bitmap
                preview.bitmap = fallback
                if (fallback !== bitmap) bitmap.recycle()
                if (previous == null || previous.width != fallback.width || previous.height != fallback.height) updatePreviewHeight()
                previewStatus.visibility = View.GONE
                preview.invalidate()
                val ordered = if (region.full) columnOrder(blocks) else blocks
                val signature = ordered.joinToString("\n") { it.text }
                stableFrames = if (signature.isNotBlank() && signature == previousOCR) (stableFrames + 1).coerceAtMost(3) else 0
                previousOCR = signature
                receive(ordered)
                } finally { ocrBusy.set(false) }
            }
        } catch (_: Exception) {
            ocrBusy.set(false)
        } finally {
            frame.close()
        }
    }

    private fun updateOverlays() {
        preview.overlays = if (region.full && overlayEnabled) currentBlocks.mapNotNull { block ->
            overlayCache[block.text]?.takeIf { it.isNotBlank() }?.let { CameraOverlay(it, block.rect, block.angle, block.background, block.lineCount, block.ink, block.surface, block.quad, block.appearance) }
        } else emptyList()
        preview.invalidate()
    }

    private fun rememberOverlay(original: String, translated: String) {
        if (translated.isBlank()) return
        if (overlayCache.size >= 256 && original !in overlayCache) {
            overlayCache.keys.firstOrNull()?.let { overlayCache.remove(it) }
        }
        overlayCache[original] = translated
    }

    private fun receive(blocks: List<CameraBlock>) {
        val text = blocks.joinToString("\n") { it.text }
        currentBlocks = blocks
        candidate = text
        if (region.full && overlayEnabled) {
            receiveFullImage(text, blocks)
            return
        }
        if (text.isBlank()) {
            updateOverlays()
            emptyFrames++
            if (emptyFrames >= 2 && (translatedText.isNotEmpty() || translating)) {
                resetTranslation(); translator?.zavri(); translator = null
            }
            return
        }
        emptyFrames = 0
        if (text == lastTranslated) {
            updateOverlays(); publishTV(); return
        }
        if (lastTranslated.isNotEmpty() && !matchesCurrent(lastTranslated)) {
            translatedText = ""; translatedLines = emptyList(); lastTranslated = ""
            clearTV()
        }
        updateOverlays()
        if (translatedText.isEmpty() && output.text.toString() != text) { output.text = text }
        if (translating || SystemClock.elapsedRealtime() < retryAt) return
        val current = configRevision
        val token = generation
        translating = true
        fun valid() = token == generation && current == configRevision && running && !isFinishing
        if (source == "auto") {
            languageIdentifier.identifyLanguage(text).addOnCompleteListener { result ->
                if (!valid()) return@addOnCompleteListener
                if (!matchesCurrent(text)) { translating = false; return@addOnCompleteListener }
                val detected = if (result.isSuccessful) result.result else null
                val from = detected?.takeIf { code -> GameLingoLanguages.targetLanguages.any { it.code == code } }
                    ?: GameLingoLanguages.game(this)
                translateRecognized(text, blocks, from, token, current)
            }
        } else translateRecognized(text, blocks, source, token, current)
    }

    private fun translateRecognized(text: String, blocks: List<CameraBlock>, from: String, token: Int, current: Int) {
        val to = target
        if (overlaySource != from) {
            overlayCache.clear(); overlaySource = from
            updateOverlays()
        }
        if (translatorSource != from) {
            translator?.zavri(); translator = null
            translatorSource = from
        }
        val engine = translator ?: Prekladac(this, to, from).also { translator = it }
        fun valid() = token == generation && current == configRevision && running && !isFinishing
        engine.preloz(text, { result ->
            if (!valid()) return@preloz
            translating = false
            if (!matchesCurrent(text) || result.isBlank()) return@preloz
            lastTranslated = text
            translatedText = result
            if (output.text.toString() != result) output.text = result
            publishTV(force = true)
        }, {
            if (valid()) {
                translating = false; retryAt = SystemClock.elapsedRealtime() + 2000L
                output.text = tr("Překlad selhal.")
            }
        })
    }

    private fun receiveFullImage(text: String, blocks: List<CameraBlock>) {
        val visible = blocks.map { it.text }.toSet()
        eligibleVisible = if (previousVisible.isEmpty()) visible else visible.intersect(previousVisible)
        val sceneChanged = previousVisible.isNotEmpty() && visible.intersect(previousVisible).isEmpty()
        previousVisible = visible
        if (sceneChanged) {
            // Invalidate callbacks, including any old provider request.
            translationRequest++
            translator?.zavri(); translator = null
            translating = false; retryAt = 0L; eligibleVisible = visible
        }
        if (text.isBlank()) {
            publishVisibleBlocks()
            return
        }
        publishVisibleBlocks()
        if (translating || SystemClock.elapsedRealtime() < retryAt ||
            blocks.none { it.text in eligibleVisible && it.text !in overlayCache }) return
        val token = generation
        val current = configRevision
        val request = ++translationRequest
        translating = true
        fun valid() = token == generation && current == configRevision && request == translationRequest && running && !isFinishing
        fun translateBlocks(from: String) {
            if (!valid()) return
            if (overlaySource != from) {
                overlayCache.clear(); overlaySource = from
                publishVisibleBlocks()
            }
            if (translatorSource != from) {
                translator?.zavri(); translator = null; translatorSource = from
            }
            val engine = translator ?: Prekladac(this, target, from).also { translator = it }
            fun next() {
                if (!valid()) return
                val original = currentBlocks.firstOrNull { it.text in eligibleVisible && it.text !in overlayCache }?.text
                if (original == null) { translating = false; return }
                engine.preloz(original, { result ->
                    if (!valid()) return@preloz
                    if (result.isBlank()) {
                        translating = false; retryAt = SystemClock.elapsedRealtime() + 2000L
                        return@preloz
                    }
                    if (original in previousVisible) {
                        rememberOverlay(original, result)
                        publishVisibleBlocks()
                    }
                    // Avoid recursively invoking synchronous cache hits.
                    output.post { next() }
                }, {
                    if (valid()) { translating = false; retryAt = SystemClock.elapsedRealtime() + 2000L }
                })
            }
            next()
        }
        if (source == "auto") {
            languageIdentifier.identifyLanguage(text).addOnCompleteListener { result ->
                if (!valid()) return@addOnCompleteListener
                val detected = if (result.isSuccessful) result.result else null
                val from = detected?.takeIf { code -> GameLingoLanguages.targetLanguages.any { it.code == code } }
                    ?: overlaySource ?: GameLingoLanguages.game(this)
                translateBlocks(from)
            }
        } else translateBlocks(source)
    }

    private fun publishVisibleBlocks() {
        updateOverlays()
        val result = currentBlocks.mapNotNull { overlayCache[it.text] }.joinToString("\n")
        val changed = translatedText != result
        translatedText = result
        val display = result.ifBlank { candidate }
        if (output.text.toString() != display) output.text = display
        if (result.isBlank()) { if (changed) clearTV() }
        else publishTV(force = changed)
    }

    private fun matchesCurrent(text: String): Boolean {
        // Do not accept a translation for changed words or numbers.
        fun normalized(value: String) = value.trim().replace(Regex("\\s+"), " ")
        return normalized(candidate) == normalized(text)
    }

    private fun resetTranslation(keepText: Boolean = false) {
        configRevision++
        translationRequest++
        previousVisible = emptySet(); eligibleVisible = emptySet()
        stableFrames = 0; previousOCR = ""
        overlayCache.clear(); overlaySource = null
        candidate = ""; emptyFrames = 0; lastTranslated = ""; translating = false; retryAt = 0L
        if (!keepText) {
            translatedText = ""; translatedLines = emptyList(); currentBlocks = emptyList()
            output.text = ""; updateOverlays()
        }
        clearTV()
    }

    private fun selectedTV(): TvProfil? {
        val store = TvProfily(this)
        if (store.pouzeTelefon) return null
        val tv = store.posledniId?.let { store.najdi(it) } ?: return null
        return tv.takeIf { GameLingoTVPairing.paired(this, it.ip) }
    }

    private fun enableTV() {
        if (!running) return
        val tv = selectedTV() ?: run {
            tvButton?.text = tr("Vyber nebo spáruj televizi")
            return
        }
        tvTarget = tv; tvEnabled = true; tvGeneration++; lastTVSent = 0
        tvButton?.text = "Google TV / Android TV · " + tr("Připojuji…")
        publishTV(force = true)
    }

    private fun disableTV() {
        clearTV()
        tvGeneration++; tvEnabled = false; tvProbing = false; tvTarget = null
        tvButton?.text = if (TvProfily(this).pouzeTelefon) tr("Telefon") else tr("Spárovat")
    }

    private fun clearTV() {
        val tv = tvTarget ?: return
        if (!RemotePlayService.aktivni && Propojeni.Vysilac.cilIp == tv.ip) {
            Propojeni.Vysilac.cameraCaption("", 0)
        }
        lastTVSent = 0L
    }

    private fun publishTV(force: Boolean = false) {
        val tv = tvTarget ?: return
        if (!tvEnabled || !running || RemotePlayService.aktivni || PrijemService.bezi) return
        if (selectedTV()?.ip != tv.ip) { disableTV(); return }
        val now = SystemClock.elapsedRealtime()
        if (!GameLingoTVPairing.connected(tv.ip)) {
            if (tvProbing) return
            tvProbing = true
            val token = tvGeneration
            GameLingoTVPairing.verify(this, tv) { ok ->
                if (token != tvGeneration || !tvEnabled || !running) return@verify
                tvProbing = false
                if (ok) publishTV(force = true)
                else {
                    disableTV()
                    Toast.makeText(this, tr("TV neodpovídá. Zkontroluj domácí síť a přijímač."), Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        tvButton?.text = tr("TV připojena")
        if (translatedText.isBlank() || (!force && now - lastTVSent < 4000L)) return
        Propojeni.Vysilac.nastavCil(tv.ip, tv.jmeno)
        Propojeni.Vysilac.cameraCaption(translatedText, 6000)
        lastTVSent = now
    }

    private fun releaseCamera(keepFrame: Boolean = false) {
        running = false; generation++
        resetTranslation(keepText = keepFrame)
        disableTV()
        camera?.cameraInfo?.torchState?.removeObservers(this)
        analysis?.clearAnalyzer()
        analysis?.let { provider?.unbind(it) }
        liveUseCase?.let { provider?.unbind(it) }
        liveUseCase = null
        livePreview.visibility = View.INVISIBLE
        preview.showBitmap = keepFrame
        analysis = null; camera = null; torch = false
        torchPending = false; torchFailed = false
        updateTorchButton()
        translator?.zavri(); translator = null
        if (!keepFrame) preview.bitmap = null
        previewStatus.visibility = if (preview.bitmap == null) View.VISIBLE else View.GONE
        preview.invalidate()
    }

    override fun onStop() { releaseCamera(); super.onStop() }
    override fun onDestroy() { recognizer.close(); languageIdentifier.close(); worker.shutdown(); super.onDestroy() }

    /** The editor uses the same normalized upright frame as OCR. */
    private class CameraFrame(context: android.content.Context) : View(context) {
        var bitmap: Bitmap? = null
        var showBitmap = false
        var region = Region()
        var overlays = emptyList<CameraOverlay>()
        var onRegionCommitted: (Region) -> Unit = {}
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var dragOrigin: RectF? = null
        private var draft: RectF? = null
        private var startX = 0f
        private var startY = 0f
        private var corner = -1
        private val density get() = resources.displayMetrics.density

        private fun imageBounds(): RectF? {
            val image = bitmap ?: return null
            if (width <= 0 || height <= 0) return null
            val scale = min(width.toFloat() / image.width, height.toFloat() / image.height)
            val w = image.width * scale; val h = image.height * scale
            val x = (width - w) / 2; val y = (height - h) / 2
            return RectF(x, y, x + w, y + h)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (region.full) return false
            val bounds = imageBounds() ?: return false
            val nx = (event.x - bounds.left) / bounds.width()
            val ny = (event.y - bounds.top) / bounds.height()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val r = region.rect()
                    val corners = listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)
                    val near = corners.map { (x, y) ->
                        val dx = (nx - x) * bounds.width()
                        val dy = (ny - y) * bounds.height()
                        dx * dx + dy * dy
                    }
                    corner = near.indices.minByOrNull { near[it] }?.takeIf { near[it] <= (22f * density) * (22f * density) } ?: -1
                    if (corner < 0 && !r.contains(nx, ny)) return false
                    dragOrigin = r
                    startX = nx; startY = ny
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val original = dragOrigin ?: return false
                    val dx = nx - startX; val dy = ny - startY
                    val r = if (corner >= 0) {
                        val left = corner == 0 || corner == 2
                        val top = corner == 0 || corner == 1
                        RectF(
                            if (left) (original.left + dx).coerceIn(0f, (original.right - .15f).coerceAtLeast(0f)) else original.left,
                            if (top) (original.top + dy).coerceIn(0f, (original.bottom - .08f).coerceAtLeast(0f)) else original.top,
                            if (left) original.right else (original.right + dx).coerceIn((original.left + .15f).coerceAtMost(1f), 1f),
                            if (top) original.bottom else (original.bottom + dy).coerceIn((original.top + .08f).coerceAtMost(1f), 1f))
                    } else {
                        val x = (original.left + dx).coerceIn(0f, (1f - original.width()).coerceAtLeast(0f))
                        val y = (original.top + dy).coerceIn(0f, (1f - original.height()).coerceAtLeast(0f))
                        RectF(x, y, x + original.width(), y + original.height())
                    }
                    draft = r
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        dragOrigin = null; draft = null
                        parent.requestDisallowInterceptTouchEvent(false)
                        onRegionCommitted(Region(false, r.centerY(), r.height(), r.centerX(), r.width()))
                        performClick()
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragOrigin = null; draft = null
                    parent.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
            return dragOrigin != null
        }

        override fun performClick(): Boolean { super.performClick(); return true }

        override fun onDraw(canvas: Canvas) {
            val image = bitmap ?: return
            val bounds = imageBounds() ?: return
            val w = bounds.width(); val h = bounds.height()
            val x = bounds.left; val y = bounds.top
            if (showBitmap) canvas.drawBitmap(image, null, bounds, paint)
            if (region.full) {
                for (overlay in overlays) {
                    val r = overlay.rect
                    val box = RectF(x + r.left * w, y + r.top * h, x + r.right * w, y + r.bottom * h)
                    if (box.width() <= 0 || box.height() <= 0) continue
                    canvas.save()
                    val projection = overlay.quad?.projection(box, bounds)
                    if (projection != null) canvas.concat(projection)
                    else canvas.rotate(overlay.angle, box.centerX(), box.centerY())
                    canvas.clipRect(box)
                    paint.color = overlay.background
                    overlay.surface?.let { surface ->
                        paint.shader = android.graphics.LinearGradient(
                            box.left, box.top,
                            if (surface.horizontal) box.right else box.left,
                            if (surface.horizontal) box.top else box.bottom,
                            surface.start, surface.end, android.graphics.Shader.TileMode.CLAMP)
                    }
                    val texture = overlay.appearance.texture
                    if (texture != null) {
                        paint.shader = null
                        canvas.drawBitmap(texture, null, box, paint)
                    } else canvas.drawRect(box, paint)
                    paint.shader = null
                    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = overlay.ink ?: (if (Color.luminance(overlay.background) > .179f) Color.BLACK else Color.WHITE)
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                            if (overlay.appearance.weight >= 1) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                        textSize = (box.height() / overlay.lineCount * .83f).coerceAtLeast(1f)
                    }
                    val layoutWidth = box.width().toInt().coerceAtLeast(1)
                    fun layout() = StaticLayout.Builder.obtain(overlay.text, 0, overlay.text.length, textPaint, layoutWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
                    var textLayout = layout()
                    repeat(12) {
                        if (textLayout.height > box.height() && textPaint.textSize > 1f) {
                            textPaint.textSize = (textPaint.textSize * .8f).coerceAtLeast(1f)
                            textLayout = layout()
                        }
                    }
                    canvas.translate(box.left, box.top + ((box.height() - textLayout.height) / 2).coerceAtLeast(0f))
                    textLayout.draw(canvas)
                    canvas.restore()
                }
            }
            if (!region.full) {
                val r = draft ?: region.rect()
                val box = RectF(x + r.left * w, y + r.top * h, x + r.right * w, y + r.bottom * h)
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(30, 255, 255, 0)
                canvas.drawRect(box, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = Color.YELLOW
                canvas.drawRect(box, paint)
                paint.style = Paint.Style.FILL
                for (cx in listOf(box.left, box.right)) {
                    for (cy in listOf(box.top, box.bottom)) {
                        canvas.drawCircle(cx, cy, 6f * density, paint)
                    }
                }
            }
        }
    }
}
