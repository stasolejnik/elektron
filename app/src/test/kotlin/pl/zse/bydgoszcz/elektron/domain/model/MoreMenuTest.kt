package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Więcej" na dolnym pasku: włączenie, przeciąganie między paskiem a panelem, strony, wyłączenie. */
class MoreMenuTest {
    private val all: (String) -> Boolean = { true }

    @Test fun enablingPutsMoreAtTheRightWithTransitAnnouncementsAndSettings() {
        val (order, content) = MoreMenu.enable(NavigationOrder.DEFAULT)
        val slots = MoreMenu.slots(order, true)
        assertEquals(listOf("transit", "announcements", "settings"), content)
        assertEquals(listOf("dashboard", "timetable", "substitutions", MoreMenu.ROUTE), MoreMenu.bar(slots, content, all))
        // Przesuwanie stron: zawartość panelu w miejscu "Więcej".
        assertEquals(listOf("dashboard", "timetable", "substitutions", "transit", "announcements", "settings"),
            MoreMenu.pages(slots, content, all))
    }

    @Test fun moreCanMoveAndItemsMoveBetweenBarAndPanel() {
        val (order, content) = MoreMenu.enable(NavigationOrder.DEFAULT)
        val slots = MoreMenu.slots(order, true)
        // "Więcej" na drugie miejsce, Plan do panelu (na początek), Ogłoszenia z panelu na pasek na koniec.
        val bar = listOf("dashboard", MoreMenu.ROUTE, "substitutions", "announcements")
        val panel = listOf("timetable", "transit", "settings")
        val (newOrder, newContent) = MoreMenu.commit(slots, bar, panel)
        val newSlots = MoreMenu.slots(newOrder, true)
        assertEquals(bar, MoreMenu.bar(newSlots, newContent, all))
        assertEquals(panel, newContent)
        assertEquals(listOf("dashboard", "timetable", "transit", "settings", "substitutions", "announcements"),
            MoreMenu.pages(newSlots, newContent, all))
        // Zapis przetrwa odczyt (znacznik "more" w kolejności).
        assertEquals(newSlots, MoreMenu.slots(newSlots, true))
    }

    @Test fun disablingReturnsPanelTabsInPlaceOfMore() {
        val slots = listOf("dashboard", MoreMenu.ROUTE, "timetable", "substitutions", "transit", "announcements", "settings")
        val content = listOf("settings", "announcements")
        assertEquals(listOf("dashboard", "settings", "announcements", "timetable", "substitutions", "transit"),
            MoreMenu.disable(slots, content))
    }

    @Test fun hiddenAndUnavailableTabsAreSkipped() {
        val (order, content) = MoreMenu.enable(NavigationOrder.DEFAULT)
        val slots = MoreMenu.slots(order, true)
        // Przejazdy wyłączone: znikają z panelu i stron, ale zostają w zapisie.
        val noTransit: (String) -> Boolean = { it != "transit" }
        assertEquals(listOf("dashboard", "timetable", "substitutions", "announcements", "settings"), MoreMenu.pages(slots, content, noTransit))
        assertEquals(listOf("dashboard", "timetable", "substitutions", MoreMenu.ROUTE), MoreMenu.bar(slots, content, noTransit))
        // Wyłączone "Więcej": bez znacznika i bez zawartości.
        assertEquals(NavigationOrder.DEFAULT, MoreMenu.slots(slots, false))
        assertEquals(emptyList<String>(), MoreMenu.content(content, false))
    }
}
