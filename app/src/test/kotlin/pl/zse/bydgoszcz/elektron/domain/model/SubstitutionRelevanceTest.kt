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

    @Test
    fun substitutionDisappearsAfterItsLesson() {
        val lessons = listOf(lesson(1, "08:00", "08:45"), lesson(7, "13:40", "14:25"))
        val ends = SubstitutionRelevance.lessonEnds(lessons)
        val sub = Substitution("s", day, 7, "1D", 2, "Zajęcia Świetlicowe", null, null, "X")
        assertFalse(SubstitutionRelevance.isOver(sub, java.time.LocalDateTime.of(day, LocalTime.of(14, 0)), ends))
        assertTrue(SubstitutionRelevance.isOver(sub, java.time.LocalDateTime.of(day, LocalTime.of(14, 25)), ends))
        assertFalse(SubstitutionRelevance.isOver(sub, java.time.LocalDateTime.of(day.minusDays(1), LocalTime.of(23, 0)), ends))
        assertTrue(SubstitutionRelevance.isOver(sub, java.time.LocalDateTime.of(day.plusDays(1), LocalTime.of(7, 0)), ends))
        // Nieznany numer lekcji - do końca dnia.
        assertFalse(SubstitutionRelevance.isOver(sub.copy(lessonNumber = 9), java.time.LocalDateTime.of(day, LocalTime.of(20, 0)), ends))
    }
}
