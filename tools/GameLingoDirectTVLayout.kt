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
    private fun busy() = RemotePlayService.aktivni || PrijemService.bezi

    fun show(a: Activity, onSaved: () -> Unit) {
        if (busy()) {
            AlertDialog.Builder(a).setMessage("Nejdříve zastav překlad i příjem z telefonu.")
                .setPositiveButton("OK", null).show()
            return
        }
        OblastTitulku.nacti(a)
        Volby.nacti(a)
        val blue = OblastTitulku.oblast.copyOf().takeIf(::validRect) ?: OblastTitulku.VYCHOZI
        val yellow = Volby.oblast.copyOf().takeIf(::validRect) ?: Volby.VYCHOZI
        val orange = orange(a)
        val green = green(a) ?: greenDefault.copyOf()
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 16)
        }
        val scroll = ScrollView(a).apply { addView(root) }
        fun heading(label: String, color: Int) {
            root.addView(TextView(a).apply {
                text = label; textSize = 19f; setTextColor(color)
                setPadding(0, 16, 0, 6)
            })
        }
        fun toggle(label: String, enabled: Boolean): Switch = Switch(a).apply {
            text = label; isChecked = enabled; isFocusable = true
            root.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        fun slider(label: String, min: Int, max: Int, current: () -> Float, update: (Float) -> Unit,
                   refreshAll: MutableList<() -> Unit>) {
            val title = TextView(a)
            val bar = SeekBar(a).apply {
                this.max = max-min; keyProgressIncrement = 5; isFocusable = true
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
                        update((progress+min)/100f)
                        refreshAll.forEach { it() }
                    }
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            })
            root.addView(title)
            root.addView(bar)
            refresh()
        }
        fun rect(r: FloatArray) {
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
        }
        heading("Modrá · čtení dialogů", Color.parseColor("#4FB3FF"))
        val manual = toggle("Ruční oblast", OblastTitulku.rucne)
        rect(blue)
        heading("Žlutá · čtení herních textů", Color.parseColor("#FFD54A"))
        val enabled = toggle("Číst oblast herních textů", Volby.zapnuto)
        val asText = toggle("Celý text místo seznamu voleb", Volby.rezimText)
        rect(yellow)
        heading("Zelená · výstup dialogů", Color.parseColor("#3CFF6E"))
        val customGreen = toggle("Vlastní poloha místo automatické", green(a) != null)
        val refreshGreen = mutableListOf<() -> Unit>()
        slider("Střed X", 0, 100, { green[0] }, { green[0] = it }, refreshGreen)
        slider("Spodní hrana Y", 0, 100, { green[1] }, { green[1] = it }, refreshGreen)
        slider("Šířka", 20, 100, { green[2] }, { green[2] = it }, refreshGreen)
        slider("Velikost písma", 60, 200, { green[3] }, { green[3] = it }, refreshGreen)
        heading("Oranžová · výstup herních textů", Color.parseColor("#FF9E34"))
        rect(orange)
        AlertDialog.Builder(a).setTitle("Oblasti přímého překladu")
            .setView(scroll).setNegativeButton("Zrušit", null)
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
                OblastTitulku.rucne = manual.isChecked
                OblastTitulku.oblast = blue.copyOf()
                OblastTitulku.uloz(a)
                Volby.zapnuto = enabled.isChecked
                Volby.rezimText = asText.isChecked
                Volby.oblast = yellow.copyOf()
                Volby.uloz(a)
                val editor = prefs(a).edit().putBoolean("directGreenEnabled", customGreen.isChecked)
                orange.forEachIndexed { i, v -> editor.putFloat("directOrange"+i, v) }
                green.forEachIndexed { i, v -> editor.putFloat("directGreen"+i, v) }
                editor.apply()
                onSaved()
            }.show()
    }
}
