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
    private val notices = mapOf(friday to SchoolNotice("Oddawanie krwi"), monday to SchoolNotice("Apel"))

    @Test fun duringLessonDaysShowsToday() {
        assertEquals(friday to SchoolNotice("Oddawanie krwi"), SchoolNotices.forDashboard(notices, lessons, friday.atTime(9, 0), monday))
    }

    @Test fun afterLessonsShowsNextLessonDay() {
        assertEquals(monday to SchoolNotice("Apel"), SchoolNotices.forDashboard(notices, lessons, friday.atTime(10, 0), monday))
        assertNull(SchoolNotices.forDashboard(mapOf(friday to SchoolNotice("x")), lessons, friday.atTime(10, 0), monday))
    }

    @Test fun dayWithoutLessonsShowsTodayThenNext() {
        assertEquals(monday to SchoolNotice("Apel"), SchoolNotices.forDashboard(notices, lessons, saturday.atTime(10, 0), monday))
        assertEquals(saturday to SchoolNotice("Dzień otwarty"), SchoolNotices.forDashboard(notices + (saturday to SchoolNotice("Dzień otwarty")), lessons, saturday.atTime(10, 0), monday))
    }

    @Test fun freedLastLessonsEndTheDayEarlier() {
        val freed = lessons.mapIndexed { i, l -> if (i == 1) l.copy(substitution = Substitution("s", friday, 2, "1D", null,
            "Uczniowie zwolnieni do domu", null, null, "X")) else l }
        assertEquals(monday to SchoolNotice("Apel"), SchoolNotices.forDashboard(notices, freed, friday.atTime(9, 0), monday))
    }

    @Test fun nothingWithoutNotices() {
        assertNull(SchoolNotices.forDashboard(emptyMap(), lessons, friday.atTime(9, 0), monday))
    }

    @Test fun notifyOnlyAboutNewOrChangedTextFromToday() {
        val today = java.time.LocalDate.of(2026, 10, 26)
        val before = mapOf(today to SchoolNotice("Apel"), today.plusDays(1) to SchoolNotice("Bez zmian"))
        val after = mapOf(today.minusDays(1) to SchoolNotice("Wczoraj"), today to SchoolNotice("Apel o 12:00"),
            today.plusDays(1) to SchoolNotice("Bez zmian ", updated = "15:30"), today.plusDays(2) to SchoolNotice("", images = listOf("https://zse.edu.bydgoszcz.pl/a.png")),
            today.plusDays(3) to SchoolNotice("Nowa"))
        assertEquals(listOf(today, today.plusDays(3)), SchoolNotices.toNotify(before, after, today).map { it.first })
    }
}
