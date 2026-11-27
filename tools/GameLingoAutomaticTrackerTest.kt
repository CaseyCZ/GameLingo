package cz.titulky.tv
import org.junit.Test
import org.junit.Assert.*
class GameLingoAutomaticTrackerTest {
    @Test fun ignoredHudAndExplicitPromptsDoNotSuppressDialogue(){
        val ignored = GameLingoIgnoredLines.entries("  Inventory  \nSPEED LIMIT")
        val lines = listOf("Inventory", "Press [A] to continue", "I need the inventory.", "No!")
        assertEquals(listOf("I need the inventory.", "No!"),
            lines.filter { GameLingoYellowOCRFilter.keepsAutomaticDialogue(it,true,ignored) })
        assertTrue(GameLingoYellowOCRFilter.keepsAutomaticDialogue("Press [A] to continue",false,ignored))
        assertFalse(GameLingoYellowOCRFilter.keepsAutomaticDialogue("speed limit",false,ignored))
        assertTrue(GameLingoYellowOCRFilter.keepsAutomaticDialogue("I pressed A to continue.",true,ignored))
    }
    @Test fun customMenuGeometryAndControllerLabels(){
        val block=AutoBlock("Play",AutoRect(.01f,.01f,.06f,.04f))
        val output=block.output("Hrát")
        assertEquals(.05f,output.width,.0001f)
        assertEquals(.03f,output.height!!,.0001f)
        assertEquals(.08f,output.legacyGeometry().width,.0001f)
        for (label in listOf("LT","RT","LB","R1")) {
            assertFalse(GameLingoYellowOCRFilter.keepsAutomaticDialogue(label,false,emptySet()))
        }
        assertTrue(GameLingoYellowOCRFilter.keepsAutomaticDialogue("PLAY",false,emptySet()))
    }
    @Test fun automaticMinimumHeightUsesCropAndPreservesDefault(){
        assertTrue(GameLingoYellowOCRFilter.meetsMinimumHeight(0, 0, 0))
        assertFalse(GameLingoYellowOCRFilter.meetsMinimumHeight(9, 500, 2))
        assertTrue(GameLingoYellowOCRFilter.meetsMinimumHeight(10, 500, 2))
        assertFalse(GameLingoYellowOCRFilter.meetsMinimumHeight(0, 500, 2))
        assertTrue(GameLingoYellowOCRFilter.meetsMinimumHeight(10, 500, 1))
        assertFalse(GameLingoYellowOCRFilter.meetsMinimumHeight(10, 500, 5))
    }
    @Test fun independentZonesAndStableTracks(){
        val t=GameLingoAutomaticTracker()
        val a=AutoBlock("One",AutoRect(.2f,.2f,.4f,.3f))
        val b=AutoBlock("Two",AutoRect(.5f,.5f,.8f,.6f))
        t.observe("A",listOf(a,b),100)
        val ids=t.tracks.map{it.id}.toSet()
        t.observe("A",listOf(b,a),200)
        assertEquals(ids,t.tracks.map{it.id}.toSet())
        assertTrue(t.tracks.all{it.confirmations==2})
        t.observe("B",listOf(a),210)
        t.observe("A",emptyList(),220)
        assertEquals(1,t.tracks.size);assertEquals("B",t.tracks.single().zone)
        t.expire(5300);assertTrue(t.tracks.isEmpty())
    }
    @Test fun smallPositionAndColorChangesDoNotFlicker(){
        val t=GameLingoAutomaticTracker()
        val a=AutoBlock("Play",AutoRect(.2f,.2f,.4f,.3f),0xffcc00,0)
        val b=a.copy(rect=AutoRect(.201f,.2f,.401f,.3f),background=0xfbc900)
        t.observe("A",listOf(a),100)
        t.observe("A",listOf(b),200)
        assertEquals(a,t.tracks.single().block)
        t.observe("A",listOf(b.copy(rect=AutoRect(.22f,.2f,.42f,.3f))),300)
        assertEquals(.22f,t.tracks.single().block.rect.left,.0001f)
    }
    @Test fun changedTextCannotReuseTranslation(){
        val t=GameLingoAutomaticTracker()
        val r=AutoRect(.2f,.2f,.4f,.3f)
        t.observe("A",listOf(AutoBlock("One",r)),100)
        val id=t.tracks.single().id
        t.observe("A",listOf(AutoBlock("Two",r)),200)
        assertNotEquals(id,t.tracks.single().id)
        assertEquals(1,t.tracks.single().confirmations)
    }
}
