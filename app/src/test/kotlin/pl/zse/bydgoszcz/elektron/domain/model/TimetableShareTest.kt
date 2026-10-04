package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimetableShareTest {
    private val day = LocalDate.of(2026, 10, 5)
    private fun lesson(n: Int) = Lesson("l$n", "o3", "1D", day, DayOfWeek.PONIEDZIALEK,
        n, LocalTime.of(8, 0), LocalTime.of(8, 45),
        listOf(LessonGroup("ang-1/2", "Ko", null, "Jan Kowalski", "211", null, null, null)), null, null)

    @Test fun usesChosenDayCustomNamesGroupsAndChanges() {
        val changed = lesson(2).copy(substitution = Substitution("s", day, 2, "1D", 1,
            "Uczniowie zwolnieni", null, "Do domu", "Jan Kowalski"))
        val text = TimetableShare.text(day, listOf(changed, lesson(1), lesson(3).copy(date = day.plusDays(1))),
            mapOf("ang" to SubjectStyle(name = "Angielski")))
        assertTrue(text.contains("1D"))
        assertTrue(text.contains("5.10.2026"))
        assertTrue(text.contains("Angielski"))
        assertTrue(text.contains(LessonGroups.displayLabel("1/2")))
        assertTrue(text.contains("s. 211"))
        assertTrue(text.contains("Uczniowie zwolnieni"))
        assertTrue(text.contains("Do domu"))
        assertTrue(text.indexOf("1. 08:00") < text.indexOf("2. 08:00"))
        assertFalse(text.contains("3. 08:00"))
    }

    @Test fun emptyDayDoesNotClaimSchoolHasNoLessons() {
        assertTrue(TimetableShare.text(day, emptyList()).contains("Brak zapisanych lekcji"))
    }
    @Test fun dayHasNoAppFooter() {
        val text = TimetableShare.text(day, listOf(lesson(1)))
        assertFalse(text.contains("Udostępniono z eLektrona"))
        assertFalse(text.contains("Dane według zapisanego planu"))
    }

    @Test fun weekIncludesFiveSchoolDaysInOrderWithChangesAndNoFooter() {
        val tuesday = lesson(2).copy(date = day.plusDays(1))
        val friday = lesson(4).copy(date = day.plusDays(4), substitution = Substitution("s", day.plusDays(4),
            4, "1D", null, "Uczniowie zwolnieni", null, null, "Jan Kowalski"))
        val outside = lesson(9).copy(date = day.plusWeeks(1))
        val text = TimetableShare.weekText(day.plusDays(2), listOf(friday, outside, tuesday, lesson(1)))
        assertTrue(text.contains("5.10.2026–9.10.2026"))
        assertTrue(text.contains("wtorek, 6.10.2026"))
        assertTrue(text.contains("środa, 7.10.2026"))
        assertTrue(text.contains("piątek, 9.10.2026"))
        assertTrue(text.contains("Uczniowie zwolnieni"))
        assertTrue(text.indexOf("1. 08:00") < text.indexOf("2. 08:00"))
        assertFalse(text.contains("9. 08:00"))
        assertFalse(text.contains("Udostępniono"))
        assertFalse(text.contains("Dane według"))
    }

}
