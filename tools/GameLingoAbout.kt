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
        page.addView(GameLingoVisualKit.wordmark(activity),
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

        fun link(title: String, url: String) {
            val row = GameLingoVisualKit.deviceCard(activity, "TV",
                tr(title), tr("Otevřít odkaz"), false) {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            page.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = pad(9) })
        }
        page.addView(label("Licenční texty v aplikaci", 19f, true))
        page.addView(GameLingoVisualKit.deviceCard(activity, "TV",
            tr("Licence třetích stran"), tr("Číst bez internetu"), false) {
            val notices = runCatching {
                activity.assets.open("GameLingoNotices.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()
            android.app.AlertDialog.Builder(activity)
                .setTitle(tr("Licence třetích stran"))
                .setMessage(notices ?: tr("Licenční soubor není v tomto sestavení dostupný."))
                .setPositiveButton(tr("Zavřít"), null).show()
        })
        page.addView(label("Zdrojové kódy", 19f, true))
        link("GameLingo na GitHubu", "https://github.com/CaseyCZ/GameLingo")
        link("Původní PlayCaption", "https://github.com/AdamRuzick4/PlayCaption-app")
        link("Chiaki / TP Play", "https://github.com/mB14ck22/tp-play")

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
