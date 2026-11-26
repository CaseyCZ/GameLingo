package cz.titulky.tv

import android.content.Context
import android.graphics.Color
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
    private val expire = Runnable { clear() }
    fun clear() {
        ui.removeCallbacks(expire)
        root?.let { runCatching { wm.removeViewImmediate(it) } }
        root = null
    }
    fun show(packet: GameLingoZonePacket) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (packet.blocks.isEmpty() || !PrijemService.bezi || !Settings.canDrawOverlays(context)) {
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
                alpha = .8f
            }
            if (runCatching { wm.addView(candidate, lp) }.isFailure) return
            root = candidate
        }
        view.update(packet)
        ui.removeCallbacks(expire)
        ui.postDelayed(expire, 2200)
    }

    private class ZoneView(c: Context) : FrameLayout(c) {
        private var packet = GameLingoZonePacket(16f/9f, 1f, emptyList())
        init { setBackgroundColor(Color.TRANSPARENT); isFocusable = false; isClickable = false }
        fun update(next: GameLingoZonePacket) {
            if (packet == next && childCount == next.blocks.size) return
            packet = next
            removeAllViews()
            next.blocks.forEach { block ->
                addView(TextView(context).apply {
                    text = block.text
                    textSize = 22f * next.font
                    setTextColor(GameLingoTVCaptionStyle.textColor(context))
                    setBackgroundColor(GameLingoTVCaptionStyle.background(context))
                    gravity = Gravity.CENTER
                    maxLines = 4
                    setPadding(8, 3, 8, 3)
                    isFocusable = false
                    isClickable = false
                }, LayoutParams(-2,-2))
            }
            requestLayout()
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = (r-l).toFloat()
            val h = (b-t).toFloat()
            if (w <= 0f || h <= 0f) return
            val frameW = min(w, h*packet.aspect)
            val frameH = frameW / packet.aspect
            val ox = (w-frameW)/2
            val oy = (h-frameH)/2
            packet.blocks.forEachIndexed { index, block ->
                val child = getChildAt(index) ?: return@forEachIndexed
                val width = (frameW*block.width).roundToInt().coerceIn(1,w.toInt())
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
