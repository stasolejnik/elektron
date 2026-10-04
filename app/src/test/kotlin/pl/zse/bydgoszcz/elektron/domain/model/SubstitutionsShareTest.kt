package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SubstitutionsShareTest {
    private val day = LocalDate.of(2026, 10, 5)
    private fun sub(id: String = "s", n: Int = 2) = Substitution(id, day, n, "1D", 2, "211", "Anna Nowak", "Przynieść zeszyt", "Jan Kowalski", "matematyka")
    @Test fun singleIncludesDateClassGroupTeachersRoomAndNotesWithoutFooter() {
        val t = SubstitutionsShare.text(listOf(sub()))
        listOf("05.10.2026", "1D", "2. lekcja", "grupa 2", "Anna Nowak", "Jan Kowalski", "211", "Przynieść zeszyt", "matematyka").forEach { assertTrue(t, t.contains(it)) }
        assertFalse(t.contains("Udostępniono"))
    }
    @Test fun allSortsAndDeduplicatesWhilePreservingCanceledLessonsAndDifferentDays() {
        val first = sub("a", 1).copy(roomOrInfo = "Uczniowie zwolnieni", substituteTeacher = null)
        val later = sub("b", 3).copy(date = day.plusDays(1))
        val text = SubstitutionsShare.text(listOf(later, sub(), first, first))
        assertTrue(text.indexOf("1. lekcja") < text.indexOf("2. lekcja"))
        assertTrue(text.indexOf("2. lekcja") < text.indexOf("3. lekcja"))
        assertEquals(1, Regex("1\\. lekcja").findAll(text).count())
        assertTrue(text.contains("Uczniowie zwolnieni"))
        assertTrue(text.contains("06.10.2026"))
        assertEquals("", SubstitutionsShare.text(emptyList()))
    }
}
