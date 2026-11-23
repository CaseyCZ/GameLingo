package cz.titulky.tv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * GameLingo-owned Android bottom navigation.
 * Shared destinations and order with the native iPhone dashboard.
 *
 * This actual Kotlin component replaces previously generated navigation
 * snippets. No PlayCaption navigation labels or behavior are reused.
 */
object GameLingoNavigation {
    private data class Destination(
        val icon: String,
        val label: String,
        val action: () -> Unit
    )

    fun create(
        activity: Activity,
        onHome: () -> Unit,
        onAreas: () -> Unit,
        onProfiles: () -> Unit,
        onDevices: () -> Unit,
        onAbout: () -> Unit
    ): LinearLayout {
        fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()
        val destinations = listOf(
            Destination("⌂", "Domů", onHome),
            Destination("▣", "Oblasti", onAreas),
            Destination("♧", "Profily", onProfiles),
            Destination("▭", "Zařízení", onDevices),
            Destination("ⓘ", "O aplikaci", onAbout)
        )

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#070B19"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#6B3BAF"))
            }
            destinations.forEachIndexed { index, destination ->
                val active = index == 0
                addView(TextView(activity).apply {
                    text = destination.icon + "\n" + destination.label
                    contentDescription = destination.label
                    textSize = 11.5f
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(if (active) "#FFFFFF" else "#92A4C7"))
                    typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setPadding(dp(2), dp(7), dp(2), dp(7))
                    minHeight = dp(58)
                    isFocusable = true
                    isClickable = true
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor(if (active) "#442080" else "#111A33"))
                        cornerRadius = dp(11).toFloat()
                    }
                    setOnClickListener { destination.action() }
                    setOnFocusChangeListener { view: View, hasFocus: Boolean ->
                        view.background = GradientDrawable().apply {
                            setColor(Color.parseColor(if (hasFocus) "#31256D" else if (active) "#442080" else "#111A33"))
                            cornerRadius = dp(11).toFloat()
                            setStroke(dp(if (hasFocus) 2 else 1),
                                Color.parseColor(if (hasFocus) "#02D6FF" else "#554078"))
                        }
                    }
                }, LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                })
            }
        }
    }
}
