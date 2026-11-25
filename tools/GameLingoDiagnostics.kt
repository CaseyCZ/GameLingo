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
 * Android counterpart of iPhone GameLingo Diagnostics.
 * Reads existing application state only; never claims a verified Remote Play
 * frame, PSN login or paired TV from a saved local profile.
 */
object GameLingoDiagnostics {
    fun show(activity: Activity) {
        if (activity.isFinishing) return
        val d: (Int) -> Int = { GameLingoVisualKit.dp(activity, it) }
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(22), d(20), d(26))
            background = GameLingoVisualKit.pageBackground(activity)
        }
        val heading = TextView(activity).apply {
            text = "Diagnostika"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        root.addView(LinearLayout(activity).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(heading,LinearLayout.LayoutParams(0,-2,1f))
            addView(GameLingoHelp.button(activity,"diagnostics"),GameLingoHelp.layout(activity,d(36),d(44)))
        })
        root.addView(TextView(activity).apply {
            text = "Stav místních nastavení a zahájené relace. " +
                "Uložené údaje nepotvrzují živé spojení s konzolí ani TV."
            textSize = 14f
            setTextColor(Color.parseColor("#92A4C7"))
            setPadding(0, d(10), 0, d(18))
        })

        val states = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        states.background=GameLingoVisualKit.card(activity)
        states.setPadding(d(13),d(13),d(13),d(13))
        root.addView(GameLingoVisualKit.heading(activity,"Stav"))
        root.addView(states)
        val last=TextView(activity).apply {
            setTextColor(Color.WHITE);textSize=16f;background=GameLingoVisualKit.card(activity)
            setPadding(d(13),d(13),d(13),d(13))
        }
        root.addView(GameLingoVisualKit.heading(activity,"Poslední překlad"))
        root.addView(last)
        fun value(title: String, actual: String) {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = GameLingoVisualKit.card(activity)
                setPadding(d(16), d(13), d(16), d(13))
            }
            card.addView(TextView(activity).apply {
                text = title
                textSize = 13f
                setTextColor(Color.parseColor("#92A4C7"))
            })
            card.addView(TextView(activity).apply {
                text = actual
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, d(4), 0, 0)
            })
            states.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = d(9) })
        }
        fun refresh() {
            states.removeAllViews()
            val item=GameLingoTranslationHistory.snapshot().firstOrNull()
            last.text=if(item==null)"Zatím žádný překlad ve volitelné historii." else item.original+"\n\n"+item.translated
            val tvStore = TvProfily(activity)
            val savedTv = tvStore.posledniId?.let { tvStore.najdi(it) }
            val xboxLogged = XboxUcet.prihlasen(activity)
            val selectedXbox = activity.getSharedPreferences(
                "gamelingo_devices", Activity.MODE_PRIVATE
            ).getString("selectedXboxId", null)
            value("Překladová fronta", GameLingoNetworkPerformance.summary())
            value("Xbox – skutečný obraz", GameLingoXboxDiagnostics.summary())
            value("Remote Play", if (RemotePlayService.aktivni)
                "Relace spuštěna – snímky se ověřují zvlášť" else "Relace není spuštěna")
            value("Xbox – účet", if (xboxLogged) "Přihlášený" else "Nepřihlášený")
            value("Xbox – volba konzole", if (selectedXbox.isNullOrBlank())
                "Žádná vybraná" else "ID konzole uloženo")
            val consoles = GameLingoPlayStationConsoles.list(activity)
            value("PlayStation", "${consoles.size} uložených · " +
                if (consoles.any { it.selected }) "jedna vybraná" else "žádná vybraná")
            value("Výstup titulků", if (tvStore.pouzeTelefon)
                "Telefon" else savedTv?.jmeno ?: "Televize nevybrána")
            value("Párování TV", when {
                savedTv == null -> "Bez uloženého profilu"
                !GameLingoTVPairing.paired(activity, savedTv.ip) -> "Nespárováno"
                GameLingoTVPairing.connected(savedTv.ip) -> "Ověřené spojení"
                else -> "Spárováno – bez čerstvého potvrzení"
            })
            value("Jazyk hry → překlad",
                GameLingoLanguages.name(
                    GameLingoLanguages.gameLanguages,
                    GameLingoLanguages.game(activity)
                ) + " → " + GameLingoLanguages.name(
                    GameLingoLanguages.targetLanguages,
                    GameLingoLanguages.target(activity)))
        }
        refresh()
        fun action(title: String, onClick: () -> Unit) {
            root.addView(TextView(activity).apply {
                text = title
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minHeight = d(54)
                setTextColor(Color.WHITE)
                background = GameLingoVisualKit.card(activity)
                setOnClickListener { onClick() }
                isFocusable = true
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, d(54)
            ).apply { bottomMargin = d(10) })
        }
        action("Aktualizovat stav") { refresh() }
        var verifying = false
        action("Ověřit spojení s TV") {
            if (!verifying) {
                val store = TvProfily(activity)
                val selected = store.posledniId?.let { store.najdi(it) }
                if (selected == null || !GameLingoTVPairing.paired(activity, selected.ip)) {
                    android.widget.Toast.makeText(activity, "Nejdříve vyber a spáruj TV.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    verifying = true
                    GameLingoTVPairing.verify(activity, selected) { ok ->
                        verifying = false
                        if (dialog.isShowing) {
                            refresh()
                            android.widget.Toast.makeText(activity, if (ok) "Spojení ověřeno." else "TV neodpovídá. Zkontroluj domácí síť a přijímač.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        action("Vymazat místní log překladu") {
            if (RemotePlayService.aktivni || PrijemService.bezi) {
                android.app.AlertDialog.Builder(activity).setMessage("Nejdříve zastav překlad a příjem.")
                    .setPositiveButton("OK", null).show()
            } else android.app.AlertDialog.Builder(activity)
                .setMessage("Vymazat starý i nový místní log překladu? Ostatní diagnostické soubory zůstanou.")
                .setNegativeButton("Zrušit", null)
                .setPositiveButton("Vymazat") { _, _ ->
                    if (RemotePlayService.aktivni || PrijemService.bezi) return@setPositiveButton
                    val removed = listOf("titulky.log", "titulky-private-v2.log").map { name ->
                        val file = java.io.File(activity.filesDir, name)
                        !file.exists() || file.delete()
                    }.all { it }
                    android.app.AlertDialog.Builder(activity)
                        .setMessage(if (removed) "Místní log překladu vymazán." else "Některý log se nepodařilo vymazat.")
                        .setPositiveButton("OK", null).show()
                }.show()
        }
        action("Hotovo") { dialog.dismiss() }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(root)
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
