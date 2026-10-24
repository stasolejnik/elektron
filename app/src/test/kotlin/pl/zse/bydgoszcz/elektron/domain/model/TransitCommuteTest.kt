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

    @Test fun fromSchoolWithoutChosenStopShowsOnlyRidesYouCanWalkTo() {
        val near = TransitJourney(listOf(ride("10", base + 4 * 60_000L)), 3, 200.0)
        val far = TransitJourney(listOf(ride("5", base + 60_000L, from = "99")), 17, 1000.0)
        val farLater = TransitJourney(listOf(ride("5", base + 20 * 60_000L, from = "99")), 17, 1000.0)
        assertEquals(listOf(near, farLater), TransitJourneys.walkable(listOf(near, far, farLater), base))
        // Opóźniony o 20 min - już da się dojść.
        assertEquals(listOf(far), TransitJourneys.walkable(listOf(far), base, mapOf(far.key to LiveStatus(20, far.departureMs + 20 * 60_000L))))
        // Do szkoły - bez filtra dojścia (dojście jest na końcu).
        val toSchool = far.copy(schoolArrivalMs = base + 30 * 60_000L)
        assertEquals(listOf(toSchool), TransitJourneys.walkable(listOf(toSchool), base))
    }

    @Test fun targetSurvivesALongWeekend() {
        val friday = LocalDate.of(2026, 10, 23)
        val tuesday = lesson(1, "08:00", "08:45", friday.plusDays(4))
        assertEquals(tuesday, TransitCommute.target(listOf(tuesday), at("15:00", friday))!!.lesson)
    }

    @Test fun toSchoolListStartsWithTheRideTenMinutesEarly() {
        val lesson = base + 60 * 60_000L
        fun trip(depMin: Long, atMin: Long) = TransitJourney(listOf(ride("8", base + depMin * 60_000L)), 3, 300.0, schoolArrivalMs = base + atMin * 60_000L)
        val trips = listOf(trip(0, 20), trip(15, 35), trip(30, 48), trip(35, 53), trip(45, 63))
        // Ostatni z co najmniej 10 min zapasu: przyjazd 48 (lekcja 60).
        assertEquals(2, TransitJourneys.anchorToSchool(trips, lesson))
        // Opóźnienie 4 min - ten już bez zapasu, start od wcześniejszego.
        assertEquals(1, TransitJourneys.anchorToSchool(trips, lesson, mapOf(trips[2].key to LiveStatus(4, trips[2].departureMs + 4 * 60_000L))))
        // Żaden z zapasem - ostatni na czas; żaden na czas albo bez lekcji - pierwszy.
        assertEquals(0, TransitJourneys.anchorToSchool(listOf(trip(45, 63)), lesson))
        assertEquals(0, TransitJourneys.anchorToSchool(trips, null))
        assertEquals(1, TransitJourneys.anchorToSchool(listOf(trip(30, 52), trip(40, 55), trip(45, 63)), lesson))
    }

    @Test fun searchEndsHalfAnHourAfterTheLessonOrThreeHoursWithoutOne() {
        assertEquals(base + 30 * 60_000L, TransitCommute.searchEndMs(base, base - 90 * 60_000L))
        // Ferie - bez lekcji: 3 h od początku szukania (dawniej bez limitu, 12 stron naraz).
        assertEquals(base + 180 * 60_000L, TransitCommute.searchEndMs(null, base))
    }

    @Test fun autoLoadStopsAfterFourTriesNoProgressOrBeyondTheEnd() {
        var state = TransitAutoLoad.State()
        fun ok(raw: Int, next: Long? = base, end: Long? = base + 1, need: Boolean = true, complete: Boolean = true) =
            TransitAutoLoad.shouldLoad(state, complete, raw, next, end, need)
        assertTrue(ok(2))
        state = state.after(2)
        assertFalse("bez postępu", ok(2))
        assertTrue(ok(3))
        assertFalse("nie trzeba", ok(3, need = false))
        assertFalse("wynik cząstkowy", ok(3, complete = false))
        assertFalse("koniec stron", ok(3, next = null))
        assertFalse("za horyzontem", ok(3, end = base - 1))
        assertTrue("ze szkoły bez horyzontu", ok(3, end = null))
        repeat(3) { state = state.after(10 + it) }
        assertFalse("limit 4 dociągnięć", ok(20))
        assertFalse("limit kursów", TransitAutoLoad.shouldLoad(TransitAutoLoad.State(), true, TransitAutoLoad.MAX_RAW, base, null, true))
    }
}
