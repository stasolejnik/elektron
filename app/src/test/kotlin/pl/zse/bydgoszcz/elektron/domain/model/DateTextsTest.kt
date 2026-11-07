package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DateTextsTest {
    private val today = LocalDate.of(2026, 11, 3) // wtorek

    @Test fun pastMomentsDropTheDateForToday() {
        assertEquals("12:49", DateTexts.past(LocalDateTime.of(2026, 11, 3, 12, 49), today))
        assertEquals("wczoraj, 18:05", DateTexts.past(LocalDateTime.of(2026, 11, 2, 18, 5), today))
        assertEquals("30.10, 07:05", DateTexts.past(LocalDateTime.of(2026, 10, 30, 7, 5), today))
    }

    @Test fun upcomingMomentsUseDayNamesWithinAWeek() {
        assertEquals("dziś, 13:30", DateTexts.upcoming(LocalDateTime.of(2026, 11, 3, 13, 30), today))
        assertEquals("jutro, 07:50", DateTexts.upcoming(LocalDateTime.of(2026, 11, 4, 7, 50), today))
        assertEquals("piątek, 07:50", DateTexts.upcoming(LocalDateTime.of(2026, 11, 6, 7, 50), today))
        assertEquals("10.11, 07:50", DateTexts.upcoming(LocalDateTime.of(2026, 11, 10, 7, 50), today))
    }

    @Test fun datesHaveLeadingZeros() {
        assertEquals("03.11", LocalDate.of(2026, 11, 3).format(DateTexts.DAY_MONTH))
        assertEquals("06.11.2026", LocalDate.of(2026, 11, 6).format(DateTexts.FULL))
    }
}
