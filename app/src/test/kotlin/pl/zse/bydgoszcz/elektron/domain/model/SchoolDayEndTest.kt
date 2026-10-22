package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SchoolDayEndTest {
    private val day = LocalDate.of(2026, 10, 23)
    private fun lesson(n: Int, from: String, to: String) = Lesson("l$n", "o3", "1D", day, DayOfWeek.PIATEK, n,
        LocalTime.parse(from), LocalTime.parse(to), emptyList(), null, null)
    private val plan = listOf(lesson(1, "08:00", "08:45"), lesson(2, "08:55", "09:40"), lesson(7, "13:40", "14:25"))

    @Test fun hoursAndMinutesUntilTheLastLesson() {
        val info = SchoolDayEnd.of(plan, day.atTime(12, 15))!!
        assertEquals(LocalTime.of(14, 25), info.end)
        assertEquals("2 h 10 min", info.left)
        assertEquals(0.66f, info.progress, 0.01f)
        assertEquals("45 min", SchoolDayEnd.of(plan, day.atTime(13, 40))!!.left)
        assertEquals("1 h", SchoolDayEnd.of(plan, day.atTime(13, 25))!!.left)
        // Przed lekcjami - cały dzień przed Tobą.
        assertEquals("6 h 25 min", SchoolDayEnd.of(plan, day.atTime(8, 0))!!.left)
        assertEquals(0f, SchoolDayEnd.of(plan, day.atTime(7, 30))!!.progress, 0.001f)
    }

    @Test fun afterLessonsOrOtherDayNothing() {
        assertNull(SchoolDayEnd.of(plan, day.atTime(14, 25)))
        assertNull(SchoolDayEnd.of(plan, day.plusDays(1).atTime(9, 0)))
    }

    @Test fun freedLastLessonsEndTheDayEarlier() {
        val freed = plan.map { if (it.number == 7) it.copy(substitution = Substitution("s", day, 7, "1D", null,
            "Uczniowie zwolnieni do domu", null, null, "X")) else it }
        assertEquals(LocalTime.of(9, 40), SchoolDayEnd.of(freed, day.atTime(9, 0))!!.end)
    }
}
