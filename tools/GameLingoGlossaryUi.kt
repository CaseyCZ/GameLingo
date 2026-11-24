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
        if (!RemotePlayService.aktivni) return true
        AlertDialog.Builder(a).setMessage("Nejdřív zastav probíhající překlad.")
            .setPositiveButton("OK", null).show()
        return false
    }
    fun edit(a: Activity, name: String, onSaved: () -> Unit = {}) {
        if (!stopped(a)) return
        val saved = GameLingoGlossaryStore.read(a, name)
        var source = saved?.source ?: GameLingoLanguages.game(a)
        var target = saved?.target ?: GameLingoLanguages.target(a)
        val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 10, 20, 10) }
        val pair = TextView(a).apply { text = "Jazyky slovníku: $source → $target" }
        body.addView(pair)
        body.addView(TextView(a).apply {
            text = "Max. 50 řádků: originál | chybný překlad | správný překlad. Prázdný prostředek opraví jen celý přesný originál. Rozlišuje velikost písmen."
        })
        val input = EditText(a).apply {
            setText(saved?.text.orEmpty())
            hint = "Arthur Morgan | Artur Morgan | Arthur Morgan"
            minLines = 4
            maxLines = 9
            setSingleLine(false)
        }
        body.addView(input)
        body.addView(Button(a).apply {
            text = "Nový slovník pro aktuální jazyky"
            setOnClickListener {
                source = GameLingoLanguages.game(a); target = GameLingoLanguages.target(a)
                pair.text = "Jazyky slovníku: $source → $target"
                input.setText("")
            }
        })
        val error = TextView(a)
        body.addView(error)
        if(GameLingoTVUi.isTV(a)) {
            var page:android.app.Dialog?=null
            GameLingoTVUi.controlFocus(a,input)
            body.addView(GameLingoTVUi.action(a,"Uložit",primary=true) {
                if(stopped(a)) {
                    if(!GameLingoGlossaryStore.save(a,name,source,target,input.text.toString()))
                        error.text="Neplatný formát, duplicita originálu nebo překročený limit."
                    else {page?.dismiss();onSaved()}
                }
            })
            page=GameLingoTVUi.page(a,"Herní slovník · "+name,body,helpTopic="glossary")
            return
        }
        val dialog = AlertDialog.Builder(a).setTitle("Herní slovník · " + name)
            .setView(ScrollView(a).apply { addView(body) })
            .setNegativeButton("Zrušit", null).setPositiveButton("Uložit", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!stopped(a)) return@setOnClickListener
            if (!GameLingoGlossaryStore.save(a, name, source, target, input.text.toString())) {
                error.text = "Neplatný formát, duplicita originálu nebo překročený limit."
                return@setOnClickListener
            }
            dialog.dismiss()
            onSaved()
        }
    }
    // TV does not have the mobile geometry-profile screen; choose local named
    // dictionary contexts explicitly for direct-on-TV OCR.
    fun show(a: Activity) {
        if (!stopped(a)) return
        val names = GameLingoGlossaryStore.names(a)
        val labels = listOf("Bez slovníku", "Nový herní slovník") + names.map {
            it + if (GameLingoGlossaryStore.active(a) == it) " · aktivní" else ""
        }
        fun choose(i: Int) {
            if (!stopped(a)) return
            when (i) {
                0 -> GameLingoGlossaryStore.activate(a, null)
                1 -> {
                    val name = EditText(a).apply { hint = "Název hry"; setSingleLine(true) }
                    AlertDialog.Builder(a).setTitle("Nový herní slovník").setView(name)
                        .setNegativeButton("Zrušit", null).setPositiveButton("Pokračovat") { _, _ ->
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
                            2 -> AlertDialog.Builder(a).setMessage("Smazat slovník " + name + "?")
                                .setNegativeButton("Zrušit", null).setPositiveButton("Smazat") { _, _ ->
                                    if (stopped(a)) GameLingoGlossaryStore.remove(a, name)
                                }.show()
                        }
                    }
                    val options=arrayOf("Použít","Upravit","Smazat")
                    if(GameLingoTVUi.isTV(a)) GameLingoTVUi.select(a,name,options,onSelect=::action)
                    else AlertDialog.Builder(a).setTitle(name).setItems(options){_,option->action(option)}.show()
                }
            }
        }
        if(GameLingoTVUi.isTV(a)) GameLingoTVUi.select(a,"Herní slovník",labels.toTypedArray(),onSelect=::choose)
        else AlertDialog.Builder(a).setTitle("Herní slovník").setItems(labels.toTypedArray()){_,i->choose(i)}
            .setNegativeButton("Zavřít",null).show()
    }
}
