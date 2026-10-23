package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TransitCommuteTest {
    private val day = LocalDate.of(2026, 10, 6)
    private fun lesson(n: Int, start: String, end: String, date: LocalDate = day) = Lesson("$date-$n", "o3", "1D", date,
        DayOfWeek.WTOREK, n, LocalTime.parse(start), LocalTime.parse(end), emptyList(), null, null)
    private fun at(time: String, date: LocalDate = day) = LocalDateTime.of(date, LocalTime.parse(time))
    private fun freed(l: Lesson) = l.copy(substitution = Substitution("s", l.date, l.number, "1D", null,
        "Uczniowie zwolnieni do domu", null, null, "AB"))

    @Test fun switchesToGoingHomeHalfwayThroughTheSchoolDay() {
        val lessons = listOf(lesson(1, "08:00", "08:45"), lesson(7, "13:45", "14:30"))
        assertEquals(LocalTime.of(11, 15), TransitCommute.switchTime(lessons, day))
        assertEquals(TransitDirection.TO_SCHOOL, TransitCommute.autoDirection(lessons, at("06:30")))
        assertEquals(TransitDirection.TO_SCHOOL, TransitCommute.autoDirection(lessons, at("11:14:59")))
        assertEquals(TransitDirection.FROM_SCHOOL, TransitCommute.autoDirection(lessons, at("11:15")))
        assertEquals(TransitDirection.FROM_SCHOOL, TransitCommute.autoDirection(lessons, at("22:00")))
    }

    @Test fun dayWithoutLessonsSwitchesAtNoonAndFreedLessonsDoNotCount() {
        assertEquals(LocalTime.NOON, TransitCommute.switchTime(emptyList(), day))
        val lessons = listOf(freed(lesson(1, "08:00", "08:45")), lesson(5, "11:50", "12:35"), lesson(6, "12:45", "13:30"))
        // Pierwsza lekcja zwolniona - dzień zaczyna się o 11:50.
        assertEquals(LocalTime.of(12, 40), TransitCommute.switchTime(lessons, day))
        assertEquals(TransitDirection.TO_SCHOOL, TransitCommute.autoDirection(lessons, at("12:00")))
    }

    @Test fun targetIsTheNextHeldLessonAndSearchStartsNotTooEarly() {
        val lessons = listOf(freed(lesson(1, "08:00", "08:45")), lesson(2, "08:55", "09:40"), lesson(1, "08:00", "08:45", day.plusDays(1)))
        val target = TransitCommute.target(lessons, at("06:00"))!!
        assertEquals(2, target.lesson.number)
        assertEquals(at("07:25"), TransitCommute.searchFrom(target.start, at("06:00")))
        assertEquals(at("08:00"), TransitCommute.searchFrom(target.start, at("08:00")))
        // Po początku ostatniej lekcji - lekcja następnego dnia; wieczorem szukanie od 6:30 rano.
        val tomorrow = TransitCommute.target(lessons, at("20:00"))!!
        assertEquals(day.plusDays(1), tomorrow.lesson.date)
        assertEquals(at("06:30", day.plusDays(1)), TransitCommute.searchFrom(tomorrow.start, at("20:00")))
        assertNull(TransitCommute.target(lessons, at("09:00", day.plusDays(5))))
    }

    @Test fun slackIsNegativeWhenLate() {
        assertEquals(5, TransitCommute.slackMinutes(1_000_000L, 1_000_000L + 5 * 60_000L))
        assertEquals(-1, TransitCommute.slackMinutes(1_000_000L + 30_000L, 1_000_000L))
    }

    @Test fun dashboardCardShowsCommuteBeforeLessonsAndReturnAfterTheLastOne() {
        val lessons = listOf(lesson(1, "08:00", "08:45"), lesson(2, "08:55", "09:40"), lesson(1, "08:00", "08:45", day.plusDays(1)))
        assertNull(TransitCommute.dashboardDirection(lessons, at("05:59:59")))
        assertEquals(TransitDirection.TO_SCHOOL, TransitCommute.dashboardDirection(lessons, at("06:00")))
        assertEquals(TransitDirection.TO_SCHOOL, TransitCommute.dashboardDirection(lessons, at("07:59:59")))
        assertNull(TransitCommute.dashboardDirection(lessons, at("08:20")))
        assertNull(TransitCommute.dashboardDirection(lessons, at("08:54:59")))
        assertEquals(TransitDirection.FROM_SCHOOL, TransitCommute.dashboardDirection(lessons, at("08:55")))
        assertEquals(TransitDirection.FROM_SCHOOL, TransitCommute.dashboardDirection(lessons, at("10:39:59")))
        assertNull(TransitCommute.dashboardDirection(lessons, at("10:40")))
        assertNull(TransitCommute.dashboardDirection(emptyList(), at("16:00")))
        assertNull(TransitCommute.dashboardDirection(lessons, at("16:00", day.plusDays(2))))
    }

    @Test fun cancelledLastLessonDoesNotPostponeTheReturn() {
        val first = lesson(1, "08:00", "08:45")
        val last = freed(lesson(2, "08:55", "09:40"))
        assertEquals(TransitDirection.FROM_SCHOOL, TransitCommute.dashboardDirection(listOf(first, last), at("08:15")))
        assertNull(TransitCommute.dashboardDirection(listOf(last), at("12:00")))
    }

    private val base = 1_800_000_000_000L
    private fun ride(line: String, departure: Long, from: String = "41") = TransitRide(line, "Kierunek", from, "Start", "11", "Cel", departure, departure + 600_000L)

    @Test fun liveDelayMatchesTheSameLineAndScheduledDeparture() {
        val r = ride("8", base)
        val board = listOf(
            LiveDeparture("8", base + 3 * 60_000L + 20_000L, 3, live = true),   // planowo base+20 s - ten kurs
            LiveDeparture("8", base + 20 * 60_000L, 0, live = true),             // następny kurs
            LiveDeparture("3", base + 3 * 60_000L, 3, live = true))
        assertEquals(LiveStatus(3, base + 3 * 60_000L), TransitLive.match(r, board))
        // Tylko rozkład albo inna godzina - bez stanu.
        assertNull(TransitLive.match(r, listOf(LiveDeparture("8", base, null, live = false))))
        assertNull(TransitLive.match(r, listOf(LiveDeparture("8", base + 5 * 60_000L, 1, live = true))))
        // Odjazd przed czasem.
        assertEquals(-2, TransitLive.match(r, listOf(LiveDeparture("8", base - 2 * 60_000L, -2, live = true)))!!.delayMinutes)
    }

    @Test fun boardsOnlyForSoonDeparturesAndDelayedTripStaysUntilItLeaves() {
        val soon = TransitJourney(listOf(ride("8", base + 10 * 60_000L)), 3, 200.0)
        val sameBoard = TransitJourney(listOf(ride("8", base + 25 * 60_000L)), 3, 200.0)
        val later = TransitJourney(listOf(ride("5", base + 45 * 60_000L)), 3, 200.0)
        assertEquals(listOf("41" to "8"), TransitLive.boards(listOf(soon, sameBoard, later), base))
        val live = mapOf(soon.key to LiveStatus(4, soon.departureMs + 4 * 60_000L))
        val now = soon.departureMs + 60_000L
        assertEquals(listOf(soon, sameBoard, later), TransitJourneys.upcoming(listOf(soon, sameBoard, later), now, live = live))
        assertEquals(listOf(sameBoard, later), TransitJourneys.upcoming(listOf(soon, sameBoard, later), now))
    }

    @Test fun lastTripOnTimeAccountsForDelay() {
        val lesson = base + 60 * 60_000L
        fun trip(dep: Long, atSchool: Long) = TransitJourney(listOf(ride("8", dep)), 4, 300.0, schoolArrivalMs = atSchool)
        val a = trip(base, base + 20 * 60_000L)
        val b = trip(base + 20 * 60_000L, base + 55 * 60_000L)
        val c = trip(base + 40 * 60_000L, base + 70 * 60_000L)
        assertSame(b, TransitJourneys.lastOnTime(listOf(a, b, c), lesson))
        assertSame(a, TransitJourneys.lastOnTime(listOf(a, b, c), lesson, mapOf(b.key to LiveStatus(6, b.departureMs + 6 * 60_000L))))
        assertNull(TransitJourneys.lastOnTime(listOf(a, b, c), null))
    }

    @Test fun leaveSchoolTimeIncludesWalkAndBuffer() {
        val trip = TransitJourney(listOf(ride("8", base)), 5, 300.0)
        assertEquals(base - 7 * 60_000L, trip.leaveSchoolMs())
        assertEquals(base - 4 * 60_000L, trip.leaveSchoolMs(base + 3 * 60_000L))
    }
}
