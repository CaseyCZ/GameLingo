package cz.titulky.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
    fun readyForFrame() = alive() && !busy.get()
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
            val x=kotlin.math.ceil(r.left*w).toInt().coerceIn(0,w-1)
            val y=kotlin.math.ceil(r.top*h).toInt().coerceIn(0,h-1)
            val cw=(r.right*w).toInt().coerceAtMost(w)-x
            val ch=(r.bottom*h).toInt().coerceAtMost(h)-y
            if(cw<16||ch<16){busy.set(false);return}
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
                    val ignored = GameLingoOCRConfig.ignoredBlueEntries
                    val filterMenus = GameLingoOCRConfig.filterMenuText
                    val minimumHeight = GameLingoOCRConfig.minBlueHeight
                    val blocks=task.result.textBlocks.mapNotNull { b ->
                        // Filter individual OCR lines before joining them, not the
                        // combined block: an ignored HUD label can share a block
                        // with genuine dialogue.
                        val lines = b.lines.filter {
                            GameLingoYellowOCRFilter.keepsAutomaticDialogue(it.text, filterMenus, ignored) &&
                                GameLingoYellowOCRFilter.meetsMinimumHeight(it.boundingBox?.height() ?: 0, ch, minimumHeight)
                        }
                        if (lines.isEmpty()) return@mapNotNull null
                        val box = if (lines.size == b.lines.size) b.boundingBox else {
                            val boxes = lines.mapNotNull { it.boundingBox }
                            boxes.firstOrNull()?.let { first ->
                                android.graphics.Rect(first).apply { boxes.drop(1).forEach { union(it) } }
                            }
                        } ?: return@mapNotNull null
                        val text=lines.joinToString(" ") { it.text }.trim().replace(Regex("\\s+")," ")
                        if(text.count{it.isLetter()}<2)return@mapNotNull null
                        val rect=AutoRect(((x+box.left).toFloat()/w).coerceIn(0f,1f),
                            ((y+box.top).toFloat()/h).coerceIn(0f,1f),
                            ((x+box.right).toFloat()/w).coerceIn(0f,1f),
                            ((y+box.bottom).toFloat()/h).coerceIn(0f,1f))
                        if(rect.width<.001f||rect.height<.001f|| (zone.centered&&kotlin.math.abs(rect.center-.5f)>.2f))null else {
                            val palette=palette(pixels,cw,ch,box)
                            AutoBlock(text,rect,palette.first,palette.second)
                        }
                    }.sortedByDescending { it.rect.bottom-kotlin.math.abs(it.rect.center-.5f) }.take(if(zone.centered)1 else 8)
                    tracker.observe(zone.id,blocks,SystemClock.elapsedRealtime())
                    lane.confirming=tracker.tracks.any { it.zone==zone.id&&it.confirmations<2 }
                    lane.gate.record(hash,now)
                    publish(SystemClock.elapsedRealtime())
                    translate(SystemClock.elapsedRealtime())
                } catch (_: Exception) {
                    // A malformed or failed recognition never replaces a zone.
                } finally { image.recycle();busy.set(false) }
            }
        } catch(e:Exception) {
            bitmap?.recycle();busy.set(false)
        }
    }
    // Bounded edge histogram: dominant background, then high-contrast ink.
    private fun palette(pixels:IntArray,w:Int,h:Int,box:android.graphics.Rect):Pair<Int?,Int?> {
        val l=box.left.coerceIn(0,w-1);val r=(box.right-1).coerceIn(l,w-1)
        val t=box.top.coerceIn(0,h-1);val b=(box.bottom-1).coerceIn(t,h-1)
        fun sample(x:Int,y:Int)=pixels[y*w+x] and 0xffffff
        fun key(c:Int)=((c shr 21) and 7)*64+((c shr 13) and 7)*8+((c shr 5) and 7)
        val edges=ArrayList<Int>(48)
        for(i in 0 until 12){
            val x=l+(r-l)*i/11;val y=t+(b-t)*i/11
            edges+=sample(x,t);edges+=sample(x,b);edges+=sample(l,y);edges+=sample(r,y)
        }
        fun dominant(colors:List<Int>):Int? {
            val group=colors.groupBy(::key).values.maxByOrNull{it.size} ?: return null
            if(group.size<maxOf(3,colors.size/4))return null
            return (group.map{it shr 16 and 255}.average().toInt() shl 16) or
                (group.map{it shr 8 and 255}.average().toInt() shl 8) or group.map{it and 255}.average().toInt()
        }
        val bg=dominant(edges) ?: return null to null
        val light=Color.luminance(bg or 0xff000000.toInt())
        val candidates=ArrayList<Int>(192)
        for(y in 0 until 8)for(x in 0 until 24){
            val c=sample(l+(r-l)*x/23,t+(b-t)*y/7)
            val v=Color.luminance(c or 0xff000000.toInt())
            if((maxOf(v,light)+.05f)/(minOf(v,light)+.05f)>=4.5f)candidates+=c
        }
        return bg to dominant(candidates)
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
        val font=if(GameLingoTVUi.isTV(context)) context.getSharedPreferences("gamelingo",Context.MODE_PRIVATE).getFloat("directGreen3",1f)
            else if(Propojeni.Vysilac.cilIp!=null) TvProfily.nastaveni(context).getFloat("tvPismo",1f)
            else GameLingoPhoneSubtitleStyle.scale(context)
        val scale=if(font.isFinite())font.coerceIn(.1f,2f) else 1f
        val packet=GameLingoZonePacket(aspect,scale,chosen.map{it.second})
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
                    if(!value.isNullOrBlank() && !GameLingoOCRConfig.expired(context, at-now)) {
                        val corrected = GameLingoGlossaryStore.apply(context,t.block.text,value,source,target)
                        translations[t.id] = corrected
                        GameLingoTranslationHistory.append("Automatický",source,target,t.block.text,corrected)
                    } else retries[t.id]=at+2000L
                }
                publish(at)
            }
        }
        try{translator.preloz(t.block.text,{completed(it)},{completed(null)})}catch(e:Exception){completed(null)}
    }
}
