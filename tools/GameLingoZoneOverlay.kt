package cz.titulky.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

/** One non-focusable, non-touchable overlay, with independent native captions.
 * Only main-thread service calls may mutate the window. Lost packets expire. */
internal class GameLingoZoneOverlay(private val context: Context, private val wm: WindowManager) {
    private val ui = Handler(Looper.getMainLooper())
    private var root: ZoneView? = null
    private var captureHidden = false
    fun suppress(hidden: Boolean): Boolean {
        val visible = root?.let { it.visibility == View.VISIBLE && it.alpha > 0f } == true
        captureHidden = hidden
        root?.alpha = if(hidden) 0f else 1f
        return visible
    }
    private val expire = Runnable { clear() }
    fun clear() {
        ui.removeCallbacks(expire)
        root?.let { runCatching { wm.removeViewImmediate(it) } }
        root = null
    }
    fun show(packet: GameLingoZonePacket, local: Boolean = false) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val allowed = if(local) !PrijemService.bezi && (RemotePlayService.aktivni || GameLingoScreenTranslationService.running) else PrijemService.bezi
        if (packet.blocks.isEmpty() || !allowed || !Settings.canDrawOverlays(context)) {
            clear()
            return
        }
        val view = root ?: ZoneView(context).also { candidate ->
            val lp = WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                // Touch devices keep the obscuring-opacity limit; TV remote focus is unaffected.
                alpha = if(context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)) .8f else 1f
            }
            if (runCatching { wm.addView(candidate, lp) }.isFailure) return
            root = candidate
        }
        view.alpha = if(captureHidden) 0f else 1f
        view.update(packet)
        ui.removeCallbacks(expire)
        ui.postDelayed(expire, 2200)
    }

    private class ZoneView(c: Context) : FrameLayout(c) {
        private var packet = GameLingoZonePacket(16f/9f, 1f, emptyList())
        init { setBackgroundColor(Color.TRANSPARENT); setWillNotDraw(false); isFocusable = false; isClickable = false }
        fun update(next: GameLingoZonePacket) {
            if (packet == next && childCount == next.blocks.count { it.height == null }) return
            packet = next
            removeAllViews()
            next.blocks.filter { it.height == null }.forEach { block ->
                addView(TextView(context).apply {
                    text = block.text
                    textSize = 22f * next.font
                    setTextColor(GameLingoTVCaptionStyle.textColor(context))
                    setBackgroundColor(GameLingoTVCaptionStyle.background(context))
                    gravity = Gravity.CENTER
                    maxLines = 4
                    includeFontPadding = false
                    setPadding(3, 1, 3, 1)
                    isFocusable = false
                    isClickable = false
                }, LayoutParams(-2,-2))
            }
            requestLayout()
            invalidate()
        }
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val w=width.toFloat();val h=height.toFloat()
            if(w<=0||h<=0)return
            val fw=min(w,h*packet.aspect);val fh=fw/packet.aspect
            val ox=(w-fw)/2;val oy=(h-fh)/2
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            for(block in packet.blocks){
                val bh=block.height ?: continue
                val left=(ox+fw*(block.x-block.width/2)-1).coerceIn(ox,ox+fw)
                val right=(ox+fw*(block.x+block.width/2)+1).coerceIn(left,ox+fw)
                val top=(oy+fh*(block.bottom-bh)-1).coerceIn(oy,oy+fh)
                val bottom=(oy+fh*block.bottom+1).coerceIn(top,oy+fh)
                val bw=(right-left-2).toInt();val available=bottom-top-2
                if(bw<2||available<2)continue
                val bg=block.background?.or(0xff000000.toInt()) ?: Color.BLACK
                val fallback=if(Color.luminance(bg)>.179f)Color.BLACK else Color.WHITE
                val candidate=block.ink?.or(0xff000000.toInt()) ?: fallback
                val light=Color.luminance(bg);val inkLight=Color.luminance(candidate)
                val ink=if((maxOf(light,inkLight)+.05f)/(minOf(light,inkLight)+.05f)>=4.5f)candidate else fallback
                val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=ink}
                fun layout(size:Float):StaticLayout {
                    tp.textSize=size
                    return StaticLayout.Builder.obtain(block.text,0,block.text.length,tp,bw)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
                }
                var low=1f;var high=(available*packet.font).coerceAtLeast(1f)
                if(layout(low).height>available)continue
                repeat(9){val mid=(low+high)/2;if(layout(mid).height<=available)low=mid else high=mid}
                val result=layout(low)
                paint.color=bg
                canvas.save()
                canvas.clipRect(left,top,right,bottom)
                canvas.drawRect(left,top,right,bottom,paint)
                canvas.translate(left+1,top+(bottom-top-result.height)/2)
                result.draw(canvas)
                canvas.restore()
            }
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = (r-l).toFloat()
            val h = (b-t).toFloat()
            if (w <= 0f || h <= 0f) return
            val frameW = min(w, h*packet.aspect)
            val frameH = frameW / packet.aspect
            val ox = (w-frameW)/2
            val oy = (h-frameH)/2
            packet.blocks.filter { it.height == null }.forEachIndexed { index, block ->
                val child = getChildAt(index) ?: return@forEachIndexed
                val width = (frameW*block.width).roundToInt().coerceIn(1,w.toInt())
                if(child is TextView) {
                    // Scale to the actual video, not the TV's logical density.
                    val nominal = (frameH * .032f * packet.font).coerceAtLeast(8f)
                    val probe = TextPaint(child.paint).apply { textSize=nominal }
                    val textWidth = probe.measureText(block.text)
                    val fitted = if(textWidth > width-6 && width>8) (nominal*(width-6)/textWidth).coerceAtLeast(nominal*.60f) else nominal
                    if(kotlin.math.abs(child.textSize-fitted)>.1f)child.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,fitted)
                }
                child.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(frameH.roundToInt().coerceAtLeast(1),View.MeasureSpec.AT_MOST))
                val x = (ox + frameW*block.x-width/2f).roundToInt().coerceIn(0,w.toInt()-width)
                val height = child.measuredHeight.coerceAtMost(h.toInt())
                val y = (oy+frameH*block.bottom-height).roundToInt().coerceIn(0,h.toInt()-height)
                child.layout(x,y,x+width,y+height)
            }
        }
    }
}
