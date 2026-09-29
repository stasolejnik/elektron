package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SubstitutionRelevanceTest {

    private val day = LocalDate.of(2026, 10, 5)
    private fun lesson(n: Int, from: String, to: String) =
        Lesson("$n", "o3", "1D", day, DayOfWeek.PONIEDZIALEK, n, LocalTime.parse(from), LocalTime.parse(to), emptyList(), null, null)

    @Test
    fun todayFinishedAfterLastLesson() {
        val lessons = listOf(lesson(1, "08:00", "08:45"), lesson(6, "12:35", "13:20"))
        assertFalse(SubstitutionRelevance.todayFinished(lessons, LocalTime.of(13, 0)))
        assertTrue(SubstitutionRelevance.todayFinished(lessons, LocalTime.of(13, 20)))
    }

    @Test
    fun exemptLastLessonStillCountsForSubstitutionsTab() {
        // Zwolnienie z 6. lekcji: informacja ma być w zakładce do końca 6. lekcji.
        val free = Substitution("s", day, 6, "1D", null, "Uczniowie zwolnieni do domu", null, null, "X")
        val lessons = listOf(lesson(1, "08:00", "08:45"), lesson(6, "12:35", "13:20").copy(substitution = free))
        assertFalse(SubstitutionRelevance.todayFinished(lessons, LocalTime.of(13, 0)))
    }

    @Test
    fun noPlanHidesNothing() {
        assertFalse(SubstitutionRelevance.todayFinished(emptyList(), LocalTime.of(23, 0)))
    }
}
