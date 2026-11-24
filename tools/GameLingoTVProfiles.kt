package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import org.json.JSONObject
import org.json.JSONArray

/** TV-only profiles. Never store console credentials or pairing secrets. */
object GameLingoTVProfiles {
    private fun store(a: Context) = a.getSharedPreferences("gamelingo_tv_profiles", Context.MODE_PRIVATE)
    private fun all(a: Context) = runCatching { JSONObject(store(a).getString("profiles", "{}") ?: "{}") }.getOrDefault(JSONObject())
    private fun busy() = RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running
    private fun array(r: FloatArray) = JSONArray().apply { r.forEach { put(it.toDouble()) } }
    private fun snapshot(a: Activity): JSONObject {
        OblastTitulku.nacti(a); Volby.nacti(a); GameLingoOCRConfig.load(a)
        val n = Nastaveni(a)
        val style = a.getSharedPreferences("gamelingo_tv_local_style", Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("source", GameLingoLanguages.game(a)); put("target", GameLingoLanguages.target(a))
            put("ocr", GameLingoOCRConfig.mode); put("filter", GameLingoOCRConfig.filterMenuText); put("expand", GameLingoOCRConfig.expandSearch)
            put("dialogues", n.dialogy); put("hints", n.napovedy); put("cover", n.prekryvat); put("story", n.pribehovy); put("duration", n.casPribehuProcenta)
            put("autoRegion",GameLingoOCRConfig.region(a))
            put("fixedTiming",GameLingoOCRConfig.fixedTiming(a));put("seconds",GameLingoOCRConfig.seconds(a))
            put("skipOld",GameLingoOCRConfig.skipOld(a));put("maxAge",GameLingoOCRConfig.maxAge(a))
            put("manual", OblastTitulku.rucne); put("blue", array(OblastTitulku.oblast))
            put("blueLocked", OblastTitulku.zamceno); put("yellowLocked", Volby.zamceno)
            put("greenLocked", GameLingoDirectTVLayout.locked(a,"Green")); put("orangeLocked", GameLingoDirectTVLayout.locked(a,"Orange"))
            put("yellowEnabled", Volby.zapnuto); put("yellowText", Volby.rezimText); put("yellow", array(Volby.oblast))
            put("orange", array(GameLingoDirectTVLayout.orange(a)))
            val green = GameLingoDirectTVLayout.green(a)
            put("greenEnabled", green != null)
            put("green", array(GameLingoDirectTVLayout.storedGreen(a)))
            put("color", style.getString("color", "white")); put("opacity", style.getInt("opacity", 230))
        }
    }
    private fun restore(a: Activity, p: JSONObject): Boolean {
        if (busy()) return false
        fun read(key: String, size: Int): FloatArray? {
            val v = p.optJSONArray(key) ?: return null
            if (v.length() != size) return null
            return FloatArray(size) { v.optDouble(it, Double.NaN).toFloat() }.takeIf { r -> r.all { it.isFinite() } }
        }
        fun rect(r: FloatArray) = r.take(4).all { it in 0f..1f } && r[2] > r[0]+0.1f && r[3] > r[1]+0.05f
        val blue = read("blue", 4)?.takeIf(::rect) ?: return false
        val yellow = read("yellow", 4)?.takeIf(::rect) ?: return false
        val orange = read("orange", 5)?.takeIf { rect(it) && it[4] in 0.6f..2f } ?: return false
        val greenEnabled = p.optBoolean("greenEnabled")
        val green = read("green", 4)?.takeIf { it[0] in 0f..1f && it[1] in 0f..1f && it[2] in 0.2f..1f && it[3] in 0.6f..2f }
        if (greenEnabled && green == null) return false
        GameLingoLanguages.restoreProfile(a, p.optString("source"), p.optString("target"))
        val mode = p.optString("ocr")
        if (mode in setOf("classic", "automatic", "street")) GameLingoOCRConfig.setMode(a, mode)
        GameLingoOCRConfig.setFilterMenu(a, p.optBoolean("filter", true))
        GameLingoOCRConfig.setExpandSearch(a, p.optBoolean("expand", false))
        GameLingoOCRConfig.setRegion(a,p.optString("autoRegion","legacy").takeIf { it in GameLingoOCRConfig.regionKeys } ?: "legacy")
        GameLingoOCRConfig.saveTiming(a,p.optBoolean("fixedTiming"),p.optInt("seconds",6),p.optBoolean("skipOld"),p.optInt("maxAge",5))
        Nastaveni(a).apply { dialogy = p.optBoolean("dialogues", true); napovedy = p.optBoolean("hints", true); prekryvat = p.optBoolean("cover"); pribehovy = p.optBoolean("story"); casPribehuProcenta = p.optInt("duration", casPribehuProcenta).coerceIn(CasovaniPribehu.MIN_PROCENT, CasovaniPribehu.MAX_PROCENT) }
        OblastTitulku.zamceno = p.optBoolean("blueLocked"); Volby.zamceno = p.optBoolean("yellowLocked")
        OblastTitulku.rucne = p.optBoolean("manual"); OblastTitulku.oblast = blue; OblastTitulku.uloz(a)
        Volby.zapnuto = p.optBoolean("yellowEnabled"); Volby.rezimText = p.optBoolean("yellowText"); Volby.oblast = yellow; Volby.uloz(a)
        val editor = a.getSharedPreferences("gamelingo", Context.MODE_PRIVATE).edit().putBoolean("directGreenEnabled", greenEnabled)
        editor.putBoolean("directLockGreen", p.optBoolean("greenLocked")).putBoolean("directLockOrange", p.optBoolean("orangeLocked"))
        orange.forEachIndexed { i, v -> editor.putFloat("directOrange"+i, v) }
        green?.forEachIndexed { i, v -> editor.putFloat("directGreen"+i, v) }
        editor.apply()
        val color = p.optString("color", "white").takeIf { it in setOf("white", "yellow", "cyan", "green", "purple") } ?: "white"
        a.getSharedPreferences("gamelingo_tv_local_style", Context.MODE_PRIVATE).edit().putString("color", color).putInt("opacity", p.optInt("opacity",230).coerceIn(0,255)).apply()
        return true
    }
    /** Same stored TV profiles for console and MediaProjection sessions. */
    fun chooseForStart(a: Activity, onChosen: () -> Unit) {
        if (busy()) return
        val items = all(a)
        val names = items.keys().asSequence().sorted().toList()
        val current = store(a).getString("activeProfile", null)
        GameLingoTVUi.select(a, "Vybrat profil", (listOf("Aktuální nastavení") + names).toTypedArray(),
            names.indexOf(current ?: "")+1) { index ->
            if (index == 0) onChosen()
            else if (restore(a, items.getJSONObject(names[index-1]))) {
                store(a).edit().putString("activeProfile", names[index-1]).apply()
                activateGlossary(a,names[index-1])
                onChosen()
            } else android.widget.Toast.makeText(a,"Profil nelze načíst",android.widget.Toast.LENGTH_LONG).show()
        }
    }
    fun saveCurrent(a: Activity, onSaved: () -> Unit = {}) {
        if (busy()) return
        val input = EditText(a).apply {
            hint = "Název hry nebo aplikace"; setSingleLine(true)
            setText(store(a).getString("activeProfile","").orEmpty())
            selectAll()
        }
        fun persist(name: String) {
            if (busy()) return
            val latest=all(a)
            latest.put(name,snapshot(a))
            store(a).edit().putString("profiles",latest.toString()).putString("activeProfile",name).apply()
            android.widget.Toast.makeText(a,"Profil uložen: "+name,android.widget.Toast.LENGTH_SHORT).show()
            onSaved()
        }
        AlertDialog.Builder(a).setTitle("Uložit profil").setView(input).setNegativeButton("Zrušit",null)
            .setPositiveButton("Uložit") { _,_ ->
                val name=input.text.toString().trim().take(64)
                if (name.isEmpty()) {
                    android.widget.Toast.makeText(a,"Zadej název profilu.",android.widget.Toast.LENGTH_SHORT).show()
                } else if (all(a).has(name)) {
                    AlertDialog.Builder(a).setMessage("Přepsat profil "+name+"?")
                        .setNegativeButton("Zrušit",null).setPositiveButton("Přepsat"){_,_->persist(name)}.show()
                } else persist(name)
            }.show()
    }
    private fun activateGlossary(a: Activity,name: String) {
        GameLingoGlossaryStore.activate(a,name.takeIf { GameLingoGlossaryStore.read(a,it)!=null })
    }
    private fun editSettings(a: Activity, name: String, openLayout: Boolean = false) {
        if(busy()) return
        val body=android.widget.LinearLayout(a).apply { orientation=android.widget.LinearLayout.VERTICAL }
        body.addView(GameLingoTVUi.title(a,"Uprav aktuální nastavení. Tlačítkem Uložit změny do profilu přepíšeš uložený profil.",20f,true))
        fun add(title: String, run:()->Unit) { body.addView(GameLingoTVUi.action(a,title,onActivate=run)) }
        add("Jazyky"){GameLingoLanguages.show(a)}
        add("OCR"){GameLingoOCRSettings.show(a){GameLingoDirectTVLayout.show(a){}}}
        add("Překlad"){GameLingoSettings.showTranslation(a,Nastaveni(a),{GameLingoDirectTVLayout.show(a){}},{GameLingoLanguages.show(a)})}
        add("Rozložení"){GameLingoDirectTVLayout.show(a){}}
        add("Vzhled titulků"){GameLingoTVCaptionStyle.show(a)}
        add("Herní slovník"){GameLingoGlossaryUi.edit(a,name){activateGlossary(a,name)}}
        var page:android.app.Dialog?=null
        add("Uložit změny do profilu"){
            if(!busy()) {
                val items=all(a); items.put(name,snapshot(a))
                store(a).edit().putString("profiles",items.toString()).putString("activeProfile",name).apply()
                page?.dismiss()
                android.widget.Toast.makeText(a,"Profil uložen: "+name,android.widget.Toast.LENGTH_SHORT).show()
                show(a)
            }
        }
        page=GameLingoTVUi.page(a,"Upravit profil · "+name,body,helpTopic="profiles")
        if(openLayout) GameLingoDirectTVLayout.show(a) {}
    }
    fun show(a: Activity) {
        if (busy()) { AlertDialog.Builder(a).setMessage("Nejdřív zastav překlad a příjem.").setPositiveButton("OK", null).show(); return }
        val items = all(a)
        val names = items.keys().asSequence().sorted().toList()
        GameLingoTVUi.select(a,"Profily her a aplikací",(listOf("+ Uložit aktuální nastavení")+names).toTypedArray(),names.indexOf(store(a).getString("activeProfile",null))+1) { i ->
            if(i==0) saveCurrent(a){show(a)}
            else {
                val name=names[i-1]
                GameLingoTVUi.select(a,name,arrayOf("Použít","Upravit rozložení","Smazat","Upravit další nastavení")) { action ->
                    when(action) {
                        0,1 -> {
                            if(restore(a,all(a).getJSONObject(name))) {
                                store(a).edit().putString("activeProfile",name).apply()
                                activateGlossary(a,name)
                                if(action==1) editSettings(a,name,openLayout=true)
                                else android.widget.Toast.makeText(a,"Profil načten",android.widget.Toast.LENGTH_SHORT).show()
                            } else android.widget.Toast.makeText(a,"Profil nelze načíst",android.widget.Toast.LENGTH_SHORT).show()
                        }
                        3 -> {
                            if(restore(a,all(a).getJSONObject(name))) {
                                store(a).edit().putString("activeProfile",name).apply()
                                activateGlossary(a,name)
                                editSettings(a,name)
                            }
                        }
                        2 -> AlertDialog.Builder(a).setMessage("Smazat profil "+name+"?").setNegativeButton("Zrušit",null)
                            .setPositiveButton("Smazat"){_,_->
                                if(!busy()) {
                                    val latest=all(a); latest.remove(name)
                                    GameLingoGlossaryStore.remove(a,name)
                                    val e=store(a).edit().putString("profiles",latest.toString())
                                    if(store(a).getString("activeProfile",null)==name)e.remove("activeProfile")
                                    e.apply()
                                }
                                show(a)
                            }.show()
                    }
                }
            }
        }
    }
}
