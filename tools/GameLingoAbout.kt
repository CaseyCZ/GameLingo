package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared GameLingo mobile About destination, reachable from the fifth tab. */
object GameLingoAbout {
    fun show(activity: Activity) {
        if (activity.isFinishing) return

        fun label(text: String, size: Float, bold: Boolean = false): TextView =
            TextView(activity).apply {
                this.text = text
                textSize = size
                setTextColor(Color.WHITE)
                if (bold) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 8, 0, 8)
            }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 23, 28, 26)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#070B14"))
                cornerRadius = 20f
            }
        }
        root.addView(label("GameLingo", 29f, true))
        root.addView(label("Hraj. Rozuměj.", 16f))
        val installedVersion = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty()
        root.addView(label(
            "Verze: " + (installedVersion.ifEmpty { "testovací sestavení" }),
            14f
        ).apply { setTextColor(Color.parseColor("#94A3B8")) })
        root.addView(label("Konzole: Xbox a PlayStation", 17f, true))
        root.addView(label(
            "Český překlad herních dialogů, nabídek a dalších textů. " +
            "Výstup lze nastavit na telefon nebo kompatibilní Google TV / Android TV.",
            15f
        ))
        root.addView(label("Soukromí a licence", 17f, true))
        root.addView(label(
            "Údaje účtů spravují přihlašovací komponenty dané platformy. " +
            "Před vydáním se ověřuje reálné párování, streamování, OCR a TV překryv.\n\n" +
            "GameLingo je nezávislý open-source projekt. Vychází z projektu PlayCaption " +
            "a zachovává původní autorství a podmínky licence AGPL-3.0. " +
            "Není oficiální aplikací Sony, Microsoftu ani Google.",
            14f
        ))
        val scroll = ScrollView(activity).apply { addView(root) }
        AlertDialog.Builder(activity)
            .setView(scroll)
            .setNeutralButton("Zdrojový kód") { _, _ ->
                val url = Uri.parse("https://github.com/CaseyCZ/GameLingo")
                activity.startActivity(Intent(Intent.ACTION_VIEW, url))
            }
            .setPositiveButton("Zavřít", null)
            .show()
            .window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.93f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
    }
}
