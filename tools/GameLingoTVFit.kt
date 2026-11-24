package cz.titulky.tv

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** The receiver must remain a single screen even at enlarged TV display density.
 * Measure real content first, then uniformly fit inside a fixed safe viewport. */
class GameLingoTVFit(a: Activity, private val content: View) : FrameLayout(a) {
    init {
        val inset = GameLingoTVUi.dp(a, 12)
        setPadding(inset, inset, inset, inset)
        background = GameLingoVisualKit.pageBackground(a)
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = View.MeasureSpec.getSize(widthSpec)
        val h = View.MeasureSpec.getSize(heightSpec)
        content.measure(View.MeasureSpec.makeMeasureSpec((w-paddingLeft-paddingRight).coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(w,h)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availableW = (r-l-paddingLeft-paddingRight).coerceAtLeast(1)
        val availableH = (b-t-paddingTop-paddingBottom).coerceAtLeast(1)
        val w = content.measuredWidth.coerceAtLeast(1)
        val h = content.measuredHeight.coerceAtLeast(1)
        val factor = minOf(1f, availableW.toFloat()/w, availableH.toFloat()/h)
        val x = paddingLeft + ((availableW-w*factor)/2f).toInt()
        val y = paddingTop + ((availableH-h*factor)/2f).toInt()
        content.pivotX=0f; content.pivotY=0f
        content.scaleX=factor; content.scaleY=factor
        content.layout(x,y,x+w,y+h)
    }
}
