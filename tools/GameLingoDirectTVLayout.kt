package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.Gravity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.widget.TextView

/** Local direct-mode settings only. The authenticated phone receiver uses its
 * own packet geometry, never these values. */
object GameLingoDirectTVLayout {
    private fun prefs(c: Context) = c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)
    private val orangeDefault = floatArrayOf(0.07f, 0.43f, 0.50f, 0.60f, 1f)
    private val greenDefault = floatArrayOf(0.5f, 0.93f, 0.62f, 1f)
    private fun validRect(r: FloatArray) = r.size >= 4 &&
        r.take(4).all { it.isFinite() && it in 0f..1f } &&
        r[2] > r[0]+0.1f && r[3] > r[1]+0.05f
    private fun read(c: Context, prefix: String, default: FloatArray): FloatArray {
        val p = prefs(c)
        return FloatArray(default.size) { p.getFloat(prefix+it, default[it]) }
    }
    fun orange(c: Context): FloatArray {
        val r = read(c, "directOrange", orangeDefault)
        return r.takeIf { validRect(it) && it[4].isFinite() && it[4] in 0.6f..2f } ?: orangeDefault.copyOf()
    }
    fun green(c: Context): FloatArray? = if (prefs(c).getBoolean("directGreenEnabled", false)) storedGreen(c) else null
    internal fun storedGreen(c: Context): FloatArray {
        val r = read(c, "directGreen", greenDefault)
        return r.takeIf { it.all { v -> v.isFinite() } && it[0] in 0f..1f &&
            it[1] in 0f..1f && it[2] in 0.2f..1f && it[3] in 0.6f..2f } ?: greenDefault.copyOf()
    }
    fun blue(c: Context): FloatArray {
        OblastTitulku.nacti(c)
        return OblastTitulku.oblast.copyOf().takeIf(::validRect) ?: OblastTitulku.VYCHOZI.copyOf()
    }
    fun yellow(c: Context): FloatArray {
        Volby.nacti(c)
        return Volby.oblast.copyOf().takeIf(::validRect) ?: Volby.VYCHOZI.copyOf()
    }
    internal fun intersects(a: FloatArray, b: FloatArray) =
        a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]
    internal fun greenBounds(r: FloatArray): FloatArray {
        val left = (r[0] - r[2]/2f).coerceIn(0f, 1f-r[2])
        return floatArrayOf(left,(r[1]-.24f*r[3]).coerceAtLeast(0f),left+r[2],r[1])
    }
    internal fun saveGreen(c: Context, values: FloatArray) {
        val e=prefs(c).edit().putBoolean("directGreenEnabled",true)
        values.forEachIndexed { i,v->e.putFloat("directGreen"+i,v) }
        e.apply()
    }
    internal fun locked(c: Context, region: String): Boolean = prefs(c).getBoolean("directLock"+region, false)
    private fun busy() = RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running

    private val names = arrayOf("Modrá · čtení dialogů", "Zelená · přeložené dialogy",
        "Žlutá · čtení dalších textů", "Oranžová · přeložené texty")
    private val colors = intArrayOf(0xFF4FB3FF.toInt(),0xFF3CFF6E.toInt(),0xFFFFD54A.toInt(),0xFFFF9E34.toInt())
    private val handler = Handler(Looper.getMainLooper())
    private var previewWindow: View? = null
    private var previewManager: WindowManager? = null
    private var previewTimeout: Runnable? = null

    private fun removePreview() {
        previewTimeout?.let { handler.removeCallbacks(it) }; previewTimeout = null
        previewWindow?.let { view -> runCatching { previewManager?.removeViewImmediate(view) } }
        previewWindow = null; previewManager = null
    }

    private class AreaCanvas(context: Context, private val boxes: Array<FloatArray>,
                             private val selected: () -> Int, private val overVideo: Boolean,
                             private val locked: (Int) -> Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = if (overVideo) 0f else 8f
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            context.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
            val ratio = metrics.widthPixels.coerceAtLeast(1).toFloat()/metrics.heightPixels.coerceAtLeast(1)
            val w = if (overVideo) width.toFloat() else minOf((width-2*inset).coerceAtLeast(1f),
                (height-2*inset).coerceAtLeast(1f)*ratio)
            val h = if (overVideo) height.toFloat() else w/ratio
            val x = (width-w)/2f; val y = (height-h)/2f
            val density = resources.displayMetrics.density
            if (!overVideo) {
                paint.style=Paint.Style.FILL; paint.color=0xFF111A33.toInt()
                canvas.drawRoundRect(x,y,x+w,y+h,8*density,8*density,paint)
            }
            // Active region is drawn last, above overlapping outlines.
            val order = (0..3).filter { it != selected() } + selected()
            for (i in order) {
                if (i !in 0..3) continue
                val r = if (i == 1) greenBounds(boxes[i]) else boxes[i]
                val left=x+r[0]*w; val top=y+r[1]*h
                val right=x+r[2]*w; val bottom=y+r[3]*h
                paint.color=colors[i]; paint.style=Paint.Style.STROKE
                paint.strokeWidth=(if (i==selected()) 3.5f else 1.5f)*density
                canvas.drawRect(left,top,right,bottom,paint)
                paint.style=Paint.Style.FILL
                paint.textSize=(if (overVideo) 15f else 12f)*density
                val label=names[i].substringBefore(" ·") + if (locked(i)) " · zamčeno" else ""
                canvas.save(); canvas.clipRect(left,top,right,bottom)
                canvas.drawText(label,left+5*density,minOf(bottom-2*density,top+paint.textSize+3*density),paint)
                canvas.restore()
            }
            if (overVideo) {
                paint.color=0xC0000000.toInt(); paint.style=Paint.Style.FILL
                val tw= minOf(w, 360*density)
                canvas.drawRoundRect(x+(w-tw)/2,y+8*density,x+(w+tw)/2,y+42*density,8*density,8*density,paint)
                paint.color=Color.WHITE; paint.textSize=14*density; paint.textAlign=Paint.Align.CENTER
                canvas.drawText("Náhled oblastí · zmizí za 30 s",x+w/2,y+30*density,paint)
                paint.textAlign=Paint.Align.LEFT
            }
        }
    }

    /** Temporary non-interactive outlines, without capture, OCR or changes to saved settings. */
    private fun showOnTV(a: Activity, boxes: Array<FloatArray>, selected: Int, locks: BooleanArray) {
        if (busy()) return
        if (!android.provider.Settings.canDrawOverlays(a)) {
            Toast.makeText(a,"Nejdříve povol překryv v nastavení TV.",Toast.LENGTH_LONG).show()
            return
        }
        removePreview()
        val c=a.applicationContext
        val wm=c.getSystemService(WindowManager::class.java)
        val copies=Array(4) { boxes[it].copyOf() }
        val view=AreaCanvas(c,copies,{selected},true,{locks[it]})
        val lp=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.LEFT }
        try {
            wm.addView(view,lp)
            previewWindow=view; previewManager=wm
            val expiry=Runnable { removePreview() }; previewTimeout=expiry
            handler.postDelayed(expiry,30000L)
            a.moveTaskToBack(true)
        } catch (e: Exception) {
            removePreview()
            Toast.makeText(a,"Náhled nelze zobrazit: "+e.javaClass.simpleName,Toast.LENGTH_LONG).show()
        }
    }

    fun show(a: Activity, onSaved: () -> Unit) {
        if (busy()) {
            AlertDialog.Builder(a).setMessage("Nejdříve zastav překlad i příjem z telefonu.")
                .setPositiveButton("OK",null).show()
            return
        }
        removePreview()
        OblastTitulku.nacti(a); Volby.nacti(a)
        val savedGreen=green(a)
        val storedGreen=read(a,"directGreen",greenDefault)
        val validGreen=storedGreen.takeIf { r -> r.all { it.isFinite() } &&
            r[0] in 0f..1f && r[1] in 0f..1f && r[2] in .2f..1f && r[3] in .6f..2f }
        val boxes=arrayOf(blue(a),savedGreen ?: validGreen ?: greenDefault.copyOf(),yellow(a),orange(a))
        val defaults=arrayOf(OblastTitulku.VYCHOZI.copyOf(),greenDefault.copyOf(),Volby.VYCHOZI.copyOf(),orangeDefault.copyOf())
        val locks=booleanArrayOf(OblastTitulku.zamceno,locked(a,"Green"),Volby.zamceno,locked(a,"Orange"))
        var manual=OblastTitulku.rucne
        var custom=savedGreen!=null
        var enabled=Volby.zapnuto
        var textMode=Volby.rezimText
        var selected=0
        var mode=0
        val dialog=Dialog(a,android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val root=LinearLayout(a).apply {
            orientation=LinearLayout.VERTICAL
            background=GameLingoVisualKit.pageBackground(a)
            setPadding(GameLingoTVUi.dp(a,20),GameLingoTVUi.dp(a,12),GameLingoTVUi.dp(a,20),GameLingoTVUi.dp(a,12))
        }
        fun d(v:Int)=GameLingoTVUi.dp(a,v)
        fun button(label:String, run:()->Unit)=GameLingoTVUi.action(a,label,onActivate=run).apply {
            minimumHeight=d(42); textSize=15f; setPadding(d(8),d(6),d(8),d(6))
        }
        fun row()=LinearLayout(a).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        fun addWeighted(parent:LinearLayout, view:View) {
            parent.addView(view,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply { marginEnd=d(6) })
        }
        val top=row()
        top.addView(GameLingoTVUi.title(a,"Rozložení oblastí",24f),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        top.addView(GameLingoHelp.button(a,"regions"),GameLingoHelp.layout(a))
        top.addView(button("Zrušit"){dialog.dismiss()})
        root.addView(top)
        val tabs=row(); root.addView(tabs)
        val body=row()
        root.addView(body,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        val left=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
        body.addView(left,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1.6f).apply { marginEnd=d(12) })
        val preview=AreaCanvas(a,boxes,{selected},false,{locks[it]})
        left.addView(preview,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        val guide=TextView(a).apply { setTextColor(Color.WHITE); textSize=14f; gravity=Gravity.CENTER }
        left.addView(guide)
        val side=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
        val scroll=ScrollView(a).apply { addView(side); isFillViewport=true }
        body.addView(scroll,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f))
        val active=GameLingoTVUi.title(a,"",18f)
        val metricsLabel=TextView(a).apply { setTextColor(Color.WHITE); textSize=13f; setPadding(d(6),d(6),d(6),d(6)) }
        side.addView(active); side.addView(metricsLabel)
        val tabButtons=mutableListOf<TextView>()
        val modeButtons=mutableListOf<TextView>()
        lateinit var lockButton:TextView
        lateinit var option:TextView
        lateinit var contentType:TextView
        lateinit var editor:TextView
        fun refresh() {
            active.text=names[selected]; active.setTextColor(colors[selected])
            tabButtons.forEachIndexed { i,v -> v.text=(if(i==selected) "✓ " else "")+names[i].substringBefore(" ·"); v.setTextColor(colors[i]) }
            modeButtons.forEachIndexed { i,v -> v.text=(if(i==mode) "✓ " else "")+arrayOf("Posun","Velikost","Písmo")[i] }
            modeButtons[2].visibility=if(selected==1 || selected==3) View.VISIBLE else View.GONE
            lockButton.text=if(locks[selected]) "Odemknout oblast" else "Zamknout oblast"
            option.text=when(selected) {0->if(manual)"Čtení: ruční" else "Čtení: automatické";1->if(custom)"Poloha: vlastní" else "Poloha: automatická";else->if(enabled)"Další texty: zapnuto" else "Další texty: vypnuto"}
            contentType.visibility=if(selected==2) View.VISIBLE else View.GONE
            contentType.text=if(textMode)"Typ: celý text" else "Typ: volby odpovědí"
            val r=boxes[selected]
            fun pc(v:Float)=kotlin.math.round(v*100).toInt()
            metricsLabel.text=if(selected==1) "X "+pc(r[0])+" % · Y "+pc(r[1])+" %\nŠířka "+pc(r[2])+" % · písmo "+pc(r[3])+" %"
                else "X "+pc(r[0])+" % · Y "+pc(r[1])+" %\nŠířka "+pc(r[2]-r[0])+" % · výška "+pc(r[3]-r[1])+" %"+if(selected==3)" · písmo "+pc(r[4])+" %" else ""
            editor.text=if(locks[selected])"Zamčeno · odemkni oblast" else when(mode){0->"OK · posouvat šipkami";1->if(selected==1)"OK · šířka a měřítko" else "OK · změnit velikost";else->"OK · velikost písma"}
            guide.text="Vyber oblast a režim · v editoru stiskni OK, potom šipky · OK ukončí úpravy"
            preview.invalidate()
        }
        fun cycleMode() { mode=(mode+1)%(if(selected==1 || selected==3)3 else 2); refresh() }
        fun change(dx:Float,dy:Float) {
            if(locks[selected]) return
            val r=boxes[selected]
            when(mode) {
                0 -> if(selected==1) {
                    r[0]=(r[0]+dx).coerceIn(r[2]/2f,1f-r[2]/2f)
                    r[1]=(r[1]+dy).coerceIn((.24f*r[3]).coerceAtMost(1f),1f)
                } else {
                    val w=r[2]-r[0]; val h=r[3]-r[1]
                    r[0]=(r[0]+dx).coerceIn(0f,(1f-w).coerceAtLeast(0f)); r[2]=(r[0]+w).coerceAtMost(1f)
                    r[1]=(r[1]+dy).coerceIn(0f,(1f-h).coerceAtLeast(0f)); r[3]=(r[1]+h).coerceAtMost(1f)
                }
                1 -> if(selected==1) {
                    r[2]=(r[2]+dx).coerceIn(.2f,1f)
                    r[0]=r[0].coerceIn(r[2]/2f,1f-r[2]/2f)
                    r[3]=(r[3]+dy).coerceIn(.6f,2f)
                } else {
                    val w=(r[2]-r[0]+dx).coerceIn(.11f,1f)
                    val h=(r[3]-r[1]+dy).coerceIn(.06f,1f)
                    r[0]=r[0].coerceAtMost(1f-w); r[2]=(r[0]+w).coerceAtMost(1f)
                    r[1]=r[1].coerceAtMost(1f-h); r[3]=(r[1]+h).coerceAtMost(1f)
                }
                2 -> { val index=if(selected==1)3 else 4; if(index<r.size) r[index]=(r[index]+dx+dy).coerceIn(.6f,2f) }
            }
            if(selected==0) manual=true
            if(selected==1) custom=true
            refresh()
        }
        for(i in 0..3) {
            val v=button(names[i].substringBefore(" ·")) { selected=i; mode=0; refresh(); editor.requestFocus() }
            tabButtons.add(v); addWeighted(tabs,v)
        }
        val modes=row()
        for(i in 0..2) {
            val v=button(arrayOf("Posun","Velikost","Písmo")[i]) { mode=i; refresh(); editor.requestFocus() }
            modeButtons.add(v); addWeighted(modes,v)
        }
        side.addView(modes)
        editor=button("Posouvat"){cycleMode()}
        var editing=false
        editor.setOnFocusChangeListener { v, focused ->
            v.background=GameLingoTVUi.panel(a,focused)
            if (!focused) editing=false
        }
        editor.setOnKeyListener { _,key,event ->
            if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) {
                if(event.action==KeyEvent.ACTION_UP) {
                    editing=!editing
                    guide.text=if(editing)"Režim úprav · šipky mění rámeček · OK ukončí úpravy" else "Vyber další ovládací prvek šipkami"
                }
                true
            } else if(editing && key==KeyEvent.KEYCODE_BACK) {
                if(event.action==KeyEvent.ACTION_UP) { editing=false; guide.text="Úpravy ukončeny · šipkami vyber další ovládací prvek" }
                true
            } else if(editing && key in arrayOf(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN)) {
                if(event.action==KeyEvent.ACTION_DOWN) {
                    val step=if(event.repeatCount>5).05f else .01f
                    change(when(key){KeyEvent.KEYCODE_DPAD_LEFT->-step;KeyEvent.KEYCODE_DPAD_RIGHT->step;else->0f},
                        when(key){KeyEvent.KEYCODE_DPAD_UP->-step;KeyEvent.KEYCODE_DPAD_DOWN->step;else->0f})
                    guide.text="Režim úprav · OK ukončí úpravy"
                }
                true
            } else false
        }
        side.addView(editor)
        lockButton=button("Zamknout oblast"){locks[selected]=!locks[selected];refresh()};side.addView(lockButton)
        option=button("Režim"){when(selected){0->manual=!manual;1->custom=!custom;else->enabled=!enabled};refresh()};side.addView(option)
        contentType=button("Typ"){textMode=!textMode;refresh()};side.addView(contentType)
        side.addView(button("Obnovit oblast"){
            if(!locks[selected]) {defaults[selected].copyInto(boxes[selected]);refresh()}
            else Toast.makeText(a,"Nejdřív odemkni oblast.",Toast.LENGTH_SHORT).show()
        })
        val footer=row()
        addWeighted(footer,button("Náhled na TV"){showOnTV(a,boxes,selected,locks.copyOf())})
        fun save():Boolean {
            if(busy() || !validRect(boxes[0]) || !validRect(boxes[2]) || !validRect(boxes[3])) {
                Toast.makeText(a,"Překlad běží nebo oblast není platná.",Toast.LENGTH_LONG).show();return false
            }
            OblastTitulku.zamceno=locks[0]; OblastTitulku.rucne=manual; OblastTitulku.oblast=boxes[0].copyOf(); OblastTitulku.uloz(a)
            Volby.zamceno=locks[2]; Volby.zapnuto=enabled; Volby.rezimText=textMode; Volby.oblast=boxes[2].copyOf();Volby.uloz(a)
            val e=prefs(a).edit().putBoolean("directGreenEnabled",custom)
                .putBoolean("directLockGreen",locks[1]).putBoolean("directLockOrange",locks[3])
            boxes[1].forEachIndexed { i,v->e.putFloat("directGreen"+i,v) }
            boxes[3].forEachIndexed { i,v->e.putFloat("directOrange"+i,v) }
            e.apply();return true
        }
        addWeighted(footer,button("Uložit do profilu"){if(save()){dialog.dismiss();GameLingoTVProfiles.saveCurrent(a) { onSaved() }}})
        addWeighted(footer,button("Uložit"){if(save()){dialog.dismiss();onSaved()}})
        root.addView(footer)
        refresh()
        dialog.setContentView(root)
        root.viewTreeObserver.addOnWindowFocusChangeListener { focused -> if(focused) removePreview() }
        dialog.setOnDismissListener { removePreview() }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        tabButtons.first().requestFocus()
    }
}
