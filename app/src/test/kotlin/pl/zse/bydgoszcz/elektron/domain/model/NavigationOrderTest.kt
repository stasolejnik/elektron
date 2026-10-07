package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test

class NavigationOrderTest {
    @Test fun normalizationRetainsEachKnownRouteAndIgnoresDamagedValues() {
        val order = NavigationOrder.normalize(listOf("settings", "settings", "unknown", "timetable"))
        assertEquals(listOf("settings", "timetable"), order.take(2))
        assertEquals(NavigationOrder.DEFAULT.toSet(), order.toSet())
        assertEquals(6, order.size)
    }
    @Test fun draggingPreservesHiddenSlotsAndRestoresOrderWhenTheyAreEnabledAgain() {
        val visible = listOf("dashboard", "timetable", "announcements", "settings")
        val moved = NavigationOrder.move(visible, "settings", 0)
        val all = NavigationOrder.reorderVisible(NavigationOrder.DEFAULT, moved)
        assertEquals(moved, all.filter { it in visible })
        assertEquals("substitutions", all[2])
        assertEquals("transit", all[3])
        assertEquals(NavigationOrder.DEFAULT.toSet(), all.toSet())
    }
    @Test fun touchSlotsHaveEqualWidthsAndCentersRegardlessOfCaptions() {
        for (count in 1..6) {
            val width = 360f
            val centers = (0 until count).map { NavigationOrder.slotLeft(it, width, count) + width / count / 2 }
            centers.zipWithNext().forEach { (a, b) -> assertEquals(width / count, b - a, 0.0001f) }
            centers.forEachIndexed { i, x -> assertEquals(i, NavigationOrder.targetIndex(x, width, count)) }
            assertEquals(0, NavigationOrder.targetIndex(-100f, width, count))
            assertEquals(count - 1, NavigationOrder.targetIndex(1000f, width, count))
        }
    }
    @Test fun rtlDragUsesTheSameLogicalOrder() {
        assertEquals(240f, NavigationOrder.slotLeft(0, 360f, 3, rtl = true), 0f)
        assertEquals(0, NavigationOrder.targetIndex(300f, 360f, 3, rtl = true))
        assertEquals(2, NavigationOrder.targetIndex(10f, 360f, 3, rtl = true))
    }
}
