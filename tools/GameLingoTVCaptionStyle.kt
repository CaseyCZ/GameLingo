package cz.titulky.tv

import android.content.Context
import android.graphics.Color

/**
 * Styling of *translated captions displayed by GameLingo's TV receiver*.
 * Settings arrive only in the authenticated PCPAIR1 STYLE packet.
 * Local preferences are intentionally independent of legacy PlayCaption.
 */
object GameLingoTVCaptionStyle {
    private const val PREFS = "gamelingo_tv_caption_style"
    private const val LOCAL = "gamelingo_tv_local_style"
    private fun local(context: Context) = context.getSharedPreferences(LOCAL, Context.MODE_PRIVATE)
    private fun current(context: Context) = if (PrijemService.bezi)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) else local(context)
    private val acceptedColors = setOf("white", "yellow", "cyan", "green", "purple")
    private val acceptedBackgrounds = setOf("dark", "translucent", "none")

    fun save(context: Context, color: String, background: String): Boolean {
        if (color !in acceptedColors || background !in acceptedBackgrounds) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("color", color)
            .putString("background", background)
            .apply()
        return true
    }

    fun textColor(context: Context): Int {
        return when (current(context).getString("color", "white")) {
            "yellow" -> Color.rgb(255, 213, 74)
            "cyan" -> Color.rgb(2, 214, 255)
            "green" -> Color.rgb(53, 229, 125)
            "purple" -> Color.rgb(163, 41, 250)
            else -> Color.WHITE
        }
    }

    fun background(context: Context): Int {
        if (!PrijemService.bezi) return Color.argb(local(context).getInt("opacity", 230).coerceIn(0, 255), 0, 0, 0)
        return when (current(context).getString("background", "dark")) {
            "none" -> Color.TRANSPARENT
            "translucent" -> 0x76000000
            else -> 0xE6000000.toInt()
        }
    }

    fun isDark(context: Context): Boolean =
        if (PrijemService.bezi) current(context).getString("background", "dark") == "dark"
        else false

    fun show(a: android.app.Activity) {
        if (RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running) {
            android.app.AlertDialog.Builder(a).setMessage("Nejdřív zastav překlad a příjem.").setPositiveButton("OK", null).show()
            return
        }
        val keys = arrayOf("white", "yellow", "cyan", "green", "purple")
        val names = arrayOf("Bílá", "Žlutá", "Tyrkysová", "Zelená", "Fialová")
        val p = local(a)
        val root = android.widget.LinearLayout(a).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
        }
        val preview = android.widget.TextView(a).apply {
            text = "Ukázka přeložených titulků"; textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(12, 18, 12, 18)
        }
        fun refresh() {
            preview.setTextColor(textColor(a)); preview.setBackgroundColor(background(a))
        }
        root.addView(preview)
        root.addView(GameLingoTVUi.action(a, "Barva písma") {
            GameLingoTVUi.select(a, "Barva písma", names, keys.indexOf(p.getString("color", "white"))) { index ->
                p.edit().putString("color", keys[index]).apply(); refresh()
            }
        })
        root.addView(GameLingoTVUi.action(a, "Pozadí titulků") {
            val dialogColors = arrayOf("Bez pozadí", "Tmavé", "Průhledné")
            val values = intArrayOf(0,230,117)
            val selected = values.indexOf(p.getInt("opacity",230))
            GameLingoTVUi.select(a,"Pozadí titulků",dialogColors,selected) { index ->
                p.edit().putInt("opacity",values[index]).apply()
                refresh()
            }
        })
        val label = android.widget.TextView(a).apply { text = "Neprůhlednost pozadí" }
        root.addView(label)
        root.addView(android.widget.SeekBar(a).apply {
            max = 100; progress = p.getInt("opacity", 230) * 100 / 255
            keyProgressIncrement = 5; isFocusable = true
            GameLingoTVUi.controlFocus(a, this)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, value: Int, fromUser: Boolean) {
                    label.text = "Neprůhlednost pozadí: $value %"
                    if (fromUser) { p.edit().putInt("opacity", value * 255 / 100).apply(); refresh() }
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {}
            })
        })
        root.addView(GameLingoTVUi.action(a, "Velikost a poloha titulků") { GameLingoDirectTVLayout.show(a) {} })
        refresh()
        val dialog = android.app.AlertDialog.Builder(a).setTitle("Vzhled překladu přímo na TV")
            .setView(root).setPositiveButton("Hotovo", null).show()
        dialog.window?.setLayout((a.resources.displayMetrics.widthPixels * .55f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
