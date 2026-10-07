package pl.zse.bydgoszcz.elektron.presentation.timetable

import org.junit.Assert.*
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.*
import java.time.*

class WeekOpeningTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun lesson(day: LocalDate, start: String = "08:55", end: String = "09:40") =
        Lesson("l$day", "o3", "1D", day, pl.zse.bydgoszcz.elektron.domain.model.DayOfWeek.fromIso(day.dayOfWeek.value)!!, 1, LocalTime.parse(start), LocalTime.parse(end), emptyList(), null, null)
    @Test fun afterLessonsSelectsNextDayWithinSameWeekAndItsFirstLesson() {
        val now = today.atTime(15, 20)
        val preferred = TimetableViewModel.preferredDay(today, now.toLocalTime(), LocalTime.of(14, 30))
        val days = (0..4).map { today.with(java.time.DayOfWeek.MONDAY).plusDays(it.toLong()) }
        assertEquals(today.plusDays(1), WeekOpening.selectedDay(days, preferred, today))
        assertEquals(8 * 60 + 55, WeekOpening.startMinute(preferred, listOf(lesson(preferred)), now))
    }
    @Test fun currentDayDuringClassesUsesCurrentTime() {
        assertEquals(9 * 60 + 5, WeekOpening.startMinute(today, listOf(lesson(today)), today.atTime(9, 5)))
    }
    @Test fun fridayAfterClassesOpensMondayInNextWeek() {
        val friday = today.with(java.time.DayOfWeek.FRIDAY)
        val preferred = TimetableViewModel.preferredDay(friday, LocalTime.of(16, 0), LocalTime.of(15, 0))
        assertEquals(friday.plusDays(3), preferred)
        val days = (0..4).map { preferred.plusDays(it.toLong()) }
        assertEquals(preferred, WeekOpening.selectedDay(days, preferred, friday))
        assertEquals(8 * 60 + 55, WeekOpening.startMinute(preferred, listOf(lesson(preferred)), friday.atTime(16, 0)))
    }
    @Test fun otherWeeksAndEmptyDaysHaveSafeOpening() {
        val monday = today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1)
        assertEquals(monday, WeekOpening.selectedDay(listOf(monday), today, today))
        assertNull(WeekOpening.selectedDay(emptyList(), today, today))
        assertNull(WeekOpening.startMinute(monday, emptyList(), today.atTime(14, 0)))
    }
}
