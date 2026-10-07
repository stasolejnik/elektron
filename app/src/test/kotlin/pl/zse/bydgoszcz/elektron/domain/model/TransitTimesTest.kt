package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test

class TransitTimesTest {
    @Test fun durationUsesHoursAbove59Minutes() {
        assertEquals("0 min", TransitTimes.duration(0))
        assertEquals("59 min", TransitTimes.duration(59))
        assertEquals("1 godz.", TransitTimes.duration(60))
        assertEquals("1 godz. 1 min", TransitTimes.duration(61))
        assertEquals("5 godz. 28 min", TransitTimes.duration(328))
        assertEquals("24 godz.", TransitTimes.duration(1440))
    }
    @Test fun countdownRoundsUpAndHandlesDepartedVehicle() {
        assertEquals("teraz", TransitTimes.until(1000, 1000))
        assertEquals("teraz", TransitTimes.until(999, 1000))
        assertEquals("za 1 min", TransitTimes.until(1001, 1000))
        assertEquals("za 59 min", TransitTimes.until(59 * 60_000L, 0))
        assertEquals("za 1 godz.", TransitTimes.until(59 * 60_000L + 1, 0))
        assertEquals("za 5 godz. 28 min", TransitTimes.until(328 * 60_000L, 0))
    }
    @Test fun dashboardAndTabEnableJourneysIndependentlyWithoutEnablingTab() {
        val goal = TransitDestination("Cel", listOf("26"), 53.12, 18.02)
        val dashboard = TransitPreferences(destination = goal, enabled = false, showOnDashboard = true)
        assertFalse(dashboard.visible)
        assertTrue(dashboard.canLoadJourneys)
        assertTrue(dashboard.copy(enabled = true, showOnDashboard = false).canLoadJourneys)
        assertFalse(dashboard.copy(showOnDashboard = false).canLoadJourneys)
        assertFalse(dashboard.copy(destination = null).canLoadJourneys)
    }
    @Test fun upcomingPreservesRankedArrivalOrderAfterWalkingUpdates() {
        fun trip(departure: Long, arrival: Long, from: String) = TransitJourney(listOf(
            TransitRide("6", "Cel", from, "Start", "26", "Cel", departure, arrival)), 3, 100.0)
        val ranked = TransitJourneys.rank(listOf(trip(10_000, 30_000, "24"), trip(20_000, 25_000, "25")), 0)
        assertEquals(listOf("25", "24"), TransitJourneys.upcoming(ranked.map { it.copy(walkMinutes = 5) }, 0).map { it.rides.first().fromId })
        assertEquals(listOf("25"), TransitJourneys.upcoming(ranked, 15_000).map { it.rides.first().fromId })
    }

}
