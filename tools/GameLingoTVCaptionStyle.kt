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
        fun tr(cs: String) = GameLingoLanguages.ui(a, cs)
        if (RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running) {
            android.app.AlertDialog.Builder(a).setMessage(tr("Nejdřív zastav překlad a příjem.")).setPositiveButton("OK", null).show()
            return
        }
        val keys = arrayOf("white", "yellow", "cyan", "green", "purple")
        val names = arrayOf("Bílá", "Žlutá", "Tyrkysová", "Zelená", "Fialová").map(::tr).toTypedArray()
        val p = local(a)
        val root = android.widget.LinearLayout(a).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
        }
        val preview = android.widget.TextView(a).apply {
            text = tr("Ukázka přeložených titulků"); textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(12, 18, 12, 18)
        }
        val label = android.widget.TextView(a).apply {
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = GameLingoVisualKit.sp(a, 16f)
        }
        val opacityBar = android.widget.SeekBar(a).apply {
            max = 100; keyProgressIncrement = 5; isFocusable = true
            GameLingoTVUi.controlFocus(a, this)
        }
        val colors=intArrayOf(Color.WHITE,Color.rgb(255,213,74),Color.rgb(2,214,255),Color.rgb(53,229,125),Color.rgb(163,41,250))
        val swatches=mutableListOf<android.widget.TextView>()
        val backgrounds=mutableListOf<android.widget.TextView>()
        val bgValues=intArrayOf(0,230,117)
        val bgNames=arrayOf("Žádné","Tmavé","Průhledné").map(::tr).toTypedArray()
        fun paintSwatch(i:Int) {
            val button=swatches[i]
            val selected=p.getString("color","white")==keys[i]
            button.text=if(selected)"✓" else ""
            button.background=android.graphics.drawable.GradientDrawable().apply {
                shape=android.graphics.drawable.GradientDrawable.OVAL
                setColor(colors[i])
                setStroke(GameLingoVisualKit.dp(a,if(button.hasFocus())4 else if(selected)3 else 1),
                    if(button.hasFocus()||selected)Color.rgb(2,214,255) else Color.argb(100,255,255,255))
            }
            button.isSelected=selected
            button.contentDescription=names[i]+if(selected)" · "+tr("vybráno") else ""
        }
        fun refresh() {
            swatches.indices.forEach{paintSwatch(it)}
            backgrounds.forEachIndexed{i,v->v.text=(if(p.getInt("opacity",230)==bgValues[i])"✓ " else "")+bgNames[i]}
            val percent = (p.getInt("opacity",230).coerceIn(0,255)*100+127)/255
            label.text = "${tr("Neprůhlednost pozadí")}: $percent %"
            if(opacityBar.progress != percent) opacityBar.progress=percent
            preview.textSize=24f*GameLingoDirectTVLayout.storedGreen(a)[3]
            preview.setTextColor(textColor(a)); preview.setBackgroundColor(background(a))
        }
        root.addView(GameLingoVisualKit.captionPreview(a,preview))
        root.addView(GameLingoTVUi.title(a,tr("Barva textu"),18f))
        val colorRow=android.widget.LinearLayout(a).apply{orientation=android.widget.LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER;clipChildren=false}
        keys.forEachIndexed{i,key->
            val swatch=android.widget.TextView(a).apply {
                gravity=android.view.Gravity.CENTER;textSize=GameLingoVisualKit.sp(a, 20f);setTextColor(Color.BLACK)
                isFocusable=true;isClickable=true
                setOnClickListener {p.edit().putString("color",key).apply();refresh()}
                setOnFocusChangeListener {_,_->paintSwatch(i)}
            }
            swatches.add(swatch)
            val size=GameLingoVisualKit.dp(a,44)
            colorRow.addView(swatch,android.widget.LinearLayout.LayoutParams(size,size).apply{marginEnd=GameLingoVisualKit.dp(a,9)})
        }
        root.addView(colorRow)
        root.addView(GameLingoTVUi.title(a,tr("Pozadí titulků"),18f))
        val backgroundRow=android.widget.LinearLayout(a).apply{orientation=android.widget.LinearLayout.HORIZONTAL}
        bgNames.forEachIndexed{i,name->
            val button=GameLingoTVUi.action(a,name){p.edit().putInt("opacity",bgValues[i]).apply();refresh()}
            backgrounds.add(button)
            backgroundRow.addView(button,android.widget.LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=GameLingoVisualKit.dp(a,5)})
        }
        root.addView(backgroundRow)
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
            val title=android.widget.TextView(a).apply {gravity=android.view.Gravity.CENTER;setTextColor(Color.WHITE)}
            val bar=android.widget.SeekBar(a).apply {
                this.max=max-min;keyProgressIncrement=5;isFocusable=true
                progress=(GameLingoDirectTVLayout.storedGreen(a)[index]*100).toInt().coerceIn(min,max)-min
                isEnabled=!GameLingoDirectTVLayout.locked(a,"Green")
                GameLingoTVUi.controlFocus(a,this)
            }
            title.text=tr(labelText)+": "+(bar.progress+min)+" %"
            bar.setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b:android.widget.SeekBar,v:Int,fromUser:Boolean){
                    title.text=tr(labelText)+": "+(v+min)+" %"
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
        geometry("Velikost písma",3,10,200)
        geometry("Šířka titulků",2,20,100)
        root.addView(GameLingoTVUi.action(a,tr("Náhled na TV")){
            if(RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running || OverlayService.overlayTestActive) return@action
            if(!android.provider.Settings.canDrawOverlays(a)) {
                android.widget.Toast.makeText(a,tr("Povol překryv v nastavení TV."),android.widget.Toast.LENGTH_LONG).show()
                return@action
            }
            OverlayService.posli(a,android.content.Intent(a,OverlayService::class.java)
                .setAction(OverlayService.ACTION_SET_TEXT).putExtra(OverlayService.EXTRA_TEXT,tr("Ukázka přeložených titulků"))
                .putExtra(OverlayService.EXTRA_DOBA,6000L).putExtra(OverlayService.EXTRA_VLASTNI,GameLingoDirectTVLayout.storedGreen(a)))
            a.moveTaskToBack(true)
        })
        root.addView(GameLingoTVUi.action(a, tr("Velikost a poloha titulků")) { GameLingoDirectTVLayout.show(a) {refresh()} })
        refresh()
        GameLingoTVUi.page(a,tr("Vzhled titulků"),root,helpTopic="appearance")
    }
}
