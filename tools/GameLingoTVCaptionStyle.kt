package cz.titulky.tv

import android.content.Context
import android.graphics.Color

/**
 * Styling of *translated captions displayed by GameLingo's TV receiver*.
 * Settings arrive only in the authenticated PCPAIR1 STYLE packet.
 * Local preferences are intentionally independent of legacy PlayCaption.
 */
object GameLingoTVCaptionStyle {
    private const val PREFS = "gamelingo_tv_caption_style"
    private const val LOCAL = "gamelingo_tv_local_style"
    private fun local(context: Context) = context.getSharedPreferences(LOCAL, Context.MODE_PRIVATE)
    private fun current(context: Context) = if (PrijemService.bezi)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) else local(context)
    private val acceptedColors = setOf("white", "yellow", "cyan", "green", "purple")
    private val acceptedBackgrounds = setOf("dark", "translucent", "none")

    fun save(context: Context, color: String, background: String): Boolean {
        if (color !in acceptedColors || background !in acceptedBackgrounds) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("color", color)
            .putString("background", background)
            .apply()
        return true
    }

    fun textColor(context: Context): Int {
        return when (current(context).getString("color", "white")) {
            "yellow" -> Color.rgb(255, 213, 74)
            "cyan" -> Color.rgb(2, 214, 255)
            "green" -> Color.rgb(53, 229, 125)
            "purple" -> Color.rgb(163, 41, 250)
            else -> Color.WHITE
        }
    }

    fun background(context: Context): Int {
        if (!PrijemService.bezi) return Color.argb(local(context).getInt("opacity", 230).coerceIn(0, 255), 0, 0, 0)
        return when (current(context).getString("background", "dark")) {
            "none" -> Color.TRANSPARENT
            "translucent" -> 0x76000000
            else -> 0xE6000000.toInt()
        }
    }

    fun isDark(context: Context): Boolean =
        if (PrijemService.bezi) current(context).getString("background", "dark") == "dark"
        else false

    fun show(a: android.app.Activity) {
        if (RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running) {
            android.app.AlertDialog.Builder(a).setMessage("Nejdřív zastav překlad a příjem.").setPositiveButton("OK", null).show()
            return
        }
        val keys = arrayOf("white", "yellow", "cyan", "green", "purple")
        val names = arrayOf("Bílá", "Žlutá", "Tyrkysová", "Zelená", "Fialová")
        val p = local(a)
        val root = android.widget.LinearLayout(a).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
        }
        val preview = android.widget.TextView(a).apply {
            text = "Ukázka přeložených titulků"; textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(12, 18, 12, 18)
        }
        val label = android.widget.TextView(a).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val opacityBar = android.widget.SeekBar(a).apply {
            max = 100; keyProgressIncrement = 5; isFocusable = true
            GameLingoTVUi.controlFocus(a, this)
        }
        fun refresh() {
            val percent = (p.getInt("opacity",230).coerceIn(0,255)*100+127)/255
            label.text = "Neprůhlednost pozadí: $percent %"
            if(opacityBar.progress != percent) opacityBar.progress=percent
            preview.textSize=24f*GameLingoDirectTVLayout.storedGreen(a)[3]
            preview.setTextColor(textColor(a)); preview.setBackgroundColor(background(a))
        }
        root.addView(preview)
        root.addView(GameLingoTVUi.action(a, "Barva písma") {
            GameLingoTVUi.select(a, "Barva písma", names, keys.indexOf(p.getString("color", "white"))) { index ->
                p.edit().putString("color", keys[index]).apply(); refresh()
            }
        })
        root.addView(GameLingoTVUi.action(a, "Pozadí titulků") {
            val dialogColors = arrayOf("Bez pozadí", "Tmavé", "Průhledné")
            val values = intArrayOf(0,230,117)
            val selected = values.indexOf(p.getInt("opacity",230))
            GameLingoTVUi.select(a,"Pozadí titulků",dialogColors,selected) { index ->
                p.edit().putInt("opacity",values[index]).apply()
                refresh()
            }
        })
        root.addView(label)
        root.addView(opacityBar.apply {
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) { p.edit().putInt("opacity", (value * 255 + 50) / 100).apply(); refresh() }
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {}
            })
        })
        fun geometry(labelText:String,index:Int,min:Int,max:Int) {
            val title=android.widget.TextView(a).apply {setTextColor(Color.WHITE)}
            val bar=android.widget.SeekBar(a).apply {
                this.max=max-min;keyProgressIncrement=5;isFocusable=true
                progress=(GameLingoDirectTVLayout.storedGreen(a)[index]*100).toInt().coerceIn(min,max)-min
                isEnabled=!GameLingoDirectTVLayout.locked(a,"Green")
                GameLingoTVUi.controlFocus(a,this)
            }
            title.text=labelText+": "+(bar.progress+min)+" %"
            bar.setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b:android.widget.SeekBar,v:Int,fromUser:Boolean){
                    title.text=labelText+": "+(v+min)+" %"
                    if(fromUser && !GameLingoDirectTVLayout.locked(a,"Green")){
                        val r=GameLingoDirectTVLayout.storedGreen(a);r[index]=(v+min)/100f
                        if(index==2)r[0]=r[0].coerceIn(r[2]/2f,1f-r[2]/2f)
                        GameLingoDirectTVLayout.saveGreen(a,r);refresh()
                    }
                }
                override fun onStartTrackingTouch(b:android.widget.SeekBar){}
                override fun onStopTrackingTouch(b:android.widget.SeekBar){}
            })
            root.addView(title);root.addView(bar)
        }
        geometry("Velikost písma",3,60,200)
        geometry("Šířka titulků",2,20,100)
        root.addView(GameLingoTVUi.action(a,"Náhled na TV"){
            if(RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running || OverlayService.overlayTestActive) return@action
            if(!android.provider.Settings.canDrawOverlays(a)) {
                android.widget.Toast.makeText(a,"Povol překryv v nastavení TV.",android.widget.Toast.LENGTH_LONG).show()
                return@action
            }
            OverlayService.posli(a,android.content.Intent(a,OverlayService::class.java)
                .setAction(OverlayService.ACTION_SET_TEXT).putExtra(OverlayService.EXTRA_TEXT,"Ukázka přeložených titulků")
                .putExtra(OverlayService.EXTRA_DOBA,6000L).putExtra(OverlayService.EXTRA_VLASTNI,GameLingoDirectTVLayout.storedGreen(a)))
            a.moveTaskToBack(true)
        })
        root.addView(GameLingoTVUi.action(a, "Velikost a poloha titulků") { GameLingoDirectTVLayout.show(a) {refresh()} })
        refresh()
        GameLingoTVUi.page(a,"Vzhled titulků",root,helpTopic="appearance")
    }
}
