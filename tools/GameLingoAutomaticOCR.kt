package cz.titulky.tv

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/** One native OCR call and one provider request at a time. Frame buffers are
 * copied only for the scheduled region, never queued or retained by this class. */
internal class GameLingoAutomaticOCR(
    private val context: Context,
    private val zones: List<AutoZone>,
    private val valid: () -> Boolean,
    private val output: (GameLingoZonePacket) -> Unit
) {
    private val ui=Handler(Looper.getMainLooper())
    private val closed=AtomicBoolean(false)
    private val busy=AtomicBoolean(false)
    private val frameAt=AtomicLong(0)
    private val lastAttempt=AtomicLong(0)
    private val source=GameLingoLanguages.game(context)
    private val target=GameLingoLanguages.target(context)
    private val translator=Prekladac(context,target,source)
    private val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val enabled=zones.filter { it.enabled && it.rect.valid() }.take(10)
    private val tracker=GameLingoAutomaticTracker()
    private val translations=mutableMapOf<String,String>()
    private val retries=mutableMapOf<String,Long>()
    private var translating=false
    private var next=0
    @Volatile private var aspect=16f/9f
    private var lastPacket: GameLingoZonePacket?=null
    private var publishedAt=0L
    private data class Lane(val gate: GameLingoFrameGate = GameLingoFrameGate(1000L), @Volatile var confirming: Boolean=true)
    private val lanes=enabled.associate { it.id to Lane() }
    private val tick=object:Runnable {
        override fun run() {
            if(closed.get())return
            if(!valid()){close();return}
            val now=SystemClock.elapsedRealtime()
            if(now-frameAt.get()>2000L)tracker.clear()
            publish(now)
            translate(now)
            ui.postDelayed(this,350L)
        }
    }
    init { ui.post(tick) }
    fun alive()=!closed.get() && valid()
    fun close() {
        if(!closed.compareAndSet(false,true))return
        ui.removeCallbacks(tick)
        ui.post { tracker.clear();translations.clear();retries.clear();runCatching{reader.close()};runCatching{translator.zavri()} }
    }
    /** Called on a decoder/capture thread; readPixels must return a top-left ARGB crop. */
    fun offer(w: Int,h: Int,readPixels: (Int,Int,Int,Int)->IntArray) {
        if(!alive()||w<32||h<32)return
        val now=SystemClock.elapsedRealtime()
        frameAt.set(now)
        aspect=(w.toFloat()/h).coerceIn(.5f,4f)
        if(enabled.isEmpty()||now-lastAttempt.get()<100L||!busy.compareAndSet(false,true))return
        lastAttempt.set(now)
        var bitmap:Bitmap?=null
        try {
            val zone=enabled[next%enabled.size];next=(next+1)%enabled.size
            val r=zone.rect
            val x=(r.left*w).toInt().coerceIn(0,w-32)
            val y=(r.top*h).toInt().coerceIn(0,h-32)
            val cw=((r.right*w).toInt()-x).coerceIn(32,w-x)
            val ch=((r.bottom*h).toInt()-y).coerceIn(32,h-y)
            val pixels=readPixels(x,y,cw,ch)
            require(pixels.size==cw*ch)
            var hash=GameLingoFrameGate.seed(cw,ch)
            // Bound sampling to the selected region, using the last successful read.
            for(row in 0 until ch step max(1,ch/64))for(col in 0 until cw step max(1,cw/192))
                hash=GameLingoFrameGate.mix(hash,pixels[row*cw+col])
            val lane=lanes.getValue(zone.id)
            if(!lane.confirming&&lane.gate.canSkip(hash,now)){busy.set(false);return}
            val image=Bitmap.createBitmap(pixels,cw,ch,Bitmap.Config.ARGB_8888)
            bitmap=image
            reader.process(InputImage.fromBitmap(image,0)).addOnCompleteListener { task ->
                try {
                    if(!alive()||!task.isSuccessful)return@addOnCompleteListener
                    val blocks=task.result.textBlocks.mapNotNull { b ->
                        val box=b.boundingBox ?: return@mapNotNull null
                        val text=b.text.trim().replace(Regex("\\s+")," ")
                        if(text.count{it.isLetter()}<2)return@mapNotNull null
                        val rect=AutoRect(((x+box.left).toFloat()/w).coerceIn(0f,1f),
                            ((y+box.top).toFloat()/h).coerceIn(0f,1f),
                            ((x+box.right).toFloat()/w).coerceIn(0f,1f),
                            ((y+box.bottom).toFloat()/h).coerceIn(0f,1f))
                        if(rect.width<=0f||rect.height<=0f|| (zone.centered&&kotlin.math.abs(rect.center-.5f)>.2f))null else AutoBlock(text,rect)
                    }.sortedByDescending { it.rect.bottom-kotlin.math.abs(it.rect.center-.5f) }.take(if(zone.centered)1 else 8)
                    tracker.observe(zone.id,blocks,SystemClock.elapsedRealtime())
                    lane.confirming=tracker.tracks.any { it.zone==zone.id&&it.confirmations<2 }
                    lane.gate.record(hash,now)
                    publish(SystemClock.elapsedRealtime())
                    translate(SystemClock.elapsedRealtime())
                } finally { image.recycle();busy.set(false) }
            }
        } catch(e:Exception) {
            bitmap?.recycle();busy.set(false)
        }
    }
    private fun publish(now:Long) {
        if(!alive())return
        tracker.expire(now)
        val ids=tracker.tracks.map{it.id}.toSet()
        translations.keys.retainAll(ids);retries.keys.retainAll(ids)
        val chosen=mutableListOf<Pair<AutoBlock,GameLingoZoneBlock>>()
        for(zone in enabled)for(track in tracker.tracks.filter{it.zone==zone.id}) {
            val value=translations[track.id]?:continue
            if(chosen.any{GameLingoAutomaticTracker.same(it.first,track.block)})continue
            if(chosen.size<32)chosen+=track.block to track.block.output(value)
        }
        val packet=GameLingoZonePacket(aspect,1f,chosen.map{it.second})
        if(packet!=lastPacket||(packet.blocks.isNotEmpty()&&now-publishedAt>=800L)){
            output(packet);lastPacket=packet;publishedAt=now
        }
    }
    private fun translate(now:Long) {
        if(!alive()||translating)return
        val t=tracker.tracks.firstOrNull{it.confirmations>=2&&it.id !in translations&&now>=(retries[it.id]?:0L)}?:return
        translating=true
        fun completed(value:String?) {
            ui.post {
                translating=false
                if(!alive())return@post
                val at=SystemClock.elapsedRealtime()
                if(tracker.tracks.any{it.id==t.id&&at-it.seen<=5000L}){
                    if(!value.isNullOrBlank()) translations[t.id]=GameLingoGlossaryStore.apply(context,t.block.text,value,source,target)
                    else retries[t.id]=at+2000L
                }
                publish(at)
            }
        }
        try{translator.preloz(t.block.text,{completed(it)},{completed(null)})}catch(e:Exception){completed(null)}
    }
}
