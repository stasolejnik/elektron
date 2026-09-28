package pl.zse.bydgoszcz.elektron.presentation.substitutions

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayLabelTest {
    private val today = LocalDate.of(2026, 9, 28) // poniedziałek

    @Test
    fun labels() {
        assertEquals("Dziś · 28 września", dayLabel(today, today))
        assertEquals("Jutro · 29 września", dayLabel(today.plusDays(1), today))
        assertEquals("Czwartek · 1 października", dayLabel(LocalDate.of(2026, 10, 1), today))
    }
}
