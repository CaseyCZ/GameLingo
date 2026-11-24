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
 * Persistent native mobile tab bar. Both navigation order and labels originate
 * in the same contract as the iPhone tab bar. Unlike the old implementation,
 * selection follows the currently displayed page (not permanently "Domů").
 */
object GameLingoNavigation {
    fun create(
        activity: Activity,
        onHome: () -> Unit,
        onAreas: () -> Unit,
        onDevices: () -> Unit,
        onSettings: () -> Unit,
        onAbout: () -> Unit,
        initialIndex: Int = 0
    ): LinearLayout {
        fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density + 0.5f).toInt()
        val actions = listOf(onHome, onAreas, onDevices, onSettings, onAbout)
        val tabs = GameLingoUIContract.tabs
        require(tabs.size == actions.size) { "iOS and Android tab contracts differ" }
        var activeIndex = initialIndex.coerceIn(0, actions.lastIndex)
        val views = mutableListOf<TextView>()

        fun appearance(index: Int, focused: Boolean): GradientDrawable {
            val active = index == activeIndex
            return GradientDrawable().apply {
                setColor(Color.parseColor(
                    if (focused) "#31256D" else if (active) "#442080" else "#070B19"
                ))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(if (focused) 2 else 1), Color.parseColor(
                    if (focused) GameLingoUIContract.Palette.cyan
                    else if (active) "#6B3BAF" else "#070B19"
                ))
            }
        }

        fun refresh() {
            views.forEachIndexed { index, text ->
                val active = index == activeIndex
                text.setTextColor(Color.parseColor(
                    if (active) "#FFFFFF" else GameLingoUIContract.Palette.muted
                ))
                text.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                text.background = appearance(index, text.isFocused)
                text.isSelected = active
                text.setCompoundDrawablesWithIntrinsicBounds(null,GameLingoVisualKit.iconDrawable(listOf("house.fill","rectangle.dashed","gamecontroller","slider.horizontal.3","info.circle")[index],if(active)Color.WHITE else Color.parseColor(GameLingoUIContract.Palette.muted),dp(20)),null,null)
                text.compoundDrawablePadding=dp(4)
            }
        }

        return LinearLayout(activity).apply {
            tag = "gamelingo-bottom-nav"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(7), dp(4), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(GameLingoUIContract.Palette.background))
                setStroke(dp(1), Color.parseColor("#6B3BAF"))
            }
            tabs.forEachIndexed { index, destination ->
                val item = TextView(activity).apply {
                    text = destination.title
                    contentDescription = destination.title
                    textSize = 10.5f
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setPadding(dp(1), dp(10), dp(1), dp(10))
                    minHeight = dp(44)
                    isFocusable = true
                    isClickable = true
                    setOnClickListener {
                        activeIndex = index
                        refresh()
                        actions[index]()
                    }
                    setOnFocusChangeListener { view: View, focused: Boolean ->
                        view.background = appearance(index, focused)
                    }
                }
                views.add(item)
                addView(item, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    leftMargin = dp(1)
                    rightMargin = dp(1)
                })
            }
            refresh()
        }
    }
}
