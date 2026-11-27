package cz.titulky.tv
import org.junit.Test
import org.junit.Assert.*
import java.util.Base64

class GameLingoZonePacketTest {
    private fun parse(fields: List<String>) = GameLingoZonePacket.parse(fields) { Base64.getDecoder().decode(it) }
    private val header = listOf("PCZ1","1.7778","1","1")
    private val block = listOf("SGVsbG8=","0.5","0.9","0.3")
    @Test fun fontScaleAcceptsNewEndpoints() {
        for (font in listOf("0.1","2.0")) {
            assertEquals(font.toFloat(),parse(listOf("PCZ1","1.7778",font,"1")+block)!!.font,.0001f)
        }
        assertNull(parse(listOf("PCZ1","1.7778","0.09","1")+block))
    }
    @Test fun snapshotAndClear() {
        assertEquals("Hello",parse(header+block)!!.blocks.single().text)
        assertTrue(parse(listOf("PCZ1","1.7778","1","0"))!!.blocks.isEmpty())
        val many = listOf("PCZ1","1.7778","1","32") + List(32){block}.flatten()
        assertEquals(32,parse(many)!!.blocks.size)
    }
    @Test fun adaptiveGeometryAndPaletteAreValidated() {
        val header=listOf("PCZ2","1.7778","1","1")
        val fields=listOf("SGVsbG8=","0.05","0.04","0.02","0.03","16776960","0")
        val b=parse(header+fields)!!.blocks.single()
        assertEquals(.03f,b.height!!,.0001f)
        assertEquals(16776960,b.background!!)
        assertEquals(0,b.ink!!)
        assertNull(parse(header+fields.toMutableList().apply{this[4]="0.5"}))
        assertNull(parse(header+fields.toMutableList().apply{this[5]="16777216"}))
        assertNull(parse(header+fields.toMutableList().apply{this[4]="NaN"}))
    }
    @Test fun invalidCountAndGeometryAreRejected() {
        assertNull(parse(header))
        assertNull(parse(listOf("PCZ1","1","1","33") + List(33){block}.flatten()))
        assertNull(parse(header+listOf("SGVsbG8=","NaN","0.9","0.3")))
        assertNull(parse(header+listOf("SGVsbG8=","0.01","0.9","0.3")))
        assertNull(parse(header+listOf("SGVsbG8=","0.5","0.9","2")))
        assertNull(parse(header+listOf("%%%","0.5","0.9","0.3")))
        assertNull(parse(header+listOf(Base64.getEncoder().encodeToString(ByteArray(513){65}),"0.5","0.9","0.3")))
    }
}
