package cz.titulky.tv
import org.junit.Test
import org.junit.Assert.*
import java.util.Base64

class GameLingoZonePacketTest {
    private fun parse(fields: List<String>) = GameLingoZonePacket.parse(fields) { Base64.getDecoder().decode(it) }
    private val header = listOf("PCZ1","1.7778","1","1")
    private val block = listOf("SGVsbG8=","0.5","0.9","0.3")
    @Test fun snapshotAndClear() {
        assertEquals("Hello",parse(header+block)!!.blocks.single().text)
        assertTrue(parse(listOf("PCZ1","1.7778","1","0"))!!.blocks.isEmpty())
        val many = listOf("PCZ1","1.7778","1","32") + List(32){block}.flatten()
        assertEquals(32,parse(many)!!.blocks.size)
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
