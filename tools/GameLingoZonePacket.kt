package cz.titulky.tv

import android.util.Base64

/** Authenticated bounded snapshots. PCZ2 adds source geometry and RGB palette. */
internal data class GameLingoZoneBlock(val text: String, val x: Float, val bottom: Float, val width: Float,
    val height: Float? = null, val background: Int? = null, val ink: Int? = null) {
    fun legacyGeometry(): GameLingoZoneBlock {
        val w = width.coerceIn(.08f,1f)
        return copy(x=x.coerceIn(w/2,1-w/2),bottom=bottom.coerceIn(.05f,1f),width=w,height=null,background=null,ink=null)
    }
}
internal data class GameLingoZonePacket(val aspect: Float, val font: Float, val blocks: List<GameLingoZoneBlock>) {
    fun encode(adaptive: Boolean = true): String {
        val v2=adaptive && blocks.isNotEmpty() && blocks.all { it.height != null }
        val selected=blocks.take(32)
        val limit=minOf(512,(if(v2) 3000 else 4608)/maxOf(1,selected.size))
        fun decimal(n:Float)="%.5f".format(java.util.Locale.US,n)
        val fields=selected.flatMap { original ->
            val b=if(v2) original else original.legacyGeometry()
            var text=b.text
            while(text.toByteArray(Charsets.UTF_8).size>limit)text=text.substring(0,text.offsetByCodePoints(text.length,-1))
            if(text.isBlank())emptyList() else listOf(
                Base64.encodeToString(text.toByteArray(Charsets.UTF_8),Base64.NO_WRAP),
                decimal(b.x),decimal(b.bottom),decimal(b.width)) +
                if(v2) listOf(decimal(b.height!!), (b.background ?: -1).toString(), (b.ink ?: -1).toString()) else emptyList()
        }
        val stride=if(v2) 7 else 4
        return (listOf(if(v2)"PCZ2" else "PCZ1",aspect.toString(),font.toString(),(fields.size/stride).toString())+fields).joinToString("\t")
    }
    fun legacy(sequence: Long): ByteArray {
        val b=blocks.firstOrNull()?.legacyGeometry()
        val text=b?.text.orEmpty().replace("\\","\\\\").replace("\t"," ").replace("\n","\\n")
        return listOf("PCT1",sequence.toString(),if(b==null)"0" else "1800",text,"",
            (b?.x?:.5f).toString(),(b?.bottom?:.93f).toString(),(b?.width?:.62f).toString(),font.toString(),"0").joinToString("\t").toByteArray(Charsets.UTF_8)
    }
    companion object {
        fun parse(fields: List<String>, decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) }): GameLingoZonePacket? = runCatching {
            require(fields.firstOrNull() in listOf("PCZ1","PCZ2") && fields.size >= 4)
            val v2=fields[0]=="PCZ2"
            val stride=if(v2)7 else 4
            val aspect=fields[1].toFloat()
            val font=fields[2].toFloat()
            val count=fields[3].toInt()
            require(aspect.isFinite() && aspect in .5f..4f && font.isFinite() && font in 0f..2.5f)
            require(count in 0..32 && fields.size==4+count*stride)
            val blocks=(0 until count).map { i ->
                val p=4+i*stride
                require(fields[p].length<=684)
                val bytes=decode(fields[p])
                require(bytes.size in 1..512)
                val text=String(bytes,Charsets.UTF_8)
                require(text.isNotBlank())
                val x=fields[p+1].toFloat()
                val bottom=fields[p+2].toFloat()
                val width=fields[p+3].toFloat()
                require(x.isFinite() && x in 0f..1f && bottom.isFinite() && bottom in (if(v2) .001f else .05f)..1f &&
                    width.isFinite() && width in (if(v2) .001f else .08f)..1f &&
                    x-width/2 >= -.0001f && x+width/2 <= 1.0001f)
                if(v2) {
                    val height=fields[p+4].toFloat()
                    val bg=fields[p+5].toInt()
                    val ink=fields[p+6].toInt()
                    require(height.isFinite() && height in .001f..1f && height<=bottom+.0001f)
                    require(bg in -1..0xffffff && ink in -1..0xffffff)
                    GameLingoZoneBlock(text,x,bottom,width,height,bg.takeIf{it>=0},ink.takeIf{it>=0})
                } else GameLingoZoneBlock(text,x,bottom,width)
            }
            GameLingoZonePacket(aspect,font,blocks)
        }.getOrNull()
    }
}
