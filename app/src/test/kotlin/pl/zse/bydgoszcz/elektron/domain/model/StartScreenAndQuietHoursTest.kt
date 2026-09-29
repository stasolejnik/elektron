package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StartScreenAndQuietHoursTest {

    private val day = LocalDate.of(2026, 10, 5)
    private fun lesson(n: Int, from: String, to: String) =
        Lesson("$n", "o3", "1D", day, DayOfWeek.PONIEDZIALEK, n, LocalTime.parse(from), LocalTime.parse(to), emptyList(), null, null)
    private val today = listOf(lesson(1, "08:00", "08:45"), lesson(2, "08:55", "09:40"))

    @Test fun smartOpensTimetableDuringLessons() {
        assertEquals(StartScreen.TIMETABLE, StartScreen.SMART.resolve(today, LocalTime.of(7, 52)))
        assertEquals(StartScreen.TIMETABLE, StartScreen.SMART.resolve(today, LocalTime.of(9, 0)))
        assertEquals(StartScreen.DASHBOARD, StartScreen.SMART.resolve(today, LocalTime.of(7, 40)))
        assertEquals(StartScreen.DASHBOARD, StartScreen.SMART.resolve(today, LocalTime.of(10, 0)))
        assertEquals(StartScreen.DASHBOARD, StartScreen.SMART.resolve(emptyList(), LocalTime.of(9, 0)))
    }

    @Test fun fixedScreenIgnoresLessons() {
        assertEquals(StartScreen.SUBSTITUTIONS, StartScreen.SUBSTITUTIONS.resolve(today, LocalTime.of(9, 0)))
        assertEquals(StartScreen.DASHBOARD, StartScreen.DASHBOARD.resolve(today, LocalTime.of(9, 0)))
        assertEquals(StartScreen.TIMETABLE, StartScreen.fromKey("timetable"))
    }

    @Test fun quietHoursOverMidnight() {
        val q = QuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0))
        assertTrue(q.isQuiet(LocalTime.of(23, 30)))
        assertTrue(q.isQuiet(LocalTime.of(3, 0)))
        assertTrue(q.isQuiet(LocalTime.of(22, 0)))
        assertFalse(q.isQuiet(LocalTime.of(7, 0)))
        assertFalse(q.isQuiet(LocalTime.of(12, 0)))
    }

    @Test fun quietHoursSameDayAndDisabled() {
        val q = QuietHours(true, LocalTime.of(13, 0), LocalTime.of(15, 0))
        assertTrue(q.isQuiet(LocalTime.of(14, 0)))
        assertFalse(q.isQuiet(LocalTime.of(15, 0)))
        assertFalse(q.copy(enabled = false).isQuiet(LocalTime.of(14, 0)))
        assertFalse(QuietHours(true, LocalTime.NOON, LocalTime.NOON).isQuiet(LocalTime.NOON))
    }
}
