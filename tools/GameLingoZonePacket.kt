package cz.titulky.tv

import android.util.Base64

/** Full bounded snapshot; only dispatched after HMAC and replay validation. */
internal data class GameLingoZoneBlock(val text: String, val x: Float, val bottom: Float, val width: Float)
internal data class GameLingoZonePacket(val aspect: Float, val font: Float, val blocks: List<GameLingoZoneBlock>) {
    companion object {
        fun parse(fields: List<String>, decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) }): GameLingoZonePacket? = runCatching {
            require(fields.firstOrNull() == "PCZ1" && fields.size >= 4)
            val aspect = fields[1].toFloat()
            val font = fields[2].toFloat()
            val count = fields[3].toInt()
            require(aspect.isFinite() && aspect in .5f..4f && font.isFinite() && font in .5f..2.5f)
            require(count in 0..10 && fields.size == 4+count*4)
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
