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
        val cyan = Color.parseColor("#02D6FF")
        val muted = Color.parseColor("#92A4C7")
        val px: (Int) -> Int = { GameLingoVisualKit.dp(activity, it) }
        var step = 0
        var consoleChoice = -1
        var tvChoice = 0
        // Full-screen onboarding belongs to GameLingo, not to an upstream
        // PlayCaption popup floating above the old landing page.
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(18), px(20), px(24))
            background = GameLingoVisualKit.pageBackground(activity)
        }
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
        val progress = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 15, 0, 0)
        }
        root.addView(progress)
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
            setPadding(px(19), px(17), px(19), px(17))
            background = if (text == "Pokračovat" || text == "Dokončit nastavení" ||
                text == "Připojit konzoli" || text == "Spárovat TV")
                GameLingoVisualKit.primary(activity)
                else GameLingoVisualKit.card(activity, selected = checked)
            isFocusable = true
            setOnClickListener { onClick() }
        }
        fun item(label: String, checked: Boolean, run: () -> Unit) {
            body.addView(button(label, checked, run),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = px(10) })
        }

        fun consoleRegistered(): Boolean = if (consoleChoice == 0) {
            XboxUcet.prihlasen(activity) &&
                !activity.getSharedPreferences("gamelingo_devices", Activity.MODE_PRIVATE)
                    .getString("selectedXboxId", null).isNullOrBlank()
        } else {
            consoleChoice == 1 && GameLingoPlayStationConsoles.list(activity)
                .any { it.selected }
        }

        fun televisionSaved(): Boolean {
            if (tvChoice == 0) return true
            val store = TvProfily(activity)
            return tvChoice == 1 && store.posledniId?.let { store.najdi(it) } != null
        }

        // The welcome flow is not evidence of a live console/receiver connection.
        // It closes before launching one provider modal, never both on top.
        fun finish(open: Int = 0) {
            val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            val edit = prefs.edit().putBoolean(COMPLETE, true)
            if (consoleChoice >= 0) edit.putInt("console", consoleChoice)
            if (tvChoice >= 0) {
                edit.putInt("output", tvChoice)
                TvProfily(activity).pouzeTelefon = tvChoice == 0
            }
            edit.apply()
            dialog.dismiss()
            finished()
            when (open) {
                1 -> connectXbox()
                2 -> connectPlayStation()
                3 -> connectTV()
            }
        }

        lateinit var redraw: () -> Unit
        redraw = {
            body.removeAllViews()
            footer.removeAllViews()
            progress.removeAllViews()
            for (index in GameLingoUIContract.setupSteps.indices) {
                progress.addView(android.view.View(activity).apply {
                    background = GradientDrawable().apply {
                        setColor(if (index <= step) cyan else Color.parseColor("#23304C"))
                        cornerRadius = GameLingoVisualKit.dp(activity, 4).toFloat()
                    }
                }, LinearLayout.LayoutParams(0,
                    GameLingoVisualKit.dp(activity, 5), 1f).apply {
                    rightMargin = GameLingoVisualKit.dp(activity, 8)
                })
            }
            heading.text = GameLingoUIContract.setupSteps[step]
            when (step) {
                0 -> {
                    body.addView(GameLingoVisualKit.hero(activity))
                    body.addView(TextView(activity).apply {
                        text = "Překládej dialogy a herní texty přímo během hraní."
                        textSize = 15f
                        setTextColor(muted)
                        setPadding(0, px(16), 0, px(14))
                    })
                    item("⚡  Překlad v reálném čase", false) { step = 1; redraw() }
                    item("🎮  Xbox a PlayStation", false) { step = 2; redraw() }
                    item("📺  Telefon a Google TV", false) { step = 3; redraw() }
                }
                1 -> {
                    item("🌐  Jazyk aplikace  •  " +
                         GameLingoLanguages.name(GameLingoLanguages.appLanguages,
                             GameLingoLanguages.app(activity)), true) {
                        GameLingoLanguages.choose(activity, GameLingoLanguages.Kind.APP) {
                            redraw()
                        }
                    }
                    item("🎮  Jazyk hry  •  " +
                         GameLingoLanguages.name(GameLingoLanguages.gameLanguages,
                             GameLingoLanguages.game(activity)), true) {
                        GameLingoLanguages.choose(activity, GameLingoLanguages.Kind.GAME) {
                            redraw()
                        }
                    }
                    item("文  Překládat do  •  " +
                         GameLingoLanguages.name(GameLingoLanguages.targetLanguages,
                             GameLingoLanguages.target(activity)), true) {
                        GameLingoLanguages.choose(activity, GameLingoLanguages.Kind.TARGET) {
                            redraw()
                        }
                    }
                    body.addView(TextView(activity).apply {
                        text = "Tyto jazyky platí pro dialogy i herní texty. " +
                            "Změnit je můžeš později v Nastavení."
                        textSize = 14f
                        setTextColor(muted)
                        setPadding(px(4), px(8), px(4), px(12))
                    })
                }
                2 -> {
                    item("🎮  Xbox Remote Play", consoleChoice == 0) {
                        consoleChoice = 0
                        redraw()
                    }
                    item("🎮  PlayStation 5 / 4", consoleChoice == 1) {
                        consoleChoice = 1
                        redraw()
                    }
                    body.addView(TextView(activity).apply {
                        text = if (consoleRegistered())
                            "Vybraná konzole je uložená. Spojení ověříme při spuštění."
                        else "Po výběru můžeš konzoli připojit v posledním kroku."
                        textSize = 14f
                        setTextColor(muted)
                    })
                }
                3 -> {
                    item("📱  Na telefonu", tvChoice == 0) {
                        tvChoice = 0
                        redraw()
                    }
                    item("📺  Google TV / Android TV", tvChoice == 1) {
                        tvChoice = 1
                        redraw()
                    }
                    if (tvChoice == 1) {
                        item("🔗  Spárovat TV", false) {
                            finish(open = 3)
                        }
                    }
                }
                else -> {
                    item("🎮  Konzole: " + when (consoleChoice) {
                        0 -> "Xbox"
                        1 -> "PlayStation"
                        else -> "Nevybráno"
                    }, consoleRegistered()) { step = 2; redraw() }
                    item("📺  Titulky: " + when (tvChoice) {
                        0 -> "Telefon"
                        1 -> "Google TV"
                        else -> "Nevybráno"
                    }, televisionSaved()) { step = 3; redraw() }
                    item("🌐  " + GameLingoLanguages.name(
                        GameLingoLanguages.gameLanguages, GameLingoLanguages.game(activity)
                    ) + "  →  " + GameLingoLanguages.name(
                        GameLingoLanguages.targetLanguages,
                        GameLingoLanguages.target(activity)), true) {
                        step = 1
                        redraw()
                    }
                    body.addView(TextView(activity).apply {
                        text = "Uložené zařízení neznamená aktivní Remote Play. " +
                            "Živé spojení a překlad se ověří až při spuštění."
                        textSize = 14f
                        setTextColor(muted)
                        setPadding(px(6), px(7), px(6), px(14))
                    })
                    if (!consoleRegistered() && consoleChoice >= 0) {
                        item("🎮  Připojit konzoli", false) {
                            finish(open = if (consoleChoice == 0) 1 else 2)
                        }
                    }
                    if (tvChoice == 1 && !televisionSaved()) {
                        item("📺  Spárovat TV", false) { finish(open = 3) }
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
            val finalStep = step == 4
            val canContinue = when (step) {
                2 -> consoleChoice >= 0
                4 -> consoleRegistered() && televisionSaved()
                else -> true
            }
            footer.addView(button(if (finalStep) "Dokončit nastavení" else "Pokračovat") {
                if (step < 4) {
                    step++
                    redraw()
                } else {
                    // iOS uses a disabled completion button until setup is valid.
                    // Pairing actions above must not pretend completion succeeded.
                    if (canContinue) finish()
                }
            }.apply {
                isEnabled = canContinue
                alpha = if (canContinue) 1f else 0.48f
            }, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        redraw()
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
        }
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
