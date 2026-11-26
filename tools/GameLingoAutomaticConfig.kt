package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Separate automatic mode; no existing manual layout keys are overwritten. */
internal object GameLingoAutomaticConfig {
    private fun prefs(c: Context) = c.getSharedPreferences("gamelingo_automatic",Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled",false)
    private fun initial(c: Context) = listOf(AutoZone("dialogue",GameLingoLanguages.ui(c, "Dialogy"),true,AutoRect(0f,.65f,1f,1f),true))
    fun zones(c: Context): List<AutoZone> = prefs(c).getString("zones",null)?.let { decode(it) } ?: initial(c)
    private fun decode(raw: String): List<AutoZone>? = runCatching {
        val array=JSONArray(raw)
        val ids=mutableSetOf<String>()
        (0 until minOf(array.length(),10)).mapNotNull { i ->
            runCatching {
                val o=array.getJSONObject(i)
                val r=AutoRect(o.getDouble("left").toFloat(),o.getDouble("top").toFloat(),o.getDouble("right").toFloat(),o.getDouble("bottom").toFloat())
                val id=o.getString("id")
                require(id.isNotBlank() && id.length<=80 && r.valid() && ids.add(id))
                AutoZone(id,o.optString("name","Zóna").take(60),o.optBoolean("enabled",true),r,o.optBoolean("centered",false))
            }.getOrNull()
        }
    }.getOrNull()
    private fun encode(zones: List<AutoZone>)=JSONArray().apply {
        zones.filter { it.rect.valid() }.distinctBy { it.id }.take(10).forEach { z -> put(JSONObject().apply {
            put("id",z.id);put("name",z.name.take(60));put("enabled",z.enabled);put("centered",z.centered)
            put("left",z.rect.left.toDouble());put("top",z.rect.top.toDouble());put("right",z.rect.right.toDouble());put("bottom",z.rect.bottom.toDouble())
        }) }
    }
    private fun save(c: Context,zones: List<AutoZone>) { prefs(c).edit().putString("zones",encode(zones).toString()).apply() }
    fun profile(c: Context): JSONObject = JSONObject().put("enabled",enabled(c)).put("zones",encode(zones(c)))
    fun restore(c: Context,data: JSONObject?) {
        val editor=prefs(c).edit().putBoolean("enabled",data?.optBoolean("enabled",false)==true)
        data?.optJSONArray("zones")?.let { decode(it.toString()) }?.let { editor.putString("zones",encode(it).toString()) }
        editor.apply()
    }
    fun label(c: Context)=GameLingoLanguages.ui(c, if(enabled(c)) "Automatický" else "Standardní")
    private fun editable(a: Activity): Boolean {
        if (!RemotePlayService.aktivni && !PrijemService.bezi) return true
        AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a,"Nejdřív zastav překlad.")).setPositiveButton("OK",null).show()
        return false
    }
    private fun select(a:Activity,title:String,labels:Array<String>,selected:Int,action:(Int)->Unit) {
        if(GameLingoTVUi.isTV(a))GameLingoTVUi.select(a,title,labels,selected,action)
        else AlertDialog.Builder(a).setTitle(title).setSingleChoiceItems(labels,selected){dialog,index->dialog.dismiss();action(index)}
            .setNegativeButton(GameLingoLanguages.ui(a, "Zrušit"),null).show()
    }
    fun mode(a: Activity, advanced: () -> Unit = {}) {
        if (!editable(a)) return
        val labels=arrayOf("Standardní","Automatický","Pokročilé nastavení OCR").map { GameLingoLanguages.ui(a,it) }.toTypedArray()
        select(a,GameLingoLanguages.ui(a,"Režim překladu"),labels,if(enabled(a)) 1 else 0) { i ->
            if(i==2) advanced() else {
                prefs(a).edit().putBoolean("enabled",i==1).apply()
                // The default dialogue zone is already ready; editing is optional.
            }
        }
    }
    fun showZones(a: Activity) {
        if(!editable(a))return
        val zones=zones(a)
        val labels=(zones.map { (if(it.enabled) "✓  " else "○  ")+it.name }+
            listOfNotNull(GameLingoLanguages.ui(a,"Přidat zónu").takeIf { zones.size<10 },GameLingoLanguages.ui(a,"Obnovit výchozí"))).toTypedArray()
        select(a,GameLingoLanguages.ui(a,"Oblasti překladu")+" ("+zones.size+"/10)",labels,-1) { index ->
            when {
                index<zones.size -> {
                    val z=zones[index]
                    select(a,z.name,arrayOf(if(z.enabled) "Vypnout" else "Zapnout","Upravit","Odstranit").map { GameLingoLanguages.ui(a,it) }.toTypedArray(),-1) { action ->
                        if(action==1) edit(a,z,false)
                        else { save(a,if(action==2) zones.filter { it.id!=z.id } else zones.map { if(it.id==z.id) it.copy(enabled=!it.enabled) else it });showZones(a) }
                    }
                }
                index==zones.size && zones.size<10 -> edit(a,AutoZone(UUID.randomUUID().toString(),GameLingoLanguages.ui(a,"Zóna")+" "+(zones.size+1),true,AutoRect(.2f,.2f,.8f,.45f),false),true)
                else -> AlertDialog.Builder(a).setMessage(GameLingoLanguages.ui(a,"Obnovit výchozí zóny?")).setNegativeButton(GameLingoLanguages.ui(a,"Zrušit"),null).setPositiveButton(GameLingoLanguages.ui(a,"Obnovit")) { _,_->save(a,initial(a));showZones(a) }.show()
            }
        }
    }
    private fun edit(a: Activity,z: AutoZone,new: Boolean) {
        var rect=z.rect
        val body=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12) }
        val name=EditText(a).apply { setText(z.name);hint=GameLingoLanguages.ui(a,"Název");maxLines=1 }
        body.addView(name)
        val preview=object: View(a) {
            val p=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.BLACK)
                p.color=Color.YELLOW;p.style=Paint.Style.STROKE;p.strokeWidth=3f
                canvas.drawRect(rect.left*width,rect.top*height,rect.right*width,rect.bottom*height,p)
            }
        }
        body.addView(preview,LinearLayout.LayoutParams(-1,(a.resources.displayMetrics.widthPixels*.25f).toInt().coerceIn(100,300)))
        fun slider(title: String,value: Float,change: (Float)->Unit) {
            val label=TextView(a).apply { gravity=android.view.Gravity.CENTER; text="$title: "+(value*100).toInt()+" %" }
            body.addView(label)
            body.addView(SeekBar(a).apply {
                max=100;progress=(value*100).toInt().coerceIn(0,100);keyProgressIncrement=1;isFocusable=true
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar:SeekBar) {}
                    override fun onStopTrackingTouch(bar:SeekBar) {}
                    override fun onProgressChanged(bar:SeekBar,p:Int,fromUser:Boolean) {
                        if(fromUser){change(p/100f);label.text="$title: $p %";preview.invalidate()}
                    }
                })
            })
        }
        slider(GameLingoLanguages.ui(a,"Vlevo"),rect.left){v->rect=rect.copy(left=v.coerceAtMost(rect.right-.08f))}
        slider(GameLingoLanguages.ui(a,"Nahoře"),rect.top){v->rect=rect.copy(top=v.coerceAtMost(rect.bottom-.04f))}
        slider(GameLingoLanguages.ui(a,"Vpravo"),rect.right){v->rect=rect.copy(right=v.coerceAtLeast(rect.left+.08f))}
        slider(GameLingoLanguages.ui(a,"Dole"),rect.bottom){v->rect=rect.copy(bottom=v.coerceAtLeast(rect.top+.04f))}
        val scroll=android.widget.ScrollView(a).apply {addView(body)}
        AlertDialog.Builder(a).setTitle(GameLingoLanguages.ui(a,"Upravit zónu")).setView(scroll).setNegativeButton(GameLingoLanguages.ui(a,"Zrušit"),null)
            .setPositiveButton(GameLingoLanguages.ui(a,"Uložit")){_,_->
                if(rect.valid()) {
                    val updated=z.copy(name=name.text.toString().trim().take(60).ifBlank{GameLingoLanguages.ui(a,"Zóna")},rect=rect,centered=false)
                    val all=zones(a)
                    save(a,if(new)all+updated else all.map{if(it.id==z.id)updated else it})
                }
                showZones(a)
            }.show()
    }
}
