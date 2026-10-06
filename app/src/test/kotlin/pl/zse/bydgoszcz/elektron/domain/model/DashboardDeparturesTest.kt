package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DashboardDeparturesTest {
    private val day = LocalDate.of(2026, 10, 6)
    private fun lesson(n: Int, start: String, end: String, date: LocalDate = day) = Lesson("$date-$n", "o3", "1D", date,
        DayOfWeek.WTOREK, n, LocalTime.parse(start), LocalTime.parse(end), emptyList(), null, null)
    private fun at(time: String, date: LocalDate = day) = LocalDateTime.of(date, LocalTime.parse(time))
    @Test fun departureAppearsDuringLastLessonAndAfterSchoolButNotDuringEarlierLessons() {
        val lessons = listOf(lesson(1, "08:00", "08:45"), lesson(2, "08:55", "09:40"), lesson(1, "08:00", "08:45", day.plusDays(1)))
        assertFalse(DashboardDepartures.isTime(lessons, at("08:20")))
        assertFalse(DashboardDepartures.isTime(lessons, at("08:54:59")))
        assertTrue(DashboardDepartures.isTime(lessons, at("08:55")))
        assertTrue(DashboardDepartures.isTime(lessons, at("09:40")))
        assertTrue(DashboardDepartures.isTime(lessons, at("15:00")))
    }
    @Test fun cancelledLastLessonDoesNotPostponeDeparture() {
        val first = lesson(1, "08:00", "08:45")
        val last = lesson(2, "08:55", "09:40").copy(substitution = Substitution("s", day, 2, "1D", null,
            "Uczniowie zwolnieni do domu", null, null, "AB"))
        assertTrue(DashboardDepartures.isTime(listOf(first, last), at("08:15")))
        assertFalse(DashboardDepartures.isTime(listOf(last), at("12:00")))
    }
    @Test fun noCommuteOnDaysWithoutLessons() {
        assertFalse(DashboardDepartures.isTime(emptyList(), at("16:00")))
        assertFalse(DashboardDepartures.isTime(listOf(lesson(1, "08:00", "08:45")), at("16:00", day.plusDays(1))))
    }
}
