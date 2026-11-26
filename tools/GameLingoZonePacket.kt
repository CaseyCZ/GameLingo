package cz.titulky.tv

import android.util.Base64

/** Full bounded snapshot; only dispatched after HMAC and replay validation. */
internal data class GameLingoZoneBlock(val text: String, val x: Float, val bottom: Float, val width: Float)
internal data class GameLingoZonePacket(val aspect: Float, val font: Float, val blocks: List<GameLingoZoneBlock>) {
    fun encode(): String {
        val selected=blocks.take(32)
        val limit=minOf(512,4608/maxOf(1,selected.size))
        val fields=selected.flatMap { b ->
            var text=b.text
            while(text.toByteArray(Charsets.UTF_8).size>limit)text=text.substring(0,text.offsetByCodePoints(text.length,-1))
            if(text.isBlank())emptyList() else listOf(
                Base64.encodeToString(text.toByteArray(Charsets.UTF_8),Base64.NO_WRAP),
                "%.5f".format(java.util.Locale.US,b.x),"%.5f".format(java.util.Locale.US,b.bottom),"%.5f".format(java.util.Locale.US,b.width))
        }
        return (listOf("PCZ1",aspect.toString(),font.toString(),(fields.size/4).toString())+fields).joinToString("\t")
    }
    fun legacy(sequence: Long): ByteArray {
        val b=blocks.firstOrNull()
        val text=b?.text.orEmpty().replace("\\","\\\\").replace("\t"," ").replace("\n","\\n")
        return listOf("PCT1",sequence.toString(),if(b==null)"0" else "1800",text,"",
            (b?.x?:.5f).toString(),(b?.bottom?:.93f).toString(),(b?.width?:.62f).toString(),font.toString(),"0").joinToString("\t").toByteArray(Charsets.UTF_8)
    }
    companion object {
        fun parse(fields: List<String>, decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) }): GameLingoZonePacket? = runCatching {
            require(fields.firstOrNull() == "PCZ1" && fields.size >= 4)
            val aspect = fields[1].toFloat()
            val font = fields[2].toFloat()
            val count = fields[3].toInt()
            require(aspect.isFinite() && aspect in .5f..4f && font.isFinite() && font in .5f..2.5f)
            require(count in 0..32 && fields.size == 4+count*4)
            val blocks = (0 until count).map { i ->
                val p = 4+i*4
                require(fields[p].length <= 684)
                val bytes = decode(fields[p])
                require(bytes.size in 1..512)
                val text = String(bytes, Charsets.UTF_8)
                require(text.isNotBlank())
                val x = fields[p+1].toFloat()
                val bottom = fields[p+2].toFloat()
                val width = fields[p+3].toFloat()
                require(x.isFinite() && x in 0f..1f && bottom.isFinite() && bottom in .05f..1f &&
                    width.isFinite() && width in .08f..1f && x-width/2 >= -.0001f && x+width/2 <= 1.0001f)
                GameLingoZoneBlock(text, x, bottom, width)
            }
            GameLingoZonePacket(aspect, font, blocks)
        }.getOrNull()
    }
}
