package cz.titulky.tv

import kotlin.math.max
import kotlin.math.min
import java.util.UUID

internal data class AutoRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right-left
    val height get() = bottom-top
    val center get() = (left+right)/2
    fun valid() = listOf(left,top,right,bottom).all { it.isFinite() && it in 0f..1f } && width >= .07999f && height >= .03999f
    fun overlaps(b: AutoRect): Boolean {
        val i = max(0f,min(right,b.right)-max(left,b.left))*max(0f,min(bottom,b.bottom)-max(top,b.top))
        val union = width*height+b.width*b.height-i
        return union > 0 && i/union >= .35f
    }
}
internal data class AutoZone(val id: String, val name: String, val enabled: Boolean, val rect: AutoRect, val centered: Boolean)
internal data class AutoBlock(val text: String, val rect: AutoRect, val background: Int? = null, val ink: Int? = null) {
    fun output(translated: String): GameLingoZoneBlock {
        // Use the source box for replacement; the old protocol expands tiny labels on encode.
        return GameLingoZoneBlock(translated,rect.center,rect.bottom,rect.width,rect.height,background,ink)
    }
}
internal class GameLingoAutomaticTracker {
    data class Track(val id: String, val zone: String, val block: AutoBlock, val confirmations: Int, val seen: Long)
    private var current = emptyList<Track>()
    val tracks: List<Track> get() = current
    fun clear() { current = emptyList() }
    fun expire(now: Long) { current = current.filter { now-it.seen <= 5000L } }
    fun observe(zone: String, blocks: List<AutoBlock>, now: Long) {
        expire(now)
        val previous = current.filter { it.zone == zone }.toMutableList()
        val next = current.filter { it.zone != zone }.toMutableList()
        for (block in blocks.take(8)) {
            if (next.size >= 64) break
            if (next.any { it.zone == zone && same(it.block,block) }) continue
            val index = previous.indexOfFirst { same(it.block,block) }
            next += if(index >= 0) previous.removeAt(index).let { it.copy(block=block,confirmations=min(2,it.confirmations+1),seen=now) }
                else Track(UUID.randomUUID().toString(),zone,block,1,now)
        }
        current=next
    }
    companion object {
        fun normalize(text: String) = text.trim().replace(Regex("\\s+")," ").lowercase(java.util.Locale.ROOT)
        fun same(a: AutoBlock,b: AutoBlock) = normalize(a.text)==normalize(b.text) && a.rect.overlaps(b.rect)
    }
}
