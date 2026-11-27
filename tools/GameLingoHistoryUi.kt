package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch
import android.widget.Button
import java.text.DateFormat
import java.util.Date

internal object GameLingoHistoryUi {
    fun show(a: Activity) {
        if (a.isFinishing) return
        fun tr(cs: String) = GameLingoLanguages.ui(a, cs)
        fun dp(v: Int) = if (GameLingoTVUi.isTV(a)) GameLingoTVUi.dp(a,v) else v * GameLingoTVUi.dp(a,1)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(TextView(a).apply {
            gravity = android.view.Gravity.CENTER
            text = tr("Pouze v paměti do ukončení aplikace · max. 200 položek. Vypnutí historii vymaže. Technický log a cache jsou oddělené.")
            textSize = GameLingoVisualKit.sp(a, 15f)
        })
        val toggle = Switch(a).apply {
            text = tr("Uchovávat historii")
            if (GameLingoTVUi.isTV(a)) textSize = GameLingoVisualKit.sp(a, 16f)
            isChecked = GameLingoTranslationHistory.enabled()
        }
        root.addView(toggle)
        val controls = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val refresh = Button(a).apply { text = tr("Obnovit"); if (GameLingoTVUi.isTV(a)) textSize = GameLingoVisualKit.sp(a, 16f) }
        val clear = Button(a).apply { text = tr("Vymazat"); if (GameLingoTVUi.isTV(a)) textSize = GameLingoVisualKit.sp(a, 16f) }
        controls.addView(refresh, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(clear, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(controls)
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(a).apply {
            isFocusable = true
            isFillViewport = true
            addView(list)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun update() {
            list.removeAllViews()
            val items = GameLingoTranslationHistory.snapshot()
            clear.isEnabled = items.isNotEmpty()
            if (items.isEmpty()) list.addView(TextView(a).apply {
                text = if (GameLingoTranslationHistory.enabled()) tr("Zatím žádné překlady.") else tr("Historie je vypnutá.")
                textSize = GameLingoVisualKit.sp(a, 17f)
            })
            val format = DateFormat.getTimeInstance(DateFormat.MEDIUM)
            for (entry in items) {
                list.addView(TextView(a).apply {
                    text = entry.kind + " · " + entry.source + " → " + entry.target + " · " +
                        format.format(Date(entry.time)) + "\n\n" + entry.translated + "\n\n" + entry.original
                    textSize = GameLingoVisualKit.sp(a, 17f)
                    setPadding(dp(8), dp(12), dp(8), dp(16))
                    isFocusable = true
                    setTextIsSelectable(true)
                })
            }
        }
        toggle.setOnCheckedChangeListener { _, checked ->
            GameLingoTranslationHistory.setEnabled(checked)
            update()
        }
        refresh.setOnClickListener { update() }
        clear.setOnClickListener {
            AlertDialog.Builder(a).setTitle(tr("Vymazat celou historii?"))
                .setPositiveButton(tr("Vymazat")) { _, _ -> GameLingoTranslationHistory.clear(); update() }
                .setNegativeButton(tr("Zrušit"), null).show()
        }
        update()
        GameLingoTVUi.page(a,tr("Historie překladů"),root,scroll=false,helpTopic="history")
        toggle.requestFocus()
    }
}
