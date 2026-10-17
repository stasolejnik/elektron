package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SchoolNoticesTest {
    private val friday = LocalDate.of(2026, 10, 16)
    private val saturday = friday.plusDays(1)
    private val monday = LocalDate.of(2026, 10, 19)
    private fun lesson(date: LocalDate, from: String, to: String) =
        Lesson("$date-$from", "o3", "1D", date, DayOfWeek.PIATEK, 1, LocalTime.parse(from), LocalTime.parse(to), emptyList(), null, null)
    private val lessons = listOf(lesson(friday, "08:00", "08:45"), lesson(friday, "08:55", "09:40"))
    private val notices = mapOf(friday to "Oddawanie krwi", monday to "Apel")

    @Test fun duringLessonDaysShowsToday() {
        assertEquals(friday to "Oddawanie krwi", SchoolNotices.forDashboard(notices, lessons, friday.atTime(9, 0), monday))
    }

    @Test fun afterLessonsShowsNextLessonDay() {
        assertEquals(monday to "Apel", SchoolNotices.forDashboard(notices, lessons, friday.atTime(10, 0), monday))
        assertNull(SchoolNotices.forDashboard(mapOf(friday to "x"), lessons, friday.atTime(10, 0), monday))
    }

    @Test fun dayWithoutLessonsShowsTodayThenNext() {
        assertEquals(monday to "Apel", SchoolNotices.forDashboard(notices, lessons, saturday.atTime(10, 0), monday))
        assertEquals(saturday to "Dzień otwarty", SchoolNotices.forDashboard(notices + (saturday to "Dzień otwarty"), lessons, saturday.atTime(10, 0), monday))
    }

    @Test fun freedLastLessonsEndTheDayEarlier() {
        val freed = lessons.mapIndexed { i, l -> if (i == 1) l.copy(substitution = Substitution("s", friday, 2, "1D", null,
            "Uczniowie zwolnieni do domu", null, null, "X")) else l }
        assertEquals(monday to "Apel", SchoolNotices.forDashboard(notices, freed, friday.atTime(9, 0), monday))
    }

    @Test fun nothingWithoutNotices() {
        assertNull(SchoolNotices.forDashboard(emptyMap(), lessons, friday.atTime(9, 0), monday))
    }
}
