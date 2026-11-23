package cz.titulky.tv

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.WeakHashMap

/**
 * First-run GameLingo onboarding on Android phones.
 * The wizard selects a real console family and translation output.
 * It never pretends that a console is paired or saves credentials.
 */
object GameLingoWelcome {
    private const val KEY = "gamelingo_welcome_v1"
    private val showing = WeakHashMap<Activity, Boolean>()
    private val navy = Color.parseColor("#070B14")
    private val surface = Color.parseColor("#111827")
    private val subtle = Color.parseColor("#172033")
    private val cyan = Color.parseColor("#38BDF8")
    private val muted = Color.parseColor("#94A3B8")

    private fun background(color: Int, selected: Boolean = false) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = 20f
            setStroke(2, if (selected) cyan else Color.parseColor("#2A3850"))
        }

    fun maybeShow(
        activity: Activity,
        onXbox: () -> Unit,
        onPlayStation: () -> Unit,
        onTV: () -> Unit
    ) = show(activity, false, onXbox, onPlayStation, onTV)

    fun show(
        activity: Activity,
        force: Boolean = true,
        onXbox: () -> Unit,
        onPlayStation: () -> Unit,
        onTV: () -> Unit
    ) {
        if (activity.isFinishing || showing[activity] == true) return
        val preferences = activity.getSharedPreferences(KEY, Context.MODE_PRIVATE)
        if (!force && preferences.getBoolean("completed", false)) return
        showing[activity] = true

        val dialog = Dialog(activity)
        var step = 0
        var console = preferences.getString("source", "xbox") ?: "xbox"
        var output = preferences.getString("output", "phone") ?: "phone"

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 26, 25, 22)
            background = background(navy)
        }

        fun title(text: String, size: Float = 23f): TextView =
            TextView(activity).apply {
                this.text = text
                textSize = size
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(5, 6, 5, 13)
            }

        fun action(name: String, icon: String, chosen: Boolean,
                   listener: () -> Unit): TextView =
            TextView(activity).apply {
                text = "$icon    $name" + if (chosen) "     ✓" else ""
                textSize = 19f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = 60
                isClickable = true
                isFocusable = true
                background = background(surface, chosen)
                setPadding(22, 18, 22, 18)
                setOnClickListener { listener() }
            }

        fun add(view: View) {
            root.addView(view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 })
        }

        fun caption(text: String): TextView =
            TextView(activity).apply {
                this.text = text
                textSize = 14f
                setTextColor(muted)
                setPadding(5, 2, 5, 9)
            }

        fun complete() {
            // Finish onboarding only after an explicit action, not merely
            // because the user opened the dialog.
            preferences.edit()
                .putString("source", console)
                .putString("output", output)
                .putBoolean("completed", true)
                .apply()
            TvProfily(activity).pouzeTelefon = (output == "phone")
            dialog.dismiss()
            // The real authentication/connection flow is opened next.
            if (console == "ps") onPlayStation() else onXbox()
        }

        lateinit var render: () -> Unit
        render = {
            root.removeAllViews()
            val top = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(title("GameLingo", 28f),
                LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(GameLingoHelp.button(activity, "home"))
            add(top)

            val steps = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            repeat(3) { index ->
                steps.addView(View(activity).apply {
                    background = background(
                        if (index <= step) cyan else Color.parseColor("#26334A"))
                }, LinearLayout.LayoutParams(0, 6, 1f).apply {
                    rightMargin = if (index == 2) 0 else 10
                })
            }
            add(steps)

            when (step) {
                0 -> {
                    add(title("Vyber konzoli"))
                    add(action("Xbox", "🎮", console == "xbox") {
                        console = "xbox"; step = 1; render()
                    })
                    add(action("PlayStation 5 / 4", "🎮", console == "ps") {
                        console = "ps"; step = 1; render()
                    })
                }
                1 -> {
                    add(title("Kam zobrazit titulky?"))
                    add(action("Na telefonu", "📱", output == "phone") {
                        output = "phone"; step = 2; render()
                    })
                    add(action("Google TV", "📺", output == "tv") {
                        output = "tv"; step = 2; render()
                    })
                    add(action("Zpět", "‹", false) { step = 0; render() })
                }
                else -> {
                    add(title("Připraveno k propojení"))
                    add(action(if (console == "ps") "PlayStation" else "Xbox",
                               "🎮", true) { step = 0; render() })
                    add(action(if (output == "tv") "Google TV" else "Telefon",
                               if (output == "tv") "📺" else "📱",
                               true) { step = 1; render() })
                    add(action("Připojit konzoli", "▶", true) { complete() })
                    if (output == "tv") {
                        add(action("Nejdřív spárovat TV", "▣", false) {
                            preferences.edit()
                                .putString("source", console)
                                .putString("output", output)
                                .putBoolean("completed", true)
                                .apply()
                            TvProfily(activity).pouzeTelefon = false
                            dialog.dismiss()
                            onTV()
                        })
                    }
                }
            }
            add(caption("Výběr zařízení můžeš později změnit v Zařízeních."))
            add(action("Přeskočit", "×", false) {
                preferences.edit().putBoolean("completed", true).apply()
                dialog.dismiss()
            })
        }

        val scroll = ScrollView(activity)
        scroll.addView(root)
        render()
        dialog.setContentView(scroll)
        dialog.setOnDismissListener { showing.remove(activity) }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
