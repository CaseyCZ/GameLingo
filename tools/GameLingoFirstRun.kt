package cz.titulky.tv

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Own GameLingo first-run wizard for Android phone.
 * Only stores user choices and invokes the proven Xbox/PS/TV setup screens.
 * Does not fake successful console pairing or network readiness.
 */
object GameLingoFirstRun {
    private const val PREFS = "gamelingo_onboarding"
    private const val COMPLETE = "completed"

    fun completed(activity: Activity): Boolean = activity.getSharedPreferences(
        PREFS, Activity.MODE_PRIVATE).getBoolean(COMPLETE, false)

    fun show(
        activity: Activity,
        connectXbox: () -> Unit,
        connectPlayStation: () -> Unit,
        connectTV: () -> Unit,
        finished: () -> Unit
    ) {
        if (activity.isFinishing) return
        val colors = arrayOf("#070B14", "#111827", "#38BDF8", "#94A3B8")
        val navy = Color.parseColor(colors[0])
        val card = Color.parseColor(colors[1])
        val cyan = Color.parseColor(colors[2])
        val muted = Color.parseColor(colors[3])
        var step = 0
        var consoleChoice = -1
        var tvChoice = -1
        // Full-screen onboarding belongs to GameLingo, not to an upstream
        // PlayCaption popup floating above the old landing page.
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 22)
            background = GradientDrawable().apply {
                setColor(navy)
                cornerRadius = 25f
            }
        }
        root.addView(TextView(activity).apply {
            text = "GameLingo"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 4)
        })
        root.addView(TextView(activity).apply {
            text = "Hraj. Rozuměj."
            textSize = 14f
            setTextColor(muted)
            setPadding(0, 0, 0, 24)
        })
        val heading = TextView(activity).apply {
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        val headingRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        headingRow.addView(heading, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        headingRow.addView(GameLingoHelp.button(activity, "home"))
        root.addView(headingRow)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        root.addView(body)
        val footer = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 0)
        }
        root.addView(footer)

        fun button(text: String, checked: Boolean = false,
                   onClick: () -> Unit): TextView = TextView(activity).apply {
            this.text = (if (checked) "✓  " else "") + text
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(19, 20, 19, 20)
            background = GradientDrawable().apply {
                setColor(card)
                cornerRadius = 16f
                setStroke(2, if (checked) cyan else Color.parseColor("#2A3850"))
            }
            isFocusable = true
            setOnClickListener { onClick() }
        }
        fun item(label: String, checked: Boolean, run: () -> Unit) {
            body.addView(button(label, checked, run),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10 })
        }

        fun finish() {
            if (consoleChoice < 0 || tvChoice < 0) return
            TvProfily(activity).pouzeTelefon = tvChoice == 0
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit()
                .putBoolean(COMPLETE, true)
                .putInt("console", consoleChoice)
                .putInt("output", tvChoice).apply()
            dialog.dismiss()
            finished()
            when (consoleChoice) {
                0 -> connectXbox()
                1 -> connectPlayStation()
            }
            if (tvChoice == 1) {
                // TV selection remains available in the central device menu.
                // Do not try to open a second modal over Sony/Microsoft login.
            }
        }
        lateinit var redraw: () -> Unit
        redraw = {
            body.removeAllViews()
            footer.removeAllViews()
            heading.text = when (step) {
                0 -> "Kde hraješ?"
                1 -> "Kam zobrazit titulky?"
                else -> "Zkontroluj nastavení"
            }
            when (step) {
                0 -> {
                    item("🎮  Xbox", consoleChoice == 0) {
                        consoleChoice = 0
                        redraw()
                    }
                    item("🎮  PlayStation 5 / 4", consoleChoice == 1) {
                        consoleChoice = 1
                        redraw()
                    }
                }
                1 -> {
                    item("📱  Na telefonu", tvChoice == 0) {
                        tvChoice = 0
                        redraw()
                    }
                    item("📺  Google TV / Android TV", tvChoice == 1) {
                        tvChoice = 1
                        redraw()
                    }
                }
                else -> {
                    val selected = if (consoleChoice == 0) "Xbox" else "PlayStation"
                    val output = if (tvChoice == 0) "Telefon" else "Google TV"
                    item("$selected   →   $output", true) {}
                    body.addView(TextView(activity).apply {
                        text = "Výběr není potvrzení spojení. Přihlášení a párování se ověří zvlášť."
                        textSize = 14f
                        setTextColor(muted)
                        setPadding(6, 7, 6, 14)
                    })
                    if (tvChoice == 1) {
                        item("📺  Spárovat Google TV", false) {
                            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                                .edit().putBoolean(COMPLETE, true)
                                .putInt("console", consoleChoice)
                                .putInt("output", tvChoice).apply()
                            TvProfily(activity).pouzeTelefon = false
                            dialog.dismiss()
                            finished()
                            connectTV()
                        }
                    }
                }
            }
            // Skipping is offered on every step. The completed flag only
            // means the welcome flow was dismissed, never "console paired".
            item("Nastavit později", false) {
                activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                    .edit().putBoolean(COMPLETE, true).apply()
                dialog.dismiss()
                finished()
            }
            if (step > 0) {
                footer.addView(button("Zpět") {
                    step--
                    redraw()
                }, LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = 8
                })
            }
            footer.addView(button(if (step == 2) "Připojit" else "Pokračovat") {
                if (step < 2) {
                    if ((step == 0 && consoleChoice >= 0) ||
                        (step == 1 && tvChoice >= 0)) {
                        step++
                        redraw()
                    }
                } else finish()
            }, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        redraw()
        val scroll = ScrollView(activity)
        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.setCancelable(false)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
