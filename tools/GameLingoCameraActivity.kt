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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
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

    private data class CameraBlock(val text: String, val rect: RectF)
    private data class CameraOverlay(val text: String, val rect: RectF)

    private data class Region(val full: Boolean = true, val centerY: Float = .78f,
                              val height: Float = .24f, val centerX: Float = .5f,
                              val width: Float = .9f) {
        fun rect(): RectF {
            if (full) return RectF(0f, 0f, 1f, 1f)
            val h = height.coerceIn(.08f, .8f)
            val y = (centerY - h / 2).coerceIn(0f, 1f - h)
            val w = width.coerceIn(.15f, .95f)
            val x = (centerX - w / 2).coerceIn(0f, 1f - w)
            return RectF(x, y, x + w, y + h)
        }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val ocrBusy = AtomicBoolean(false)
    @Volatile private var region = Region()
    @Volatile private var generation = 0
    @Volatile private var configRevision = 0
    @Volatile private var running = false
    private var lastSample = 0L // analysis worker only
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var liveUseCase: Preview? = null
    private lateinit var livePreview: PreviewView
    private var camera: Camera? = null
    private var translator: Prekladac? = null
    private var source = "en"
    private var target = "cs"
    private var paused = false
    private var torch = false
    private var zoom = 1f
    private var candidate = ""
    private var emptyFrames = 0
    private var lastTranslated = ""
    private var translatedText = ""
    private var translatedLines = emptyList<String>()
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
    private lateinit var pauseButton: Button
    private lateinit var torchButton: Button
    private lateinit var modeButton: Button
    private lateinit var overlayButton: Button
    private var tvButton: TextView? = null
    private lateinit var regionControls: LinearLayout
    private lateinit var languageButton: Button
    private fun tr(text: String) = GameLingoLanguages.ui(this, text)
    private fun px(value: Int) = GameLingoVisualKit.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = GameLingoLanguages.game(this)
        target = GameLingoLanguages.target(this)
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
        root.addView(title)
        languageButton = button("Jazyky") { chooseLanguages() }
        updateLanguages()
        root.addView(languageButton)
        tvButton = TextView(this).apply {
            text = if (TvProfily(this@GameLingoCameraActivity).pouzeTelefon) tr("Telefon") else "Google TV / Android TV"
            gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }
        root.addView(tvButton)
        modeButton = button("Celý obraz") {
            updateRegion(region.copy(full = !region.full))
            modeButton.text = tr(if (region.full) "Celý obraz" else "Vybraná oblast")
            regionControls.visibility = if (region.full) View.GONE else View.VISIBLE
            overlayButton.visibility = if (region.full) View.VISIBLE else View.GONE
        }
        root.addView(modeButton)
        overlayButton = button("Překlad přes původní text") {
            overlayEnabled = !overlayEnabled
            overlayButton.text = (if (overlayEnabled) "✓  " else "") + tr("Překlad přes původní text")
            resetTranslation()
        }
        overlayButton.text = "✓  " + tr("Překlad přes původní text")
        root.addView(overlayButton)
        livePreview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        preview = CameraFrame(this)
        root.addView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(livePreview, FrameLayout.LayoutParams(-1, -1))
            addView(preview, FrameLayout.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(300)))
        regionControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(row("Vodorovná pozice oblasti", 100, 50) { updateRegion(region.copy(centerX = it / 100f)) })
            addView(row("Pozice oblasti", 100, 78) { updateRegion(region.copy(centerY = it / 100f)) })
            addView(row("Šířka oblasti", 80, 75) { updateRegion(region.copy(width = .15f + it / 100f)) })
            addView(row("Velikost oblasti", 72, 16) { updateRegion(region.copy(height = .08f + it / 100f)) })
        }
        root.addView(regionControls)
        output = TextView(this).apply {
            gravity = Gravity.CENTER; minHeight = px(52); setPadding(px(8), px(8), px(8), px(8))
            textSize = 20f * GameLingoPhoneSubtitleStyle.scale(this@GameLingoCameraActivity)
            GameLingoPhoneSubtitleStyle.apply(this@GameLingoCameraActivity, this)
            setTextIsSelectable(true)
        }
        root.addView(output)
        root.addView(row("Přiblížení", 30, 0) {
            zoom = 1f + it / 10f
            camera?.let { c -> c.cameraControl.setZoomRatio(zoom.coerceAtMost(c.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)) }
        })
        val actions = LinearLayout(this)
        torchButton = button("Svítilna") {
            val c = camera ?: return@button
            if (!c.cameraInfo.hasFlashUnit()) return@button
            val desired = !torch
            val future = c.cameraControl.enableTorch(desired)
            future.addListener({
                if (camera === c && runCatching { future.get(); true }.getOrDefault(false)) {
                    torch = desired
                    torchButton.isSelected = torch
                }
            }, ContextCompat.getMainExecutor(this))
        }
        pauseButton = button("Pozastavit") {
            paused = !paused
            pauseButton.text = tr(if (paused) "Pokračovat" else "Pozastavit")
            if (paused) releaseCamera(keepFrame = true) else startCamera()
        }
        actions.addView(torchButton, LinearLayout.LayoutParams(0, px(48), 1f))
        actions.addView(pauseButton, LinearLayout.LayoutParams(0, px(48), 1f))
        root.addView(actions)
        root.addView(TextView(this).apply {
            text = tr("Online překlad může odesílat rozpoznaný text externím poskytovatelům.")
            setTextColor(Color.parseColor("#92A4C7")); textSize = 12f
        })
        root.addView(button("Zastavit překlad") { finish() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun updateLanguages() {
        languageButton.text = GameLingoLanguages.name(GameLingoLanguages.gameLanguages, source) +
            " → " + GameLingoLanguages.name(GameLingoLanguages.targetLanguages, target)
    }

    private fun chooseLanguages() {
        android.app.AlertDialog.Builder(this).setTitle(tr("Jazyky"))
            .setItems(arrayOf(tr("Jazyk textu"), tr("Cílový jazyk"))) { _, which ->
                val options = if (which == 0) GameLingoLanguages.gameLanguages else GameLingoLanguages.targetLanguages
                android.app.AlertDialog.Builder(this)
                    .setTitle(tr(if (which == 0) "Jazyk textu" else "Cílový jazyk"))
                    .setItems(options.map { it.title }.toTypedArray()) { _, index ->
                        if (which == 0) source = options[index].code else target = options[index].code
                        updateLanguages()
                        resetTranslation()
                        translator?.zavri(); translator = null
                    }.show()
            }.show()
    }

    private fun updateRegion(value: Region) {
        region = value
        resetTranslation()
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
                preview.showBitmap = false
                livePreview.visibility = View.VISIBLE
                camera = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, live, use)
                if (!TvProfily(this).pouzeTelefon) enableTV()
                torchButton.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
            }.onFailure { unavailable() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(frame: ImageProxy, token: Int) {
        val now = SystemClock.elapsedRealtime()
        if (!running || token != generation || now - lastSample < 450L || !ocrBusy.compareAndSet(false, true)) {
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
                ocrBusy.set(false)
                if (cropped !== bitmap) cropped.recycle()
                if (!running || token != generation || current != configRevision || isFinishing) {
                    bitmap.recycle(); return@addOnCompleteListener
                }
                preview.bitmap = bitmap
                preview.invalidate()
                val blocks = mutableListOf<CameraBlock>()
                var remaining = 1200
                if (task.isSuccessful) {
                    for (line in task.result.textBlocks.flatMap { it.lines }.take(20)) {
                        val box = line.boundingBox ?: continue
                        if (line.text.isBlank() || remaining <= 0) continue
                        val text = line.text.take(remaining)
                        remaining -= text.length + 1
                        blocks.add(CameraBlock(text, RectF(
                            (left + box.left).toFloat() / bitmap.width,
                            (top + box.top).toFloat() / bitmap.height,
                            (left + box.right).toFloat() / bitmap.width,
                            (top + box.bottom).toFloat() / bitmap.height)))
                    }
                }
                receive(blocks)
            }
        } catch (_: Exception) {
            ocrBusy.set(false)
        } finally {
            frame.close()
        }
    }

    private fun updateOverlays() {
        val overlay = region.full && overlayEnabled && candidate == lastTranslated && translatedLines.size == currentBlocks.size &&
            translatedLines.isNotEmpty()
        preview.overlays = if (overlay) currentBlocks.zip(translatedLines).map { (block, text) ->
            CameraOverlay(text, block.rect)
        } else emptyList()
        output.visibility = if (overlay) View.GONE else View.VISIBLE
        preview.invalidate()
    }

    private fun receive(blocks: List<CameraBlock>) {
        val text = blocks.joinToString("\n") { it.text }
        currentBlocks = blocks
        candidate = text
        if (text.isBlank()) {
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
        if (translatedText.isEmpty()) output.text = text
        if (translating || SystemClock.elapsedRealtime() < retryAt) return
        val current = configRevision
        val token = generation
        val from = source
        val to = target
        val engine = translator ?: Prekladac(this, to, from).also { translator = it }
        translating = true
        fun valid() = token == generation && current == configRevision && running && !isFinishing
        fun finishResult(result: String, lines: List<String>, complete: Boolean = true) {
            if (!valid()) return
            if (complete) translating = false
            if (!matchesCurrent(text) || result.isBlank()) return
            lastTranslated = text
            translatedText = result
            translatedLines = lines
            output.text = result
            updateOverlays()
            publishTV(force = true)
        }
        engine.preloz(text, { translated ->
            if (!valid()) return@preloz
            if (!matchesCurrent(text) || translated.isBlank()) {
                translating = false
                return@preloz
            }
            val result = GameLingoGlossaryStore.apply(this, text, translated, from, to)
            val lines = result.split("\n")
            if (!region.full || !overlayEnabled || lines.size == blocks.size) {
                finishResult(result, lines)
            } else {
                // Only place a translation on a source line when alignment is known.
                finishResult(result, emptyList(), complete = false)
                val mapped = mutableListOf<String>()
                fun next(index: Int) {
                    if (!valid()) return
                    if (!matchesCurrent(text)) { translating = false; return }
                    if (index == blocks.size) { finishResult(result, mapped.toList()); return }
                    val original = blocks[index].text
                    engine.preloz(original, { item ->
                        if (!valid()) return@preloz
                        if (item.isBlank()) finishResult(result, emptyList())
                        else {
                            mapped.add(GameLingoGlossaryStore.apply(this, original, item, from, to))
                            next(index + 1)
                        }
                    }, { finishResult(result, emptyList()) })
                }
                next(0)
            }
        }, {
            if (token == generation && current == configRevision) {
                translating = false; retryAt = SystemClock.elapsedRealtime() + 2000L
                output.text = tr("Překlad selhal.")
            }
        })
    }

    private fun matchesCurrent(text: String): Boolean {
        if (candidate == text) return true
        fun words(value: String) = value.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
        val old = words(text)
        val now = words(candidate)
        return old.isNotEmpty() && old.intersect(now).size * 2 >= maxOf(old.size, now.size)
    }

    private fun resetTranslation(keepText: Boolean = false) {
        configRevision++
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
        analysis?.clearAnalyzer()
        analysis?.let { provider?.unbind(it) }
        liveUseCase?.let { provider?.unbind(it) }
        liveUseCase = null
        livePreview.visibility = View.INVISIBLE
        preview.showBitmap = keepFrame
        analysis = null; camera = null; torch = false
        torchButton.isEnabled = false; torchButton.isSelected = false
        translator?.zavri(); translator = null
        if (!keepFrame) preview.bitmap = null
        preview.invalidate()
    }

    override fun onStop() { releaseCamera(); super.onStop() }
    override fun onDestroy() { recognizer.close(); worker.shutdown(); super.onDestroy() }

    /** The OCR and drawn rectangle share precisely the same upright bitmap. */
    private class CameraFrame(context: android.content.Context) : View(context) {
        var bitmap: Bitmap? = null
        var showBitmap = false
        var region = Region()
        var overlays = emptyList<CameraOverlay>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(canvas: Canvas) {
            val image = bitmap ?: return
            val scale = min(width.toFloat() / image.width, height.toFloat() / image.height)
            val w = image.width * scale; val h = image.height * scale
            val x = (width - w) / 2; val y = (height - h) / 2
            if (showBitmap) canvas.drawBitmap(image, null, RectF(x, y, x + w, y + h), paint)
            if (region.full) {
                for (overlay in overlays) {
                    val r = overlay.rect
                    val box = RectF(x + r.left * w, y + r.top * h, x + r.right * w, y + r.bottom * h)
                    if (box.width() <= 0 || box.height() <= 0) continue
                    paint.color = GameLingoPhoneSubtitleStyle.backgroundColor(
                        GameLingoPhoneSubtitleStyle.background(context))
                    if (paint.color == Color.TRANSPARENT) paint.color = Color.argb(224, 0, 0, 0)
                    canvas.drawRect(box, paint)
                    paint.color = GameLingoPhoneSubtitleStyle.textColor(GameLingoPhoneSubtitleStyle.color(context))
                    paint.textSize = (box.height() * .8f * GameLingoPhoneSubtitleStyle.scale(context)).coerceAtLeast(8f)
                    val measured = paint.measureText(overlay.text)
                    if (measured > box.width()) paint.textSize *= box.width() / measured
                    canvas.save()
                    canvas.clipRect(box)
                    canvas.drawText(overlay.text, box.left, box.centerY() - (paint.ascent() + paint.descent()) / 2, paint)
                    canvas.restore()
                }
            }
            if (!region.full) {
                val r = region.rect()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * resources.displayMetrics.density
                paint.color = Color.YELLOW
                canvas.drawRect(x + r.left * w, y + r.top * h, x + r.right * w, y + r.bottom * h, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }
}
