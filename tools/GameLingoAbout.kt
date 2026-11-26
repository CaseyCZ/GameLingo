package cz.titulky.tv

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Same native brand, structure and open-source attribution as iPhone. */
object GameLingoAbout {
    /** Same About/Licence content on the tab and in the standalone TV dialog. */
    fun content(activity: Activity, onDismiss: () -> Unit = {},
                showClose: Boolean = false): LinearLayout {
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
        val tv = GameLingoTVUi.isTV(activity)
        val d = GameLingoVisualKit.dp(activity, 1)
        fun pad(v: Int) = if (tv) GameLingoVisualKit.dp(activity, v) else v*d
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(18), pad(if (tv) 8 else 17), pad(18), pad(if (tv) 10 else 30))
            if (!tv) background = GameLingoVisualKit.pageBackground(activity)
        }
        val close = TextView(activity).apply {
            text = "✕   " + tr("Zavřít")
            textSize = GameLingoVisualKit.sp(activity, 15f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onDismiss() }
            setPadding(0, pad(6), 0, pad(14))
        }
        if (showClose) page.addView(close)
        page.addView(GameLingoVisualKit.wordmark(activity, compact = tv),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = pad(if (tv) 6 else 17) })

        fun label(value: String, size: Float, strong: Boolean = false): TextView =
            TextView(activity).apply {
                text = tr(value)
                gravity = android.view.Gravity.CENTER
                textSize = GameLingoVisualKit.sp(activity, size)
                setTextColor(if (strong) Color.WHITE else Color.parseColor("#92A4C7"))
                if (strong) setTypeface(null, Typeface.BOLD)
                setPadding(pad(4), pad(if (tv) 3 else 7), pad(4), pad(if (tv) 3 else 7))
            }

        val version = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty().ifEmpty { tr("testovací sestavení") }

        page.addView(label("GameLingo · $version", 19f, true))
        page.addView(label("Nezávislý překlad herních dialogů a textů pro Xbox, PlayStation a kompatibilní TV.", 14f))

        page.addView(label("Licence a původní autoři", 19f, true))
        page.addView(label(
            "GameLingo využívá open-source základy PlayCaption a Chiaki. Zdrojový kód a jeho úpravy jsou šířeny podle GNU AGPLv3 (AGPL-3.0). GameLingo není oficiální aplikací Sony, Microsoftu ani Google. Názvy Xbox a PlayStation jsou ochranné známky příslušných vlastníků.",
            14f
        ))

        fun link(title: String, url: String): LinearLayout =
            GameLingoVisualKit.detailRow(activity, "globe", tr(title), tr("Otevřít odkaz")) {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: android.content.ActivityNotFoundException) {
                    android.app.AlertDialog.Builder(activity).setMessage(url)
                        .setPositiveButton(tr("Zavřít"), null).show()
                }
            }
        val license = GameLingoVisualKit.detailRow(activity, "info.circle",
            tr("Licence třetích stran"), tr("Číst bez internetu")) {
            val notices = runCatching {
                activity.assets.open("GameLingoNotices.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()
            if (tv) {
                val body = TextView(activity).apply {
                    text = notices ?: tr("Licenční soubor není v tomto sestavení dostupný.")
                    textSize = GameLingoVisualKit.sp(activity, 16f)
                    setTextColor(Color.WHITE)
                    isFocusable = true
                    setTextIsSelectable(true)
                    setPadding(pad(12),pad(8),pad(12),pad(8))
                }
                GameLingoTVUi.page(activity, tr("Licence třetích stran"), body)
            } else android.app.AlertDialog.Builder(activity)
                .setTitle(tr("Licence třetích stran"))
                .setMessage(notices ?: tr("Licenční soubor není v tomto sestavení dostupný."))
                .setPositiveButton(tr("Zavřít"), null).show()
        }
        val sources = listOf(
            link("GameLingo na GitHubu", "https://github.com/CaseyCZ/GameLingo"),
            link("Původní PlayCaption", "https://github.com/AdamRuzick4/PlayCaption-app"),
            link("Chiaki / TP Play", "https://github.com/mB14ck22/tp-play"))
        if (tv) {
            // Four compact cards, two columns. Only the actual long license text scrolls.
            (listOf(license) + sources).chunked(2).forEach { pair ->
                val row = LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
                pair.forEachIndexed { i, card ->
                    row.addView(card, LinearLayout.LayoutParams(0,-2,1f).apply {
                        if (i == 0) marginEnd = pad(12)
                    })
                }
                page.addView(row, LinearLayout.LayoutParams(-1,-2).apply { topMargin=pad(8) })
            }
        } else {
            page.addView(label("Licenční texty v aplikaci", 19f, true))
            page.addView(license)
            page.addView(label("Zdrojové kódy", 19f, true))
            sources.forEach { page.addView(it, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=pad(9) }) }
        }

        return page
    }

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (GameLingoTVUi.isTV(activity)) {
            GameLingoTVUi.page(activity, GameLingoLanguages.ui(activity, "O aplikaci / Licence"), content(activity))
            return
        }
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val page = content(activity, onDismiss = { dialog.dismiss() }, showClose = true)
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
