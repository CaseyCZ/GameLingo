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
        r[4].isFinite() && r[4] in 0.6f..2f

    fun read(c: Context): FloatArray {
        val p = prefs(c)
        val d = defaults()
        return FloatArray(5) { p.getFloat(keys[it], d[it]) }.takeIf(::valid) ?: d
    }

    fun locked(c: Context) = prefs(c).getBoolean(LOCK, false)

    fun save(c: Context, r: FloatArray, locked: Boolean) {
        if (!valid(r)) return
        val editor = prefs(c).edit().putBoolean(LOCK, locked)
        keys.forEachIndexed { i, key -> editor.putFloat(key, r[i]) }
        editor.apply()
    }

    fun publish(c: Context, preview: Boolean = false) {
        Propojeni.Vysilac.nastavOranzovyVystup(read(c), if (preview)
            GameLingoPhoneSubtitleStyle.sample(c) else null)
    }

    /** Existing region editor, separate geometry controls so overlapping rectangles
     * cannot accidentally move the blue, yellow or green regions. */
    fun editor(c: Context, onChanged: () -> Unit): LinearLayout {
        val dp: (Int) -> Int = { GameLingoVisualKit.dp(c, it) }
        val panel = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GameLingoVisualKit.card(c)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val r = read(c)
        var locked = locked(c)
        val orange = Color.parseColor("#FF9E34")
        fun text(label: String) = TextView(c).apply {
            this.text = label
            setTextColor(Color.WHITE)
            textSize = 14f
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
        panel.addView(preview, LinearLayout.LayoutParams(
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
            sample.textSize = (10f*r[4]).coerceIn(6f, 20f)
        }
        preview.addOnLayoutChangeListener { _, l, t, right, b, ol, ot, oright, ob ->
            if (right-l != oright-ol || b-t != ob-ot) draw()
        }
        fun persist() { save(c, r, locked); publish(c); draw(); onChanged() }
        val sliders = mutableListOf<SeekBar>()
        fun slider(label: String, min: Int, max: Int, value: () -> Int, change: (Int) -> Unit) {
            val title = text("")
            fun refresh() { title.text = "$label: ${value()} %" }
            refresh()
            panel.addView(title)
            val bar = SeekBar(c).apply {
                this.max = max-min
                progress = (value()-min).coerceIn(0, this.max)
                isEnabled = !locked
                progressTintList = android.content.res.ColorStateList.valueOf(orange)
                thumbTintList = android.content.res.ColorStateList.valueOf(orange)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser || locked) return
                        change(progress+min)
                        refresh(); draw()
                    }
                    override fun onStartTrackingTouch(seek: SeekBar) {}
                    override fun onStopTrackingTouch(seek: SeekBar) { if (!locked) persist() }
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
        slider("Velikost písma", 60, 200, { pct(r[4]) }) { r[4] = it/100f }
        panel.addView(text(if (locked) "🔒 Zamčeno" else "🔓 Odemčeno").apply {
            isFocusable = true
            setOnClickListener {
                locked = !locked
                text = if (locked) "🔒 Zamčeno" else "🔓 Odemčeno"
                sliders.forEach { it.isEnabled = !locked }
                persist()
            }
        })
        panel.addView(text("Náhled na TV").apply {
            gravity = Gravity.CENTER
            background = GameLingoVisualKit.card(c, selected = true)
            minHeight = dp(48)
            isFocusable = true
            setOnClickListener {
                if (Propojeni.Vysilac.cilIp == null) {
                    android.widget.Toast.makeText(c, "Nejdříve připoj televizi.", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    persist()
                    publish(c, preview = true)
                }
            }
        })
        return panel
    }
}
