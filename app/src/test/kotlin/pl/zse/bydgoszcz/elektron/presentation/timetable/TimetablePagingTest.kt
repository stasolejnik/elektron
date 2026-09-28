package pl.zse.bydgoszcz.elektron.presentation.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableViewModel.Companion.START_PAGE
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableViewModel.Companion.dayForPage
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableViewModel.Companion.mondayForPage
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableViewModel.Companion.nextSchoolDay
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableViewModel.Companion.pageForDay
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableViewModel.Companion.pageForWeek
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableViewModel.Companion.preferredDay
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.LocalDate

/** Mapowanie stron planu na daty: weekendy pomijane, strona <-> data w obie strony. */
class TimetablePagingTest {

    private val monday = LocalDate.of(2026, 9, 28)

    @Test
    fun weekendMapsToNextMonday() {
        assertEquals(monday.plusWeeks(1), nextSchoolDay(LocalDate.of(2026, 10, 3)))   // sobota
        assertEquals(monday.plusWeeks(1), nextSchoolDay(LocalDate.of(2026, 10, 4)))   // niedziela
        assertEquals(monday, nextSchoolDay(monday))
    }

    @Test
    fun consecutivePagesAreConsecutiveSchoolDays() {
        for (baseOffset in 0..4) {
            val base = monday.plusDays(baseOffset.toLong())
            var prev = dayForPage(base, START_PAGE - 300)
            for (page in START_PAGE - 299..START_PAGE + 300) {
                val d = dayForPage(base, page)
                assertTrue("Weekend na stronie $page: $d", d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY)
                val gap = d.toEpochDay() - prev.toEpochDay()
                assertTrue("Przerwa $gap dni między $prev a $d", gap == 1L || gap == 3L)
                assertEquals("Strona -> data -> strona", page, pageForDay(base, d))
                prev = d
            }
        }
    }

    @Test
    fun startPageIsBaseDay() {
        assertEquals(monday.plusDays(2), dayForPage(monday.plusDays(2), START_PAGE))
        assertEquals(START_PAGE, pageForDay(monday, monday))
    }

    @Test
    fun weekPagesAreMondays() {
        assertEquals(monday, mondayForPage(monday.plusDays(3), START_PAGE))
        assertEquals(monday.plusWeeks(2), mondayForPage(monday, START_PAGE + 2))
        assertEquals(START_PAGE - 1, pageForWeek(monday, monday.minusDays(3)))   // piątek poprzedniego tygodnia
    }

    @Test
    fun afterLastLessonShowsNextSchoolDay() {
        val friday = monday.plusDays(4)
        val end = LocalTime.of(14, 15)
        assertEquals(monday, preferredDay(monday, LocalTime.of(10, 0), end))                 // w trakcie dnia
        assertEquals(monday.plusDays(1), preferredDay(monday, LocalTime.of(14, 15), end))   // po lekcjach
        assertEquals(monday.plusWeeks(1), preferredDay(friday, LocalTime.of(16, 0), end))   // piątek po lekcjach
        assertEquals(monday.plusWeeks(1), preferredDay(friday.plusDays(1), LocalTime.of(10, 0), end)) // sobota
        assertEquals(monday, preferredDay(monday, LocalTime.of(20, 0), null))                // dziś bez lekcji
    }
}
