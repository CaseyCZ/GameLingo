package cz.titulky.tv

import android.content.Context
import android.graphics.Color
import android.widget.TextView

/**
 * Native Android phone subtitle appearance, matching the iPhone choices.
 * Stored separately from Google TV receiver typography and the colored
 * OCR area overlays; applied to the real TelefonActivity caption TextView.
 */
object GameLingoPhoneSubtitleStyle {
    private const val PREFS = "gamelingo_phone_subtitles"
    val colors = listOf("white", "yellow", "cyan", "green", "purple")
    val backgrounds = listOf("none", "dark", "translucent")

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun scale(c: Context): Float = prefs(c).getFloat("scale", 1f).coerceIn(0.70f, 1.80f)
    fun color(c: Context): String = prefs(c).getString("color", "white")
        ?.takeIf { it in colors } ?: "white"
    fun background(c: Context): String = prefs(c).getString("background", "none")
        ?.takeIf { it in backgrounds } ?: "none"

    fun setScale(c: Context, scale: Float) {
        prefs(c).edit().putFloat("scale", scale.coerceIn(0.70f, 1.80f)).apply()
    }
    fun setColor(c: Context, selected: String) {
        require(selected in colors)
        prefs(c).edit().putString("color", selected).apply()
    }
    fun setBackground(c: Context, selected: String) {
        require(selected in backgrounds)
        prefs(c).edit().putString("background", selected).apply()
    }
    fun textColor(selected: String): Int = when (selected) {
        "yellow" -> Color.YELLOW
        "cyan" -> Color.parseColor(GameLingoUIContract.Palette.cyan)
        "green" -> Color.GREEN
        "purple" -> Color.parseColor(GameLingoUIContract.Palette.purple)
        else -> Color.WHITE
    }
    fun backgroundColor(selected: String): Int = when (selected) {
        "dark" -> Color.argb(224, 0, 0, 0)
        "translucent" -> Color.argb(117, 0, 0, 0)
        else -> Color.TRANSPARENT
    }
    fun apply(context: Context, view: TextView) {
        view.setTextColor(textColor(color(context)))
        view.setBackgroundColor(backgroundColor(background(context)))
    }
}
