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
            setPadding(pad(if (tv) 18 else 16), pad(if (tv) 8 else 12), pad(if (tv) 18 else 16), pad(if (tv) 10 else 16))
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
        if (tv) page.addView(GameLingoVisualKit.wordmark(activity, compact = true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = pad(6) })
        else page.addView(GameLingoVisualKit.neonTitle(activity,
            "O aplikaci", "GameLingo – hraj a rozuměj svým hrám", "info.circle", helpTopic = if (showClose) null else "about"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = pad(15) })

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

        if (tv) {
            page.addView(label("GameLingo · $version", 19f, true))
            page.addView(label("Nezávislý překlad herních dialogů a textů pro Xbox, PlayStation a kompatibilní TV.", 14f))
        } else {
            // Equivalent to iOS About's emphasized GLNeonPanel.
            val brandCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GameLingoVisualKit.card(activity, selected = true)
                setPadding(pad(14), pad(22), pad(14), pad(22))
            }
            brandCard.addView(GameLingoVisualKit.wordmark(activity))
            brandCard.addView(label("Překlad herních dialogů a textů", 14f))
            brandCard.addView(label(tr("Verze:") + " " + version, 13f, true).apply {
                setTextColor(Color.parseColor(GameLingoUIContract.Palette.cyan))
            })
            page.addView(brandCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = pad(13) })
        }

        val licensePanel = if (tv) page else LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GameLingoVisualKit.card(activity)
            setPadding(pad(12), pad(12), pad(12), pad(12))
        }
        licensePanel.addView(label("Licence a původní autoři", 19f, true))
        licensePanel.addView(label(
            "GameLingo využívá open-source základy PlayCaption a Chiaki. Zdrojový kód a jeho úpravy jsou šířeny podle GNU AGPLv3 (AGPL-3.0). GameLingo není oficiální aplikací Sony, Microsoftu ani Google. Názvy Xbox a PlayStation jsou ochranné známky příslušných vlastníků.",
            14f
        ))
        if (!tv) page.addView(licensePanel, LinearLayout.LayoutParams(-1,-2).apply {
            bottomMargin = pad(12)
        })

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
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(link("Licence třetích stran na GitHubu",
                    "https://github.com/CaseyCZ/GameLingo/blob/Master/THIRD_PARTY_NOTICES.md"))
                val content = notices ?: tr("Licenční soubor není v tomto sestavení dostupný.")
                // Bound individual text layouts without truncating the bundled legal text.
                var start = 0
                while (start < content.length) {
                    var end = minOf(start + 1400, content.length)
                    if (end < content.length) {
                        val newline = content.lastIndexOf('\n', end - 1)
                        if (newline >= start) end = newline + 1
                        else if (Character.isHighSurrogate(content[end - 1])) end--
                    }
                    val block = content.substring(start, end)
                    addView(TextView(activity).apply {
                        text = block
                        textSize = GameLingoVisualKit.sp(activity, 14f)
                        setTextColor(Color.WHITE)
                        isFocusable = true
                        setTextIsSelectable(true)
                        setPadding(pad(12),pad(2),pad(12),pad(2))
                    })
                    start = end
                }
            }
            GameLingoTVUi.page(activity, tr("Licence třetích stran"), body, helpTopic = "about")
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
            page.addView(label("Offline překlad na telefonu využívá Google ML Kit. Jazykové modely se stahují přes Wi-Fi.", 14f))
            page.addView(link("Podmínky překladu Google ML Kit", "https://developers.google.com/ml-kit/language/translation/translation-terms"))
            page.addView(label("Zdrojové kódy", 19f, true))
            sources.forEach { page.addView(it, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=pad(9) }) }
        }

        page.addView(GameLingoVisualKit.detailRow(activity, "hand.raised.fill",
            tr("Soukromí"), tr("Jak GameLingo pracuje s obrazem a textem")) { showPrivacy(activity) })
        page.addView(link("Historie vydání", "https://github.com/CaseyCZ/GameLingo/releases"))
        return page
    }

    private const val PRIVACY_NOTICE = "OCR probíhá na zařízení. Online překlad může poslat rozpoznaný text a jazyky službám Google, Microsoft a MyMemory. Nesnímej hesla ani jiné citlivé údaje."

    private fun privacyText(activity: Activity, value: String): TextView =
        TextView(activity).apply {
            text = GameLingoLanguages.ui(activity, value)
            gravity = Gravity.CENTER
            textSize = GameLingoVisualKit.sp(activity, 15f)
            setTextColor(Color.parseColor("#92A4C7"))
            val padding = GameLingoVisualKit.dp(activity, 8)
            setPadding(padding, padding, padding, padding)
        }

    fun showPrivacy(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        for ((title, paragraph) in listOf(
            "OCR a rozpoznávání" to PRIVACY_NOTICE,
            "Překladová cache" to "Cache může ukládat původní i přeložený text. Lze ji vypnout a samostatně vymazat. Volitelná historie zůstává pouze v paměti aplikace.",
            "Výstup titulků" to "Text titulků na spárovanou TV se šifruje. Telefon i TV musí mít aktuální GameLingo. Starší volitelný režim přijímače není šifrovaný.",
            "Zařízení" to "Přihlášení ke konzolím využívá služby Microsoft a Sony. Připojené účty a zařízení spravuj v nabídce Zařízení.",
            "Soukromí" to "Snímání lze kdykoli zastavit. GameLingo nepřekládá mikrofon ani zvuk."
        )) {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = GameLingoVisualKit.card(activity)
                val p = GameLingoVisualKit.dp(activity, 12)
                setPadding(p, p, p, p)
                addView(GameLingoVisualKit.heading(activity, tr(title)), LinearLayout.LayoutParams(-1, -2))
                addView(privacyText(activity, paragraph), LinearLayout.LayoutParams(-1, -2))
            }
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = GameLingoVisualKit.dp(activity, 12)
            })
        }
        body.addView(GameLingoTVUi.action(activity, tr("Zásady ochrany osobních údajů"), primary = true) {
            val url = "https://github.com/CaseyCZ/GameLingo/blob/Master/PRIVACY.md"
            try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: android.content.ActivityNotFoundException) {
                val fallback = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(TextView(activity).apply {
                        text = url
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setTextIsSelectable(true)
                    }, LinearLayout.LayoutParams(-1, -2))
                }
                GameLingoTVUi.page(activity, tr("Zásady ochrany osobních údajů"), fallback, helpTopic = "privacy")
            }
        }, LinearLayout.LayoutParams(-1, -2))
        GameLingoTVUi.page(activity, tr("Soukromí"), body, helpTopic = "privacy")
    }

    /** Affirmative action before user-initiated capture, not a legal consent record. */
    fun confirmTranslationPrivacy(activity: Activity, onCancel: () -> Unit = {}, onStart: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        fun tr(cs: String) = GameLingoLanguages.ui(activity, cs)
        fun dp(value: Int) = GameLingoVisualKit.dp(activity, value)
        val dialog = Dialog(activity)
        var accepted = false
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GameLingoVisualKit.card(activity, selected = true)
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        val back = GameLingoTVUi.action(activity, tr("Zpět")) { dialog.dismiss() }
        val help = GameLingoHelp.button(activity, "privacy")
        body.addView(GameLingoTVUi.header(activity,
            GameLingoVisualKit.heading(activity, tr("Před spuštěním překladu")), back, help))
        val copy = privacyText(activity, PRIVACY_NOTICE)
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(copy, ViewGroup.LayoutParams(-1, -2))
        }
        body.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        val start = GameLingoTVUi.action(activity, tr("Spustit překlad"), primary = true) {
            accepted = true
            dialog.dismiss()
        }
        val cancel = GameLingoTVUi.action(activity, tr("Zrušit")) { dialog.dismiss() }
        body.addView(start, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        body.addView(cancel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        listOf(back, help, start, cancel).forEach { it.id = android.view.View.generateViewId() }
        back.nextFocusRightId = help.id
        help.nextFocusLeftId = back.id
        back.nextFocusDownId = start.id
        help.nextFocusDownId = start.id
        start.nextFocusUpId = back.id
        start.nextFocusDownId = cancel.id
        cancel.nextFocusUpId = start.id
        dialog.setContentView(body)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener {
            if (!accepted) onCancel()
            else if (!activity.isFinishing && !activity.isDestroyed) onStart()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val screen = activity.resources.displayMetrics
        val width = if (GameLingoTVUi.isTV(activity)) (screen.widthPixels * .66f).toInt()
            else minOf((screen.widthPixels - dp(32)).coerceAtLeast(1), dp(560))
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        scroll.post {
            val maxHeight = (screen.heightPixels * .40f).toInt().coerceAtLeast(dp(48))
            if (scroll.height > maxHeight) scroll.layoutParams = LinearLayout.LayoutParams(-1, maxHeight)
        }
        GameLingoTVUi.applyMenuFocus(activity, body)
        cancel.requestFocus()
    }

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        GameLingoTVUi.page(activity, GameLingoLanguages.ui(activity, "O aplikaci / Licence"), content(activity), helpTopic = "about")
    }
}
