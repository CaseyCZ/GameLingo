package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Local direct-mode settings only. The authenticated phone receiver uses its
 * own packet geometry, never these values. */
object GameLingoDirectTVLayout {
    private fun prefs(c: Context) = c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)
    private val orangeDefault = floatArrayOf(0.07f, 0.43f, 0.50f, 0.60f, 1f)
    private val greenDefault = floatArrayOf(0.5f, 0.93f, 0.62f, 1f)
    private fun validRect(r: FloatArray) = r.size >= 4 &&
        r.take(4).all { it.isFinite() && it in 0f..1f } &&
        r[2] > r[0]+0.1f && r[3] > r[1]+0.05f
    private fun read(c: Context, prefix: String, default: FloatArray): FloatArray {
        val p = prefs(c)
        return FloatArray(default.size) { p.getFloat(prefix+it, default[it]) }
    }
    fun orange(c: Context): FloatArray {
        val r = read(c, "directOrange", orangeDefault)
        return r.takeIf { validRect(it) && it[4].isFinite() && it[4] in 0.6f..2f } ?: orangeDefault.copyOf()
    }
    fun green(c: Context): FloatArray? {
        if (!prefs(c).getBoolean("directGreenEnabled", false)) return null
        val r = read(c, "directGreen", greenDefault)
        return r.takeIf { it.all { v -> v.isFinite() } && it[0] in 0f..1f &&
            it[1] in 0f..1f && it[2] in 0.2f..1f && it[3] in 0.6f..2f } ?: greenDefault.copyOf()
    }
    fun blue(c: Context): FloatArray {
        OblastTitulku.nacti(c)
        return OblastTitulku.oblast.copyOf().takeIf(::validRect) ?: OblastTitulku.VYCHOZI.copyOf()
    }
    fun yellow(c: Context): FloatArray {
        Volby.nacti(c)
        return Volby.oblast.copyOf().takeIf(::validRect) ?: Volby.VYCHOZI.copyOf()
    }
    internal fun intersects(a: FloatArray, b: FloatArray) =
        a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]
    internal fun greenBounds(r: FloatArray): FloatArray {
        val left = (r[0] - r[2]/2f).coerceIn(0f, 1f-r[2])
        return floatArrayOf(left,(r[1]-.24f*r[3]).coerceAtLeast(0f),left+r[2],r[1])
    }
    internal fun locked(c: Context, region: String): Boolean = prefs(c).getBoolean("directLock"+region, false)
    private fun busy() = RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running

    fun show(a: Activity, onSaved: () -> Unit) {
        if (busy()) {
            AlertDialog.Builder(a).setMessage("Nejdříve zastav překlad i příjem z telefonu.")
                .setPositiveButton("OK", null).show()
            return
        }
        OblastTitulku.nacti(a)
        Volby.nacti(a)
        val blue = blue(a)
        val yellow = yellow(a)
        val orange = orange(a)
        val savedGreen = green(a)
        val green = savedGreen ?: greenDefault.copyOf()
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 16)
        }
        // A live schematic of the actual normalized geometry, updated by every D-pad slider.
        val preview = object : android.view.View(a) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val inset = 8f
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                a.getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
                val ratio = metrics.widthPixels.coerceAtLeast(1).toFloat() / metrics.heightPixels.coerceAtLeast(1)
                val w = minOf((width - inset * 2).coerceAtLeast(1f), (height - inset * 2).coerceAtLeast(1f) * ratio)
                val h = w / ratio
                canvas.save()
                canvas.translate((width-w-2*inset)/2f, (height-h-2*inset)/2f)
                paint.color = Color.parseColor("#111A33")
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawRect(inset,inset,inset+w,inset+h,paint)
                fun area(r: FloatArray, color: Int, label: String) {
                    val left = inset+r[0]*w; val top = inset+r[1]*h
                    val right = inset+r[2]*w; val bottom = inset+r[3]*h
                    paint.color = color; paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    canvas.drawRect(left,top,right,bottom,paint)
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.textSize = 15f * resources.displayMetrics.scaledDensity
                    canvas.save()
                    canvas.clipRect(left,top,right,bottom)
                    canvas.drawText(label,left+5f,minOf(bottom-3f,top+paint.textSize+3f),paint)
                    canvas.restore()
                }
                area(blue,Color.parseColor("#4FB3FF"),"OCR dialogů")
                area(yellow,Color.parseColor("#FFD54A"),"OCR herních textů")
                area(GameLingoDirectTVLayout.greenBounds(green),
                    Color.parseColor("#3CFF6E"),"Přeložené dialogy")
                area(orange,Color.parseColor("#FF9E34"),"Přeložené herní texty")
                canvas.restore()
            }
        }
        root.addView(TextView(a).apply { text = "Náhled rozložení · 4 nezávislé oblasti"; textSize = 16f })
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            GameLingoTVUi.dp(a, 170)))
        val columns = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        columns.addView(left, LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
            .apply { rightMargin = GameLingoTVUi.dp(a,18) })
        columns.addView(right, LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        root.addView(ScrollView(a).apply { addView(columns) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (a.resources.displayMetrics.heightPixels * .46f).toInt()))
        var controls = left
        var sectionLocked: Switch? = null
        fun heading(label: String, color: Int) {
            controls.addView(TextView(a).apply {
                text = label; textSize = 16f; setTextColor(color)
                setPadding(0, 16, 0, 6)
            })
        }
        fun toggle(label: String, enabled: Boolean): Switch = Switch(a).apply {
            text = label; isChecked = enabled; isFocusable = true
            GameLingoTVUi.controlFocus(a, this)
            controls.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        fun slider(label: String, min: Int, max: Int, current: () -> Float, update: (Float) -> Unit,
                   refreshAll: MutableList<() -> Unit>) {
            val title = TextView(a)
            val lock = sectionLocked
            val bar = SeekBar(a).apply {
                this.max = max-min; keyProgressIncrement = 5; isFocusable = true
                GameLingoTVUi.controlFocus(a, this)
            }
            fun refresh() {
                val percent = kotlin.math.round(current()*100).toInt()
                title.text = "$label: $percent %"
                bar.progress = (percent-min).coerceIn(0, max-min)
            }
            refreshAll.add { refresh() }
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        if (lock?.isChecked == true) { refresh(); return }
                        update((progress+min)/100f)
                        refreshAll.forEach { it() }
                        preview.invalidate()
                    }
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            })
            controls.addView(title)
            controls.addView(bar)
            refresh()
        }
        fun reset(label: String, lock: Switch, run: () -> Unit) {
            controls.addView(GameLingoTVUi.action(a,label) {
                if (!lock.isChecked) { run(); preview.invalidate() }
                else android.widget.Toast.makeText(a,"Nejdřív odemkni oblast.",android.widget.Toast.LENGTH_SHORT).show()
            })
        }
        fun rect(r: FloatArray, defaults: FloatArray) {
            val refresh = mutableListOf<() -> Unit>()
            slider("Poloha X", 0, 89, { r[0] }, { v ->
                val width = r[2]-r[0]
                r[0] = v.coerceIn(0f, (1f-width).coerceAtLeast(0f)); r[2] = (r[0]+width).coerceAtMost(1f)
            }, refresh)
            slider("Poloha Y", 0, 94, { r[1] }, { v ->
                val height = r[3]-r[1]
                r[1] = v.coerceIn(0f, (1f-height).coerceAtLeast(0f)); r[3] = (r[1]+height).coerceAtMost(1f)
            }, refresh)
            slider("Šířka", 11, 100, { r[2]-r[0] }, { v ->
                r[0] = r[0].coerceAtMost(1f-v); r[2] = (r[0]+v).coerceAtMost(1f)
            }, refresh)
            slider("Výška", 6, 100, { r[3]-r[1] }, { v ->
                r[1] = r[1].coerceAtMost(1f-v); r[3] = (r[1]+v).coerceAtMost(1f)
            }, refresh)
            if (r.size == 5) slider("Velikost písma", 60, 200, { r[4] }, { r[4] = it }, refresh)
            sectionLocked?.let { lock -> reset("Obnovit oblast", lock) { defaults.copyInto(r); refresh.forEach { it() } } }
        }
        heading("Modrá · čtení dialogů", Color.parseColor("#4FB3FF"))
        val manual = toggle("Ruční oblast", OblastTitulku.rucne)
        val blueLock = toggle("Zamknout modrou oblast", OblastTitulku.zamceno)
        sectionLocked = blueLock
        rect(blue, OblastTitulku.VYCHOZI)
        heading("Žlutá · čtení herních textů", Color.parseColor("#FFD54A"))
        val enabled = toggle("Číst oblast herních textů", Volby.zapnuto)
        val asText = toggle("Celý text místo seznamu voleb", Volby.rezimText)
        val yellowLock = toggle("Zamknout žlutou oblast", Volby.zamceno)
        sectionLocked = yellowLock
        rect(yellow, Volby.VYCHOZI)
        controls = right
        heading("Zelená · výstup dialogů", Color.parseColor("#3CFF6E"))
        val customGreen = toggle("Vlastní poloha místo automatické", savedGreen != null)
        val greenLock = toggle("Zamknout zelenou oblast", locked(a,"Green"))
        sectionLocked = greenLock
        val refreshGreen = mutableListOf<() -> Unit>()
        slider("Střed X", 0, 100, { green[0] }, { green[0] = it }, refreshGreen)
        slider("Spodní hrana Y", 0, 100, { green[1] }, { green[1] = it }, refreshGreen)
        slider("Šířka", 20, 100, { green[2] }, { green[2] = it }, refreshGreen)
        slider("Velikost písma", 60, 200, { green[3] }, { green[3] = it }, refreshGreen)
        reset("Obnovit výchozí polohu", greenLock) { greenDefault.copyInto(green); refreshGreen.forEach { it() } }
        heading("Oranžová · výstup herních textů", Color.parseColor("#FF9E34"))
        val orangeLock = toggle("Zamknout oranžovou oblast", locked(a,"Orange"))
        sectionLocked = orangeLock
        rect(orange, orangeDefault)
        val dialog = AlertDialog.Builder(a).setTitle("Oblasti přímého překladu")
            .setView(root).setNegativeButton("Zrušit", null)
            .setPositiveButton("Uložit") { _, _ ->
                if (busy()) {
                    android.widget.Toast.makeText(a, "Překlad běží. Nastavení nebylo změněno.",
                        android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!validRect(blue) || !validRect(yellow) || !validRect(orange)) {
                    android.widget.Toast.makeText(a, "Neplatná oblast. Nastavení nebylo změněno.",
                        android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                OblastTitulku.zamceno = blueLock.isChecked
                Volby.zamceno = yellowLock.isChecked
                OblastTitulku.rucne = manual.isChecked
                OblastTitulku.oblast = blue.copyOf()
                OblastTitulku.uloz(a)
                Volby.zapnuto = enabled.isChecked
                Volby.rezimText = asText.isChecked
                Volby.oblast = yellow.copyOf()
                Volby.uloz(a)
                val editor = prefs(a).edit().putBoolean("directGreenEnabled", customGreen.isChecked)
                editor.putBoolean("directLockGreen", greenLock.isChecked).putBoolean("directLockOrange", orangeLock.isChecked)
                orange.forEachIndexed { i, v -> editor.putFloat("directOrange"+i, v) }
                green.forEachIndexed { i, v -> editor.putFloat("directGreen"+i, v) }
                editor.apply()
                onSaved()
            }.show()
        dialog.window?.setLayout((a.resources.displayMetrics.widthPixels * .86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
