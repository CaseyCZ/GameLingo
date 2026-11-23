package cz.titulky.tv

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Actual Android GameLingo visual components, matching the native SwiftUI
 * design language. Transport, OCR and console pairing stay in their own code.
 */
object GameLingoVisualKit {
    private val cyan = Color.parseColor(GameLingoUIContract.Palette.cyan)
    private val blue = Color.parseColor(GameLingoUIContract.Palette.blue)
    private val purple = Color.parseColor(GameLingoUIContract.Palette.purple)
    private val ink = Color.parseColor(GameLingoUIContract.Palette.background)
    private val panel = Color.parseColor(GameLingoUIContract.Palette.panel)
    private val muted = Color.parseColor(GameLingoUIContract.Palette.muted)

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
        fun caption(label: String, size: Float, color: Int,
                    strong: Boolean = false) = TextView(activity).apply {
            text = label
            textSize = size
            setTextColor(color)
            if (strong) setTypeface(null, Typeface.BOLD)
        }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 17), dp(activity, 16),
                dp(activity, 17), dp(activity, 17))
            background = card(activity, selected = true)
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption("HRAJ. ROZUMĚJ.", 11f, cyan, true).apply {
                letterSpacing = 0.14f
            })
            addView(caption("Překlad přímo při hraní", 18f, Color.WHITE, true).apply {
                setPadding(0, dp(activity, 5), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(ImageView(activity).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            adjustViewBounds = true
            contentDescription = "GameLingo"
        }, LinearLayout.LayoutParams(dp(activity, 51), dp(activity, 51)))
        panel.addView(top)

        // Same two platform badges as GameLingoHome.swift on iPhone.
        val consoles = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 14), 0, 0)
        }
        consoles.addView(platformEmblem(activity, "Xbox"),
            LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)).apply {
                rightMargin = dp(activity, 8)
            })
        consoles.addView(caption("Xbox", 14f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        consoles.addView(TextView(activity).apply {
            text = "│"
            textSize = 24f
            setTextColor(Color.parseColor("#554078"))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(activity, 25), dp(activity, 44)))
        consoles.addView(platformEmblem(activity, "PlayStation"),
            LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)).apply {
                rightMargin = dp(activity, 8)
            })
        consoles.addView(caption("PlayStation", 14f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(consoles)
        return panel
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
    fun brandHeader(activity: Activity, onSettings: () -> Unit): LinearLayout {
        val px = dp(activity, 13)
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 5), 0, dp(activity, 8))
        }
        bar.addView(ImageView(activity).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            adjustViewBounds = true
            contentDescription = "GameLingo"
        }, LinearLayout.LayoutParams(dp(activity, 49), dp(activity, 49)).apply {
            rightMargin = dp(activity, 9)
        })
        bar.addView(TextView(activity).apply {
            val brand = android.text.SpannableString("GameLingo")
            brand.setSpan(android.text.style.ForegroundColorSpan(purple),
                4, 9, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = brand
            textSize = 27f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(TextView(activity).apply {
            text = "⚙"
            contentDescription = "Nastavení"
            gravity = Gravity.CENTER
            textSize = 26f
            setTextColor(Color.WHITE)
            background = card(activity)
            isFocusable = true
            isClickable = true
            setOnClickListener { onSettings() }
        }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)))
        return bar
    }

    fun platformEmblem(activity: Activity, family: String): View {
        val xbox = family.equals("Xbox", ignoreCase = true)
        val playStation = family.equals("PlayStation", ignoreCase = true)
        val from = if (xbox) Color.parseColor("#1B7938")
                   else if (playStation) Color.parseColor("#285ACA") else cyan
        val to = if (xbox) Color.parseColor("#15283A")
                 else if (playStation) Color.parseColor("#1F1445") else blue
        return FrameLayout(activity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(from, to)).apply {
                cornerRadius = dp(activity, 14).toFloat()
            }
            addView(ImageView(activity).apply {
                setImageResource(when {
                    xbox -> R.drawable.gamelingo_xbox
                    playStation -> R.drawable.gamelingo_playstation
                    else -> android.R.drawable.ic_menu_slideshow
                })
                setColorFilter(Color.WHITE)
                contentDescription = family
                adjustViewBounds = true
            }, FrameLayout.LayoutParams(dp(activity, 31),
                dp(activity, 31), Gravity.CENTER))
        }
    }

    fun deviceCard(activity: Activity, family: String, title: String,
                   subtitle: String, selected: Boolean,
                   onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = card(activity, selected)
            setPadding(dp(activity, 13), dp(activity, 13),
                dp(activity, 13), dp(activity, 13))
            minimumHeight = dp(activity, 77)
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(platformEmblem(activity, family),
            LinearLayout.LayoutParams(dp(activity, 53), dp(activity, 53)).apply {
                rightMargin = dp(activity, 12)
            })
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = title
                textSize = 17f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
            })
            addView(TextView(activity).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(if (selected) Color.parseColor("#3BE781") else muted)
                maxLines = 2
                setPadding(0, dp(activity, 4), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(activity).apply {
            text = "›"
            textSize = 27f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(activity, 25),
            ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    fun section(activity: Activity, name: String): TextView = heading(activity, name)

    fun status(activity: Activity, title: String, message: String,
               ready: Boolean): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = card(activity, selected = ready)
            setPadding(dp(activity, 14), dp(activity, 14),
                dp(activity, 14), dp(activity, 14))
        }
        row.addView(TextView(activity).apply {
            text = if (ready) "✓" else "!"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(if (ready) Color.parseColor("#3BE781") else muted)
        }, LinearLayout.LayoutParams(dp(activity, 39), dp(activity, 39)))
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = title
                textSize = 15.5f
                setTextColor(if (ready) Color.parseColor("#3BE781") else Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = message
                textSize = 12f
                setTextColor(muted)
                setPadding(0, dp(activity, 3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    fun pageBackground(activity: Activity): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(ink, Color.parseColor("#0D1733"),
                       Color.parseColor("#211047")))

    fun navigationTile(activity: Activity, symbol: String, title: String,
                       onClick: () -> Unit): LinearLayout {
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = card(activity)
            minimumHeight = dp(activity, 99)
            isFocusable = true
            isClickable = true
            setPadding(dp(activity, 7), dp(activity, 11),
                dp(activity, 7), dp(activity, 11))
            setOnClickListener { onClick() }
        }
        item.addView(TextView(activity).apply {
            text = symbol
            textSize = 27f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        })
        item.addView(TextView(activity).apply {
            text = title
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(activity, 6), 0, 0)
        })
        return item
    }

    fun cta(activity: Activity, title: String,
            onClick: () -> Unit): TextView = TextView(activity).apply {
        text = "▶   " + title
        textSize = 20f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = primary(activity)
        minHeight = dp(activity, 66)
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
    }

}
