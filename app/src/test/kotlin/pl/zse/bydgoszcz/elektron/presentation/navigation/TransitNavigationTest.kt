package pl.zse.bydgoszcz.elektron.presentation.navigation

import org.junit.Assert.*
import org.junit.Test
import pl.zse.bydgoszcz.elektron.presentation.common.bottomDestinationsForTransit

class TransitNavigationTest {
    @Test fun enabledTabIsBetweenSubstitutionsAndAnnouncementsAndRoutesStayStable() {
        val off = bottomDestinationsForTransit(false).map { it.route }
        val on = bottomDestinationsForTransit(true).map { it.route }
        assertEquals(listOf("dashboard", "timetable", "substitutions", "transit", "announcements", "settings"), on)
        assertEquals(off, on.filterNot { it == "transit" })
        assertEquals(on.size, on.distinct().size)
        assertEquals("settings", on[off.indexOf("settings") + 1])
    }
    @Test fun retainedSixthPageIsSafeAfterTransitIsRemoved() {
        val on = bottomDestinationsForTransit(true)
        val off = bottomDestinationsForTransit(false)
        assertEquals("settings", destinationPageKey(on, 5))
        assertEquals("removed-page-5", destinationPageKey(off, 5))
        assertNull(off.getOrNull(5))
        assertEquals(off.size, off.indices.map { destinationPageKey(off, it) }.distinct().size)
        assertNotEquals(destinationPageKey(off, 5), destinationPageKey(off, 6))
    }

    @Test fun retainedCallbacksUseCurrentPagesWhenTransitIsInserted() {
        var current = bottomDestinationsForTransit(false)
        val key: (Int) -> String = { destinationPageKey(current, it) }
        val count = { current.size }
        assertEquals("settings", key(4))
        current = bottomDestinationsForTransit(true)
        assertEquals(6, count())
        assertEquals("announcements", key(4))
        assertEquals("settings", key(5))
        current = bottomDestinationsForTransit(false)
        assertEquals(5, count())
        assertEquals("removed-page-5", key(5))
    }
}
