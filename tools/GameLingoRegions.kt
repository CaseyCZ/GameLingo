package cz.titulky.tv

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Android counterpart of the iPhone "Obrazovka" tab. This is a native
 * overview of the same four functional OCR/output regions; editing still
 * opens the working TelefonActivity mini-TV layout, without fake coordinates.
 */
object GameLingoRegions {
    fun content(activity: Activity, onEdit: () -> Unit,
                onDismiss: () -> Unit = {}, showClose: Boolean = false): LinearLayout {
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
        val d: (Int) -> Int = { GameLingoVisualKit.dp(activity, it) }
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(20), d(20), d(26))
            background = GameLingoVisualKit.pageBackground(activity)
        }
        page.addView(TextView(activity).apply {
            gravity = android.view.Gravity.CENTER
            text = tr("Oblasti obrazovky")
            textSize = GameLingoVisualKit.sp(activity, 26f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        page.addView(TextView(activity).apply {
            gravity = android.view.Gravity.CENTER
            text = tr("Čtyři nezávislé oblasti pro rozpoznávání a překlad.")
            textSize = GameLingoVisualKit.sp(activity, 14f)
            setTextColor(Color.parseColor("#92A4C7"))
            setPadding(0, d(10), 0, d(20))
        })
        val areas = listOf(
            Triple("Modrá", "Čtení dialogových titulků", Color.parseColor("#3557FF")),
            Triple("Zelená", "Zobrazení přeložených titulků", Color.parseColor("#3CFF6E")),
            Triple("Žlutá", "Čtení herních textů a voleb", Color.parseColor("#FFD54A")),
            Triple("Oranžová", "Překlad herních textů", Color.parseColor("#FF9E34"))
        )
        for ((name, detail, color) in areas) {
            val line = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = GameLingoVisualKit.card(activity)
                setPadding(d(14), d(15), d(14), d(15))
            }
            val symbol=when(name) {"Modrá"->"viewfinder";"Zelená"->"text.bubble";"Žlutá"->"rectangle.dashed";else->"character.book.closed.fill"}
            line.addView(GameLingoVisualKit.icon(activity,symbol,color),LinearLayout.LayoutParams(d(38),d(38)).apply{rightMargin=d(13)})
            line.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    gravity = android.view.Gravity.CENTER
                    text = tr(name)
                    textSize = GameLingoVisualKit.sp(activity, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                })
                addView(TextView(activity).apply {
                    gravity = android.view.Gravity.CENTER
                    text = tr(detail)
                    textSize = GameLingoVisualKit.sp(activity, 13f)
                    setTextColor(Color.parseColor("#92A4C7"))
                    setPadding(0, d(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(android.view.View(activity), LinearLayout.LayoutParams(d(51), 1))
            page.addView(line, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = d(11) })
        }
        page.addView(TextView(activity).apply {
            gravity = android.view.Gravity.CENTER
            text = tr("Otevřít editor oblastí")
            gravity = Gravity.CENTER
            textSize = GameLingoVisualKit.sp(activity, 17f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GameLingoVisualKit.primary(activity)
            minHeight = d(58)
            isFocusable = true
            setOnClickListener {
                onDismiss()
                onEdit()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, d(58)
        ).apply { topMargin = d(8); bottomMargin = d(12) })
        if (showClose) page.addView(TextView(activity).apply {
            gravity = android.view.Gravity.CENTER
            text = tr("Zpět")
            gravity = Gravity.CENTER
            textSize = GameLingoVisualKit.sp(activity, 16f)
            setTextColor(Color.WHITE)
            background = GameLingoVisualKit.card(activity)
            minHeight = d(52)
            setOnClickListener { onDismiss() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, d(52)
        ))
        return page
    }

    fun show(activity: Activity, onEdit: () -> Unit) {
        if (activity.isFinishing) return
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val page = content(activity, onEdit,
            onDismiss = { dialog.dismiss() }, showClose = true)
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(page)
        }
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
