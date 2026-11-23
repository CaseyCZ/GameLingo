package cz.titulky.tv

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Actual Android GameLingo visual components, matching the native SwiftUI
 * design language. Transport, OCR and console pairing stay in their own code.
 */
object GameLingoVisualKit {
    private val cyan = Color.parseColor("#02D6FF")
    private val blue = Color.parseColor("#3557FF")
    private val purple = Color.parseColor("#A329FA")
    private val ink = Color.parseColor("#070B19")
    private val panel = Color.parseColor("#111A33")
    private val muted = Color.parseColor("#92A4C7")

    fun dp(c: Context, value: Int): Int =
        (c.resources.displayMetrics.density * value + 0.5f).toInt()

    fun card(context: Context, selected: Boolean = false): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(panel, ink, if (selected)
                Color.parseColor("#291448") else Color.parseColor("#15142A"))
        ).apply {
            cornerRadius = dp(context, 20).toFloat()
            setStroke(dp(context, if (selected) 2 else 1),
                if (selected) purple else Color.parseColor("#435177"))
        }

    fun primary(context: Context): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(cyan, blue, purple)).apply {
            cornerRadius = dp(context, 18).toFloat()
            setStroke(dp(context, 1), Color.parseColor("#9DEAFF"))
        }

    fun heading(context: Context, title: String): TextView =
        TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(context, 2), dp(context, 8),
                dp(context, 2), dp(context, 10))
        }

    fun hero(activity: Activity): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 17), dp(activity, 16),
                dp(activity, 17), dp(activity, 16))
            background = card(activity, selected = true)
        }
        row.addView(ImageView(activity).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            adjustViewBounds = true
            contentDescription = "GameLingo"
        }, LinearLayout.LayoutParams(dp(activity, 65), dp(activity, 65))
            .apply { rightMargin = dp(activity, 13) })
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = "HRAJ. ROZUMĚJ."
                textSize = 11f
                letterSpacing = 0.12f
                setTextColor(cyan)
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = "Překlad přímo při hraní"
                textSize = 17f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(activity, 4), 0, dp(activity, 4))
            })
            addView(TextView(activity).apply {
                text = "Xbox  •  PlayStation"
                textSize = 13f
                setTextColor(muted)
            })
        }, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    fun shortcut(activity: Activity, symbol: String, title: String,
                 onClick: () -> Unit): TextView = TextView(activity).apply {
        text = symbol + "\n" + title
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        minHeight = dp(activity, 103)
        setPadding(dp(activity, 9), dp(activity, 12),
            dp(activity, 9), dp(activity, 12))
        background = card(activity)
        elevation = dp(activity, 3).toFloat()
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
    }
}
