package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.*
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.*
import java.time.*

class WidgetNotesTest {
    private val date = LocalDate.of(2026, 10, 5)
    private val lesson = Lesson("l", "o3", "1D", date, DayOfWeek.PONIEDZIALEK, 1,
        LocalTime.of(8, 0), LocalTime.of(8, 45),
        listOf(LessonGroup("ang-1/2", "Ch", null, null, "105", null, null, null)), null, null)
    private val note = LessonNote("o3", date, 1, "ang-1/2", "Powtórzyć rozdział 3\nZabrać zeszyt", 1)
    private fun state(notes: List<LessonNote>, lessons: List<Lesson> = listOf(lesson)) =
        WidgetDataLoader.buildState(lessons, date.atTime(7, 0), notes = notes) as WidgetState.Ready

    @Test fun noteBelongsToExactClassDateAndNumber() {
        assertEquals(note.text, state(listOf(note)).focus.userNote)
        for (other in listOf(note.copy(classId = "o4"), note.copy(date = date.plusDays(1)), note.copy(number = 2))) {
            assertNull(state(listOf(other)).focus.userNote)
        }
    }
    @Test fun changingGroupsHidesTheOtherGroupsNote() {
        val otherGroup = lesson.copy(groups = lesson.groups.map { it.copy(subject = "ang-2/2") })
        assertNull(state(listOf(note), listOf(otherGroup)).focus.userNote)
        val renamed = lesson.copy(groups = lesson.groups.map { it.copy(subject = "mat") })
        assertEquals(note.text, state(listOf(note), listOf(renamed)).focus.userNote)
    }
    @Test fun personalNoteDoesNotOverwriteSchoolSubstitutionNotes() {
        val sub = Substitution("s", date, 1, "1D", null, "211", "M. Zelek", "za ostatnią lekcję", "Nauczyciel")
        val result = state(listOf(note), listOf(lesson.copy(substitution = sub))).focus
        assertEquals("za ostatnią lekcję", result.note)
        assertEquals(note.text, result.userNote)
        assertNull(state(emptyList()).focus.userNote)
    }
    private fun sub(userNote: String? = null, schoolNote: String? = null) =
        WidgetSubstitution(LessonTarget(date, 1), "Dziś", 1, "Nauczyciel", "s. 211", schoolNote, userNote)

    @Test fun noteBudgetGrowsWithHeightAndShrinksWithFontScale() {
        assertEquals(0, WidgetNoteLayout.lines(100f, 100f))
        assertEquals(2, WidgetNoteLayout.lines(136f, 100f))
        assertEquals(1, WidgetNoteLayout.lines(136f, 100f, 2f))
        assertTrue(WidgetNoteLayout.lines(250f, 100f) > WidgetNoteLayout.lines(136f, 100f))
    }
    @Test fun substitutionsReserveSpaceForNotesAndMoreLink() {
        val items = List(5) { sub(note.text, "Uwagi szkoły") }
        val result = WidgetNoteLayout.substitutions(250f, items)
        assertEquals(1, result.shown)
        assertEquals(4, result.more)
        assertTrue(result.noteLines.single() > 0)
        val used = SubstitutionRows.CHROME_DP + 52f + 18f + 32f + result.noteLines.sum() * 18f
        assertTrue("Notes must not push the More link outside the widget", used <= 250f)
        val larger = WidgetNoteLayout.substitutions(400f, items)
        assertTrue(larger.shown > result.shown)
        assertEquals(items.size, larger.shown + larger.more)
    }
    @Test fun minimumSizePreservesSubstitutionWithoutOverflowingNote() {
        val result = WidgetNoteLayout.substitutions(110f, listOf(sub(note.text, "Uwagi szkoły")))
        assertEquals(1, result.shown)
        assertEquals(listOf(0), result.noteLines)
        assertEquals(WidgetNoteLayout.Subs(0, 0, emptyList()), WidgetNoteLayout.substitutions(250f, emptyList()))
    }
}
