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

    @Test
    fun dayWithoutVisibleSubstitutions() {
        // Wieczorem: dzisiejsze zastępstwa były, ale już minęły - nie "Brak zastępstw".
        assertEquals("Dzisiejsze zastępstwa już minęły. Są w planie lekcji.", emptyDayText(passed = 2, otherGroups = 0))
        assertEquals("Dzisiejsze zastępstwo już minęło. Jest w planie lekcji.", emptyDayText(passed = 1, otherGroups = 3))
        assertEquals("Brak zastępstw dla Twoich grup", emptyDayText(passed = 0, otherGroups = 1))
        assertEquals("Brak zastępstw dla Twojej klasy", emptyDayText(passed = 0, otherGroups = 0))
    }
}
