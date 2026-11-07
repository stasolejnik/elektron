package pl.zse.bydgoszcz.elektron.domain.sync

import org.junit.Assert.*
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome

class BackgroundSyncPolicyTest {
    @Test fun firstRunAndClockRollbackAlwaysFetch() {
        for (source in SyncRequest.ALL_SOURCES) {
            assertTrue(BackgroundSyncPolicy.isDue(source, null, 100))
            assertTrue(BackgroundSyncPolicy.isDue(source, 200, 100))
        }
    }
    @Test fun onlySubstitutionsAreDueAfterFifteenMinutes() {
        val due = SyncRequest.ALL_SOURCES.filter { BackgroundSyncPolicy.isDue(it, 100, 100 + 900) }
        assertEquals(listOf(SyncOutcome.SUBSTITUTIONS), due)
    }
    @Test fun deadlinesAreInclusive() {
        for ((source, interval) in mapOf(SyncOutcome.SIDEBAR to 86400L,
            SyncOutcome.TIMETABLE to 21600L, SyncOutcome.ANNOUNCEMENTS to 3600L)) {
            assertFalse(BackgroundSyncPolicy.isDue(source, 100, 100 + interval - 1))
            assertTrue(BackgroundSyncPolicy.isDue(source, 100, 100 + interval))
        }
    }
    @Test fun atNightSubstitutionsAndAnnouncementsAreCheckedAtMostEveryTwoAndAHalfHours() {
        val interval = BackgroundSyncPolicy.NIGHT_INTERVAL
        val late = java.time.LocalTime.of(23, 30)
        assertTrue(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, 100, 100 + 900, java.time.LocalTime.NOON))
        assertTrue(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, 100, 100 + 900))
        for (source in listOf(SyncOutcome.SUBSTITUTIONS, SyncOutcome.ANNOUNCEMENTS)) {
            assertFalse(BackgroundSyncPolicy.isDue(source, 100, 100 + 900, late))
            assertFalse(BackgroundSyncPolicy.isDue(source, 100, 100 + interval - 1, late))
            assertTrue(BackgroundSyncPolicy.isDue(source, 100, 100 + interval, late))
            assertTrue("pierwszy odczyt także w nocy", BackgroundSyncPolicy.isDue(source, null, 100, late))
        }
        // Następny przebieg (co 15 min) nadal przed progiem "Sprawdzono: …" widżetu.
        assertTrue(interval + 15 * 60 < pl.zse.bydgoszcz.elektron.widget.SubstitutionsFreshness.STALE_HOURS * 3600)
        // Plan i pasek boczny bez zmian.
        assertFalse(BackgroundSyncPolicy.isDue(SyncOutcome.TIMETABLE, 100, 100 + interval, late))
        for (h in listOf(23, 0, 3, 4)) assertTrue(BackgroundSyncPolicy.isNight(java.time.LocalTime.of(h, 30)))
        for (h in listOf(5, 7, 14, 22)) assertFalse(BackgroundSyncPolicy.isNight(java.time.LocalTime.of(h, 30)))
    }

    @Test fun firstRunAfterMidnightChecksSubstitutionsFromBeforeMidnight() {
        val now = 1_000_000L
        val halfPastMidnight = java.time.LocalTime.of(0, 30)
        // Odczyt o 23:45 (45 min temu) - sprzed północy: sprawdzamy raz.
        assertTrue(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, now - 45 * 60, now, halfPastMidnight))
        // Odczyt o 00:10 - już po północy: czekamy na odstęp nocny.
        assertFalse(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, now - 20 * 60, now, halfPastMidnight))
        // Ogłoszenia nie mają tej reguły.
        assertFalse(BackgroundSyncPolicy.isDue(SyncOutcome.ANNOUNCEMENTS, now - 45 * 60, now, halfPastMidnight))
        // Przed północą bez wyjątku.
        assertFalse(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, now - 45 * 60, now, java.time.LocalTime.of(23, 50)))
    }

    @Test fun daysWithoutLessonsCheckSubstitutionsEveryFortyFiveMinutes() {
        val noon = java.time.LocalTime.NOON
        val before = pl.zse.bydgoszcz.elektron.domain.model.SchoolCalendar.enabled
        pl.zse.bydgoszcz.elektron.domain.model.SchoolCalendar.enabled = true
        try {
            val saturday = java.time.LocalDate.of(2026, 11, 7)
            val sunday = saturday.plusDays(1)
            assertTrue(BackgroundSyncPolicy.isRestDay(saturday, noon))
            assertTrue(BackgroundSyncPolicy.isRestDay(sunday, java.time.LocalTime.of(14, 59)))
            // Niedziela po południu przed poniedziałkiem lekcyjnym - zastępstwa na jutro, jak w dzień lekcyjny.
            assertFalse(BackgroundSyncPolicy.isRestDay(sunday, java.time.LocalTime.of(15, 0)))
            assertFalse(BackgroundSyncPolicy.isRestDay(sunday.plusDays(1), noon))
            // Święto w środku tygodnia (11.11) i wieczór przed nim - rzadziej; po nim lekcje.
            assertTrue(BackgroundSyncPolicy.isRestDay(java.time.LocalDate.of(2026, 11, 11), java.time.LocalTime.of(10, 0)))
            assertFalse(BackgroundSyncPolicy.isRestDay(java.time.LocalDate.of(2026, 11, 11), java.time.LocalTime.of(16, 0)))
            assertFalse(BackgroundSyncPolicy.isRestDay(java.time.LocalDate.of(2026, 11, 10), java.time.LocalTime.of(16, 0)))
        } finally { pl.zse.bydgoszcz.elektron.domain.model.SchoolCalendar.enabled = before }
        val interval = BackgroundSyncPolicy.REST_DAY_INTERVAL
        assertFalse(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, 100, 100 + 900, noon, restDay = true))
        assertFalse(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, 100, 100 + interval - 1, noon, restDay = true))
        assertTrue(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, 100, 100 + interval, noon, restDay = true))
        assertTrue("pierwszy odczyt od razu", BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, null, 100, noon, restDay = true))
        // W nocy dalej odstęp nocny (dłuższy).
        assertFalse(BackgroundSyncPolicy.isDue(SyncOutcome.SUBSTITUTIONS, 100, 100 + interval, java.time.LocalTime.of(23, 30), restDay = true))
    }
}
