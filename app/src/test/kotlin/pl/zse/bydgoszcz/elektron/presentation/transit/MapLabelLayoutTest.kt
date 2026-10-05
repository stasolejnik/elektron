package pl.zse.bydgoszcz.elektron.presentation.transit

import org.junit.Assert.*
import org.junit.Test

class MapLabelLayoutTest {
    private val viewport = MapLabelRect(0f, 0f, 300f, 300f)

    @Test fun prefersRightAndKeepsAwayFromTheStopIcon() {
        val marker = MapLabelRect(90f, 90f, 110f, 110f)
        val result = placeMapLabel(100f, 100f, 80f, 24f, 17f, viewport, listOf(marker))!!
        assertEquals(117f, result.left, 0f)
        assertFalse(result.overlaps(marker))
    }

    @Test fun triesOtherSidesWhenALabelWouldCoverAnotherStopOrControl() {
        val right = MapLabelRect(115f, 80f, 220f, 120f)
        val result = placeMapLabel(100f, 100f, 80f, 24f, 17f, viewport, listOf(right))!!
        assertEquals(3f, result.left, 0f)
        assertFalse(result.overlaps(right))
    }

    @Test fun hidesNamesWhenAllPositionsAreObstructed() {
        assertNull(placeMapLabel(100f, 100f, 80f, 24f, 17f, viewport, listOf(viewport)))
    }

    @Test fun longNamesAndLargeFontsStayInsideViewport() {
        assertNull(placeMapLabel(150f, 150f, 310f, 60f, 17f, viewport, emptyList()))
        val result = placeMapLabel(280f, 150f, 160f, 60f, 17f, viewport, emptyList())!!
        assertTrue(viewport.contains(result))
    }

    @Test fun successiveNamesNeverOverlap() {
        val occupied = mutableListOf<MapLabelRect>()
        listOf(100f to 100f, 125f to 110f, 160f to 120f, 180f to 150f).forEach { (x, y) ->
            placeMapLabel(x, y, 85f, 28f, 17f, viewport, occupied)?.let { next ->
                assertTrue(occupied.none(next::overlaps))
                occupied += next
            }
        }
        assertTrue(occupied.isNotEmpty())
    }
}
