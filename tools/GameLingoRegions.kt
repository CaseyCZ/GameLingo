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
                onDismiss: () -> Unit = {}, showClose: Boolean = false, showHeader: Boolean = true): LinearLayout {
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
        val d: (Int) -> Int = { GameLingoVisualKit.dp(activity, it) }
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(12), d(16), d(16))
            background = GameLingoVisualKit.pageBackground(activity)
        }
        if (showHeader) page.addView(GameLingoVisualKit.neonTitle(activity,
            "Oblasti obrazovky",
            if (GameLingoAutomaticConfig.enabled(activity))
                "Automatické oblasti překladu"
            else "Čtyři nezávislé oblasti pro rozpoznávání a překlad",
            "viewfinder"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = d(15) })
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
                if (GameLingoAutomaticConfig.enabled(activity)) GameLingoAutomaticConfig.showZones(activity)
                else onEdit()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, d(58)
        ).apply { topMargin = d(8); bottomMargin = d(12) })
        val areas = if (GameLingoAutomaticConfig.enabled(activity)) {
            GameLingoAutomaticConfig.zones(activity).map { zone ->
                Triple(zone.name, if (zone.enabled) "Aktivní" else "Vypnutá",
                    Color.parseColor(if (zone.enabled) "#35E57D" else "#92A4C7"))
            }
        } else listOf(
            Triple("Modrá", "Čtení dialogů", Color.parseColor("#3557FF")),
            Triple("Zelená", "Výstup přeložených titulků", Color.parseColor("#3CFF6E")),
            Triple("Žlutá", "Čtení herních textů a voleb", Color.parseColor("#FFD54A")),
            Triple("Oranžová", "Překlad herních textů", Color.parseColor("#FF9E34"))
        )
        for ((name, detail, color) in areas) {
            val line = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = GameLingoVisualKit.card(activity)
                setPadding(d(14), d(15), d(14), d(15))
            }
            // Same slim color marker as iOS region rows, not unrelated icons.
            line.addView(android.view.View(activity).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = d(5).toFloat()
                }
            }, LinearLayout.LayoutParams(d(9), d(34)).apply { rightMargin = d(11) })
            line.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(TextView(activity).apply {
                    gravity = Gravity.CENTER
                    text = tr(name)
                    textSize = GameLingoVisualKit.sp(activity, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(activity).apply {
                    gravity = Gravity.CENTER
                    text = tr(detail)
                    textSize = GameLingoVisualKit.sp(activity, 13f)
                    setTextColor(Color.parseColor("#92A4C7"))
                    setPadding(0, d(4), 0, 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(android.view.View(activity), LinearLayout.LayoutParams(d(20), 1))
            page.addView(line, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = d(11) })
        }
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
            onDismiss = { dialog.dismiss() }, showClose = false, showHeader = false)
        GameLingoTVUi.mountPage(activity, dialog, GameLingoLanguages.ui(activity, "Oblasti obrazovky"), page, helpTopic = "regions")
    }
}
