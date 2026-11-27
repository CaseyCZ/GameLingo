package cz.titulky.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Independent output only: never reads or writes Volby.oblast (yellow OCR). */
object GameLingoOrangeOutput {
    private val keys = listOf("orangeL", "orangeT", "orangeR", "orangeB", "orangeFont")
    private const val LOCK = "orangeLocked"
    private fun prefs(c: Context) = TvProfily.nastaveni(c)
    fun defaults() = floatArrayOf(0.07f, 0.43f, 0.50f, 0.60f, 1f)
    fun valid(r: FloatArray): Boolean = r.size == 5 &&
        r.take(4).all { it.isFinite() && it in 0f..1f } &&
        r[2] > r[0] + 0.10f && r[3] > r[1] + 0.05f &&
        r[4].isFinite() && r[4] in 0.1f..2f

    fun read(c: Context): FloatArray = readFrom(prefs(c))
    fun readFrom(p: android.content.SharedPreferences): FloatArray {
        val d = defaults()
        return FloatArray(5) { p.getFloat(keys[it], d[it]) }.takeIf(::valid) ?: d
    }

    fun locked(c: Context) = lockedFrom(prefs(c))
    fun lockedFrom(p: android.content.SharedPreferences) = p.getBoolean(LOCK, false)

    fun save(c: Context, r: FloatArray, locked: Boolean) = saveTo(prefs(c), r, locked)
    fun saveTo(p: android.content.SharedPreferences, r: FloatArray, locked: Boolean) {
        if (!valid(r)) return
        val editor = p.edit().putBoolean(LOCK, locked)
        keys.forEachIndexed { i, key -> editor.putFloat(key, r[i]) }
        editor.apply()
    }

    fun moveCenter(rect: FloatArray, x: Float, y: Float): FloatArray {
        val r = rect.takeIf(::valid)?.copyOf() ?: defaults()
        if (!x.isFinite() || !y.isFinite()) return r
        val width = r[2]-r[0]; val height = r[3]-r[1]
        r[0] = (x-width/2).coerceIn(0f, 1f-width)
        r[1] = (y-height/2).coerceIn(0f, 1f-height)
        r[2] = (r[0]+width).coerceAtMost(1f)
        r[3] = (r[1]+height).coerceAtMost(1f)
        return r
    }

    fun publish(c: Context, preview: Boolean = false) {
        Propojeni.Vysilac.nastavOranzovyVystup(read(c), if (preview)
            GameLingoPhoneSubtitleStyle.sample(c) else null)
    }

    /** Existing region editor, separate geometry controls so overlapping rectangles
     * cannot accidentally move the blue, yellow or green regions. */
    fun editor(c: Context, includePreview: Boolean = true, onChanged: () -> Unit): LinearLayout {
        val dp: (Int) -> Int = { GameLingoVisualKit.dp(c, it) }
        val panel = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.card(c)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val r = read(c)
        var isLocked = locked(c)
        val orange = Color.parseColor("#FF9E34")
        fun text(label: String) = TextView(c).apply {
            this.text = GameLingoLanguages.ui(c, label)
            setTextColor(Color.WHITE)
            textSize = GameLingoVisualKit.sp(c, 14f)
            setPadding(0, dp(6), 0, dp(6))
        }
        panel.addView(text("Oranžová · výstup herních textů").apply {
            setTextColor(orange); typeface = Typeface.DEFAULT_BOLD
        })
        val preview = FrameLayout(c).apply {
            background = GameLingoVisualKit.card(c)
        }
        val sample = TextView(c).apply {
            text = GameLingoPhoneSubtitleStyle.sample(c)
            gravity = Gravity.CENTER
            setTextColor(orange)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33251A")); setStroke(dp(2), orange)
            }
        }
        preview.addView(sample, FrameLayout.LayoutParams(1, 1))
        if (includePreview) panel.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130)))
        fun draw() {
            val w = preview.width
            val h = preview.height
            if (w <= 0 || h <= 0) return
            sample.layoutParams = FrameLayout.LayoutParams(
                ((r[2]-r[0])*w).toInt().coerceAtLeast(1),
                ((r[3]-r[1])*h).toInt().coerceAtLeast(1)).apply {
                leftMargin = (r[0]*w).toInt(); topMargin = (r[1]*h).toInt()
            }
            sample.textSize = (10f*r[4]).coerceIn(1f, 20f)
        }
        preview.addOnLayoutChangeListener { _, l, t, right, b, ol, ot, oright, ob ->
            val width = right-l
            if (width > 0 && preview.layoutParams.height != width*9/16) {
                preview.layoutParams = preview.layoutParams.apply { height = width*9/16 }
            }
            if (width != oright-ol || b-t != ob-ot) draw()
        }
        val receiverID = TvProfily.aktivniId
        val publishPending = Runnable {
            if (panel.isAttachedToWindow && TvProfily.aktivniId == receiverID) publish(c)
        }
        fun persist(immediate: Boolean = true) {
            if (TvProfily.aktivniId != receiverID) return
            save(c, r, isLocked); draw(); onChanged()
            panel.removeCallbacks(publishPending)
            if (immediate) publish(c) else panel.postDelayed(publishPending, 180)
        }
        panel.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) {}
            override fun onViewDetachedFromWindow(view: android.view.View) { panel.removeCallbacks(publishPending) }
        })
        val sliders = mutableListOf<SeekBar>()
        fun slider(label: String, min: Int, max: Int, value: () -> Int, change: (Int) -> Unit) {
            val title = text("")
            fun refresh() { title.text = GameLingoLanguages.ui(c, label) + ": ${value()} %" }
            refresh()
            panel.addView(title)
            val bar = SeekBar(c).apply {
                this.max = max-min
                progress = (value()-min).coerceIn(0, this.max)
                isEnabled = !isLocked
                progressTintList = android.content.res.ColorStateList.valueOf(orange)
                thumbTintList = android.content.res.ColorStateList.valueOf(orange)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser || isLocked) return
                        change(progress+min)
                        refresh(); persist(immediate = false)
                    }
                    override fun onStartTrackingTouch(seek: SeekBar) {}
                    override fun onStopTrackingTouch(seek: SeekBar) { if (!isLocked) persist() }
                })
            }
            sliders.add(bar)
            panel.addView(bar)
        }
        fun pct(v: Float) = kotlin.math.round(v*100).toInt()
        slider("Poloha X", 0, 89, { pct(r[0]) }) {
            val width = r[2]-r[0]
            r[0] = (it/100f).coerceIn(0f, 1f-width); r[2] = r[0]+width
        }
        slider("Poloha Y", 0, 94, { pct(r[1]) }) {
            val height = r[3]-r[1]
            r[1] = (it/100f).coerceIn(0f, 1f-height); r[3] = r[1]+height
        }
        slider("Šířka", 11, 100, { pct(r[2]-r[0]) }) {
            val width = it/100f
            r[0] = r[0].coerceAtMost(1f-width); r[2] = r[0]+width
        }
        slider("Výška", 6, 100, { pct(r[3]-r[1]) }) {
            val height = it/100f
            r[1] = r[1].coerceAtMost(1f-height); r[3] = r[1]+height
        }
        slider("Velikost písma", 10, 200, { pct(r[4]) }) { r[4] = it/100f }
        fun lockLabel() = (if (isLocked) "🔒 " else "🔓 ") + GameLingoLanguages.ui(c, if (isLocked) "Zamčeno" else "Odemčeno")
        panel.addView(text(lockLabel()).apply {
            isFocusable = true
            setOnClickListener {
                isLocked = !isLocked
                text = lockLabel()
                sliders.forEach { it.isEnabled = !isLocked }
                persist()
            }
        })
        panel.addView(text("Náhled na TV").apply {
            gravity = Gravity.CENTER
            background = GameLingoVisualKit.card(c, selected = true)
            minHeight = dp(48)
            isFocusable = true
            setOnClickListener {
                if (TvProfily.aktivniId != receiverID) return@setOnClickListener
                if (Propojeni.Vysilac.cilIp == null) {
                    android.widget.Toast.makeText(c, GameLingoLanguages.ui(c, "Nejdříve připoj televizi."), android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    persist()
                    publish(c, preview = true)
                }
            }
        })
        return panel
    }
}
