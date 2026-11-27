package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Button

internal object GameLingoGlossaryUi {
    private fun stopped(a: Activity): Boolean {
        if (!RemotePlayService.aktivni && !PrijemService.bezi) return true
        AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a, "Nejdřív zastav probíhající překlad."))
            .setPositiveButton("OK", null).show()
        return false
    }
    fun edit(a: Activity, name: String, onSaved: () -> Unit = {}) {
        if (!stopped(a)) return
        val saved = GameLingoGlossaryStore.read(a, name)
        var source = saved?.source ?: GameLingoLanguages.game(a)
        var target = saved?.target ?: GameLingoLanguages.target(a)
        val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 10, 20, 10) }
        val pair = TextView(a).apply { gravity = android.view.Gravity.CENTER; text = GameLingoLanguages.ui(a, "Jazyky slovníku") + ": $source → $target" }
        body.addView(pair)
        body.addView(TextView(a).apply {
            gravity = android.view.Gravity.CENTER
            text = GameLingoLanguages.ui(a, "Max. 50 řádků: originál | chybný překlad | správný překlad. Prázdný prostředek opraví jen celý přesný originál. Rozlišuje velikost písmen.")
        })
        val input = EditText(a).apply {
            setText(saved?.text.orEmpty())
            hint = GameLingoLanguages.ui(a, "Originál | chybný překlad | správný překlad")
            minLines = 4
            maxLines = 9
            setSingleLine(false)
        }
        body.addView(input)
        body.addView(TextView(a).apply {
            text = GameLingoLanguages.ui(a, "Příklad pravidla (angličtina → čeština)") + "\n" +
                "Save game | Zachránit hru | Uložit hru"
            gravity = android.view.Gravity.CENTER
        })
        body.addView(Button(a).apply {
            text = GameLingoLanguages.ui(a, "Nový slovník pro aktuální jazyky")
            setOnClickListener {
                source = GameLingoLanguages.game(a); target = GameLingoLanguages.target(a)
                pair.text = GameLingoLanguages.ui(a, "Jazyky slovníku") + ": $source → $target"
                input.setText("")
            }
        })
        val error = TextView(a)
        body.addView(error)
        run {
            var page:android.app.Dialog?=null
            GameLingoTVUi.controlFocus(a,input)
            body.addView(GameLingoTVUi.action(a,GameLingoLanguages.ui(a, "Uložit"),primary=true) {
                if(stopped(a)) {
                    if(!GameLingoGlossaryStore.save(a,name,source,target,input.text.toString()))
                        error.text=GameLingoLanguages.ui(a, "Neplatný formát, duplicita originálu nebo překročený limit.")
                    else {page?.dismiss();onSaved()}
                }
            })
            page=GameLingoTVUi.page(a,GameLingoLanguages.ui(a, "Herní slovník") + " · " + name,body,helpTopic="glossary")
        }
    }
    // TV does not have the mobile geometry-profile screen; choose local named
    // dictionary contexts explicitly for direct-on-TV OCR.
    fun show(a: Activity) {
        if (!stopped(a)) return
        val names = GameLingoGlossaryStore.names(a)
        val labels = listOf(GameLingoLanguages.ui(a, "Bez slovníku"), GameLingoLanguages.ui(a, "Nový herní slovník")) + names.map {
            it + if (GameLingoGlossaryStore.active(a) == it) " · " + GameLingoLanguages.ui(a, "aktivní") else ""
        }
        fun choose(i: Int) {
            if (!stopped(a)) return
            when (i) {
                0 -> GameLingoGlossaryStore.activate(a, null)
                1 -> {
                    val name = EditText(a).apply { hint = GameLingoLanguages.ui(a, "Název hry"); setSingleLine(true) }
                    AlertDialog.Builder(a).setTitle(GameLingoLanguages.ui(a, "Nový herní slovník")).setView(name)
                        .setNegativeButton(GameLingoLanguages.ui(a, "Zrušit"), null).setPositiveButton(GameLingoLanguages.ui(a, "Pokračovat")) { _, _ ->
                            val value = name.text.toString().trim()
                            if (value.isNotEmpty() && value.length <= 64) edit(a, value) { show(a) }
                        }.show()
                }
                else -> {
                    val name = names[i-2]
                    fun action(option: Int) {
                        if (!stopped(a)) return
                        when(option) {
                            0 -> GameLingoGlossaryStore.activate(a, name)
                            1 -> edit(a, name) { show(a) }
                            2 -> AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a, "Smazat slovník") + " " + name + "?")
                                .setNegativeButton(GameLingoLanguages.ui(a, "Zrušit"), null).setPositiveButton(GameLingoLanguages.ui(a, "Smazat")) { _, _ ->
                                    if (stopped(a)) GameLingoGlossaryStore.remove(a, name)
                                }.show()
                        }
                    }
                    val options=arrayOf(GameLingoLanguages.ui(a, "Použít"),GameLingoLanguages.ui(a, "Upravit"),GameLingoLanguages.ui(a, "Smazat"))
                    if(GameLingoTVUi.isTV(a)) GameLingoTVUi.select(a,name,options,onSelect=::action)
                    else AlertDialog.Builder(a).setTitle(name).setItems(options){_,option->action(option)}.show()
                }
            }
        }
        val body=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
        var page: android.app.Dialog?=null
        labels.forEachIndexed { index,label ->
            body.addView(GameLingoTVUi.action(a,label) { page?.dismiss();choose(index) })
        }
        page=GameLingoTVUi.page(a,GameLingoLanguages.ui(a,"Herní slovník"),body,helpTopic="glossary")
    }
}
