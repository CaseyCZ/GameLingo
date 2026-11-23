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
        return when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("color", "white")) {
            "yellow" -> Color.rgb(255, 213, 74)
            "cyan" -> Color.rgb(2, 214, 255)
            "green" -> Color.rgb(53, 229, 125)
            "purple" -> Color.rgb(163, 41, 250)
            else -> Color.WHITE
        }
    }

    fun background(context: Context): Int {
        return when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("background", "dark")) {
            "none" -> Color.TRANSPARENT
            "translucent" -> 0x76000000
            else -> 0xE6000000.toInt()
        }
    }

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("background", "dark") == "dark"
}
