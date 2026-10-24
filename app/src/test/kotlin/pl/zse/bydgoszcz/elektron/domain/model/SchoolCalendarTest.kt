package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SchoolCalendarTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun easterDates() {
        assertEquals(d(2026, 4, 5), SchoolCalendar.easter(2026))
        assertEquals(d(2027, 3, 28), SchoolCalendar.easter(2027))
        assertEquals(d(2028, 4, 16), SchoolCalendar.easter(2028))
    }

    @Test fun schoolYear2026Of2027MatchesTheMinistryCalendar() {
        assertNull(SchoolCalendar.dayOff(d(2026, 11, 1)))                      // niedziela - weekend
        assertEquals("Wszystkich Świętych", SchoolCalendar.dayOff(d(2027, 11, 1)))
        assertEquals("Święto Niepodległości", SchoolCalendar.dayOff(d(2026, 11, 11)))
        assertNull(SchoolCalendar.dayOff(d(2026, 11, 10)))
        assertNull(SchoolCalendar.dayOff(d(2026, 12, 22)))           // ostatni dzień zajęć przed przerwą
        assertEquals("Zimowa przerwa świąteczna", SchoolCalendar.dayOff(d(2026, 12, 23)))
        assertEquals("Wigilia", SchoolCalendar.dayOff(d(2026, 12, 24)))
        assertEquals("Boże Narodzenie", SchoolCalendar.dayOff(d(2026, 12, 25)))
        assertEquals("Nowy Rok", SchoolCalendar.dayOff(d(2027, 1, 1)))
        assertNull(SchoolCalendar.dayOff(d(2027, 1, 4)))
        assertEquals("Trzech Króli", SchoolCalendar.dayOff(d(2027, 1, 6)))
        // Ferie w kujawsko-pomorskim 15-28.02.2027 (gov.pl).
        assertNull(SchoolCalendar.dayOff(d(2027, 2, 12)))
        assertEquals("Ferie zimowe", SchoolCalendar.dayOff(d(2027, 2, 15)))
        assertEquals("Ferie zimowe", SchoolCalendar.dayOff(d(2027, 2, 26)))
        assertNull(SchoolCalendar.dayOff(d(2027, 3, 1)))
        // Wiosenna przerwa 25-30.03.2027.
        assertNull(SchoolCalendar.dayOff(d(2027, 3, 24)))
        assertEquals("Wiosenna przerwa świąteczna", SchoolCalendar.dayOff(d(2027, 3, 25)))
        assertEquals("Wiosenna przerwa świąteczna", SchoolCalendar.dayOff(d(2027, 3, 30)))
        assertNull(SchoolCalendar.dayOff(d(2027, 3, 31)))
        assertEquals("Święto Konstytucji 3 Maja", SchoolCalendar.dayOff(d(2027, 5, 3)))
        assertEquals("Boże Ciało", SchoolCalendar.dayOff(d(2027, 5, 27)))
        // Koniec zajęć 25.06.2027, wakacje do 31.08, rok szkolny 2027/28 od środy 1.09.
        assertNull(SchoolCalendar.dayOff(d(2027, 6, 25)))
        assertEquals("Wakacje", SchoolCalendar.dayOff(d(2027, 6, 28)))
        assertEquals("Wakacje", SchoolCalendar.dayOff(d(2027, 8, 31)))
        assertNull(SchoolCalendar.dayOff(d(2027, 9, 1)))
        // Weekendy nie są "dniem wolnym" (planu na nie i tak nie ma).
        assertNull(SchoolCalendar.dayOff(d(2027, 2, 13)))
    }

    @Test fun schoolYearStartsOnMondayWhenFirstSeptemberIsFridayOrSaturday() {
        assertEquals(d(2028, 9, 4), SchoolCalendar.schoolYearStart(2028))   // 1.09.2028 - piątek
        assertEquals(d(2026, 9, 1), SchoolCalendar.schoolYearStart(2026))
        assertEquals("Wakacje", SchoolCalendar.dayOff(d(2028, 9, 1)))
    }

    @Test fun lessonsOnDaysOffDoNotTakePlace() {
        val before = SchoolCalendar.enabled
        SchoolCalendar.enabled = true
        try {
            fun lesson(date: LocalDate) = Lesson("x", "o3", "1D", date, DayOfWeek.SRODA, 1, LocalTime.of(8, 0), LocalTime.of(8, 45),
                emptyList(), null, null)
            assertFalse(SubstitutionDisplay.takesPlace(lesson(d(2026, 11, 11))))
            assertTrue(SubstitutionDisplay.takesPlace(lesson(d(2026, 11, 12))))
            // Na 11.11 nie ma powiadomienia na żywo ani "do końca lekcji".
            val day = d(2026, 11, 11)
            assertNull(LiveLesson.content(listOf(lesson(day)), day.atTime(8, 10)))
            assertNull(SchoolDayEnd.of(listOf(lesson(day)), day.atTime(8, 10)))
        } finally { SchoolCalendar.enabled = before }
    }
}
