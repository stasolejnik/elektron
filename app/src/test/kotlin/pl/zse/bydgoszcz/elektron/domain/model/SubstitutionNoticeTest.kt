package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubstitutionNoticeTest {
    private val today = LocalDate.of(2026, 10, 7) // środa

    private fun sub(teacher: String?, info: String, notes: String? = null, date: LocalDate = today.plusDays(1), group: Int? = null) =
        Substitution("id|$info|$teacher", date, 3, "2D", group, info, teacher, notes, "Nowak")

    @Test
    fun absentTeacherIsMarkedAsReplacedNotAsSubstitute() {
        val c = SubstitutionNotice.content(sub("Kowalski", "211"), "Matematyka", today)
        assertEquals("Jutro, 3. lekcja: Kowalski", c.title)
        assertEquals("s.\u00A0211", c.text)
        assertEquals("s.\u00A0211\nZa: Matematyka · Nowak", c.expanded)
        assertFalse("Dawniej 'Zastępuje: <nieobecny>' sugerowało odwrotną rolę", (c.title + c.text + c.expanded).contains("Zastępuje"))
    }

    /** Prawdziwy wpis z 03.11.2026 ("1 D - 111", D. Musierowicz, uwaga "edukacja zdrowotna"). */
    @Test
    fun lessonFromNotesIsTheTitleAndEachDetailAppearsOnce() {
        val c = SubstitutionNotice.content(sub("D. Musierowicz", "111", "edukacja zdrowotna", today), "j.polski", today)
        assertEquals("Dziś, 3. lekcja: edukacja zdrowotna", c.title)
        assertEquals("D. Musierowicz · s.\u00A0111", c.text)
        assertEquals("D. Musierowicz · s.\u00A0111\nZa: j.polski · Nowak", c.expanded)
    }

    @Test
    fun freedLessonShowsTheSchoolInformationInsteadOfNewSubstitution() {
        val c = SubstitutionNotice.content(sub(null, "Uczniowie przychodzą później"), null, today)
        assertEquals("Jutro, 3. lekcja: Uczniowie przychodzą później", c.title)
        assertEquals("Za: Nowak", c.text)
        assertEquals("Za: Nowak", c.expanded)
        assertFalse(c.title.contains("Nowe zastępstwo"))
    }

    @Test
    fun notesGroupAndDayLabels() {
        val c = SubstitutionNotice.content(sub("Kowalski", "Zajęcia w auli", "za ostatnią lekcję", today, group = 2), null, today)
        assertEquals("Dziś, 3. lekcja (grupa 2): Kowalski", c.title)
        assertEquals("Zajęcia w auli", c.text)
        assertEquals("Zajęcia w auli\nZa: Nowak\nUwagi: za ostatnią lekcję", c.expanded)
        assertEquals("Poniedziałek 12.10", SubstitutionNotice.dayLabel(LocalDate.of(2026, 10, 12), today))
    }

    @Test
    fun correctedSubstitutionKeepsTheSameNotificationTag() {
        val first = sub(null, "Zajęcia świetlicowe")
        val corrected = sub("Kowalski", "211")
        assertNotEquals(first.id, corrected.id)
        assertEquals(SubstitutionNotice.tag(first), SubstitutionNotice.tag(corrected))
        assertNotEquals(SubstitutionNotice.tag(first), SubstitutionNotice.tag(first.copy(groupNumber = 1)))
        assertNotEquals(SubstitutionNotice.tag(first), SubstitutionNotice.tag(first.copy(lessonNumber = 4)))
    }

    @Test
    fun notesOnlyChangeIsDetectedOnceAFingerprintExists() {
        val seen = sub("Kowalski", "211", notes = null)
        val changed = seen.copy(notes = "za ostatnią lekcję")
        val ids = setOf(seen.id)
        // Odcisk z poprzedniej synchronizacji: zmiana samych uwag = poprawka do powiadomienia.
        assertEquals(listOf(changed), SubstitutionNotice.changedNotes(listOf(changed), ids, setOf(SubstitutionNotice.fingerprint(seen))))
        // Bez zmian - nic.
        assertTrue(SubstitutionNotice.changedNotes(listOf(seen), ids, setOf(SubstitutionNotice.fingerprint(seen))).isEmpty())
        // Pierwsza synchronizacja po aktualizacji (brak odcisków) - bez lawiny powiadomień.
        assertTrue(SubstitutionNotice.changedNotes(listOf(changed), ids, emptySet()).isEmpty())
        // Nowe zastępstwo nie jest "zmienione" (powiadamia o nim zwykła ścieżka nowych ID).
        assertTrue(SubstitutionNotice.changedNotes(listOf(changed), emptySet(), setOf(SubstitutionNotice.fingerprint(seen))).isEmpty())
        assertEquals(seen.id, changed.id)
        assertNotEquals(SubstitutionNotice.fingerprint(seen), SubstitutionNotice.fingerprint(changed))
    }
}
