package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LessonGroupsTest {

    private val day = LocalDate.of(2026, 9, 28)

    private fun group(subject: String) = LessonGroup(subject, null, null, null, null, null, null, null)

    private fun lesson(number: Int, vararg subjects: String, sub: Substitution? = null) = Lesson(
        id = "l$number", classId = "o1", className = "2D", date = day,
        dayOfWeek = DayOfWeek.PONIEDZIALEK, number = number,
        timeFrom = LocalTime.of(8, 0), timeTo = LocalTime.of(8, 45),
        groups = subjects.map(::group), note = null, substitution = sub
    )

    private fun substitution(number: Int, group: Int?) = Substitution(
        id = "s$number", date = day, lessonNumber = number, classShortName = "2D",
        groupNumber = group, roomOrInfo = "316", substituteTeacher = "X", notes = null,
        originalTeacher = "Y"
    )

    @Test
    fun parsesGroupSuffixes() {
        assertEquals(LessonGroups.ParsedSubject("ang", "1/2"), LessonGroups.parse("ang-1/2"))
        assertEquals(LessonGroups.ParsedSubject("zaj.prakt", "3/3"), LessonGroups.parse("zaj.prakt-3/3"))
        assertEquals(LessonGroups.ParsedSubject("rel", "1/1"), LessonGroups.parse("rel-1/1"))
        assertEquals(LessonGroups.ParsedSubject("wf", "j2"), LessonGroups.parse("wf-j2"))
        // Myślnik w nazwie przedmiotu to nie grupa.
        assertNull(LessonGroups.parse("wos-rozsz")?.label)
        assertNull(LessonGroups.parse("e-learning")?.label)
        assertNull(LessonGroups.parse("mat")?.label)
    }

    @Test
    fun labelNumbers() {
        assertEquals(2, LessonGroups.labelNumber("2/3"))
        assertEquals(1, LessonGroups.labelNumber("j1"))
        assertEquals(2, LessonGroups.labelNumber("gr2"))
    }

    @Test
    fun detectsDividedSubjects() {
        val lessons = listOf(
            lesson(1, "mat"),
            lesson(2, "zaj.prakt-1/3", "zaj.prakt-2/3", "zaj.prakt-3/3"),
            lesson(3, "rel-1/1")
        )
        val detected = LessonGroups.detect(lessons)
        assertEquals(listOf("rel", "zaj.prakt"), detected.map { it.base })
        assertEquals(listOf("1/3", "2/3", "3/3"), detected.first { it.base == "zaj.prakt" }.labels)
        assertTrue(detected.first { it.base == "rel" }.isToggle)
    }

    @Test
    fun filtersToChosenGroupsAndKeepsUndivided() {
        val lessons = listOf(
            lesson(1, "mat"),
            lesson(2, "zaj.prakt-1/3", "zaj.prakt-2/3"),
            lesson(3, "rel-1/1"),
            lesson(4, "ang-1/2", "niem-2/2")
        )
        val sel = mapOf("zaj.prakt" to "2/3", "rel" to LessonGroups.NONE, "niem" to LessonGroups.NONE)
        val out = LessonGroups.filter(lessons, sel)
        assertEquals(listOf(1, 2, 4), out.map { it.number })          // religia ukryta
        assertEquals(listOf("zaj.prakt-2/3"), out[1].groups.map { it.subject })
        assertEquals(listOf("ang-1/2"), out[2].groups.map { it.subject }) // niem ukryty, ang nieustawiony
    }

    @Test
    fun noSelectionsMeansEverythingVisible() {
        val lessons = listOf(lesson(2, "zaj.prakt-1/3", "zaj.prakt-2/3"))
        assertEquals(lessons, LessonGroups.filter(lessons, emptyMap()))
    }

    @Test
    fun substitutionForOtherGroupIsNotRelevant() {
        val raw = listOf(lesson(2, "zaj.prakt-1/3", "zaj.prakt-2/3"))
        val sel = mapOf("zaj.prakt" to "2/3")
        assertFalse(LessonGroups.substitutionRelevant(substitution(2, 1), raw, sel))
        assertTrue(LessonGroups.substitutionRelevant(substitution(2, 2), raw, sel))
        assertTrue(LessonGroups.substitutionRelevant(substitution(2, null), raw, sel)) // cała klasa
    }

    @Test
    fun wholeClassSubstitutionForHiddenLessonIsNotRelevant() {
        // "Nie chodzę" na religię: plan chowa lekcję, więc zastępstwo zapisane dla całej klasy
        // (bez numeru grupy) też nie dotyczy ucznia.
        val raw = listOf(lesson(3, "rel-1/1"), lesson(4, "ang-1/2", "niem-2/2"), lesson(5))
        val sel = mapOf("rel" to LessonGroups.NONE, "niem" to LessonGroups.NONE)
        assertTrue(LessonGroups.filter(raw, sel).none { it.number == 3 })
        assertFalse(LessonGroups.substitutionRelevant(substitution(3, null), raw, sel))
        // Lekcja z widoczną grupą i lekcja bez grup - zastępstwo dla całej klasy zostaje.
        assertTrue(LessonGroups.substitutionRelevant(substitution(4, null), raw, sel))
        assertTrue(LessonGroups.substitutionRelevant(substitution(5, null), raw, sel))
        // Godzina spoza planu - nie chowamy na ślepo.
        assertTrue(LessonGroups.substitutionRelevant(substitution(7, null), raw, sel))
    }

    @Test
    fun substitutionGetsSubjectOfTheAffectedGroup() {
        val raw = listOf(lesson(2, "ang-1/2", "niem-2/2"), lesson(3, "mat"))
        val sel = mapOf("niem" to LessonGroups.NONE)
        assertEquals("ang-1/2", LessonGroups.withSubject(substitution(2, null), raw, sel).originalSubject)
        assertEquals("niem-2/2", LessonGroups.withSubject(substitution(2, 2), raw, sel).originalSubject)
        assertEquals("mat", LessonGroups.withSubject(substitution(3, null), raw, emptyMap()).originalSubject)
        // Bez lekcji w planie i z przedmiotem już podanym - bez zmian.
        assertNull(LessonGroups.withSubject(substitution(7, null), raw, sel).originalSubject)
        val given = substitution(3, null).copy(originalSubject = "fiz")
        assertEquals("fiz", LessonGroups.withSubject(given, raw, sel).originalSubject)
    }

    @Test
    fun substitutionOverlayRemovedForOtherGroup() {
        val raw = listOf(lesson(2, "zaj.prakt-1/3", "zaj.prakt-2/3", sub = substitution(2, 1)))
        val out = LessonGroups.filter(raw, mapOf("zaj.prakt" to "2/3"))
        assertNull(out.single().substitution)
    }

    @Test
    fun notificationSubjectComesFromSubstitutedGroup() {
        // Dawniej przedmiot w powiadomieniu był zawsze z pierwszej grupy lekcji.
        val groups = lesson(2, "ang-1/2", "niem-2/2").groups
        assertEquals("niem-2/2", LessonGroups.subjectFor(substitution(2, 2), groups))
        assertEquals("ang-1/2", LessonGroups.subjectFor(substitution(2, 1), groups))
        // Bez numeru grupy: pierwsza z podanych (po filtrze - grupa użytkownika).
        val mine = LessonGroups.filter(listOf(lesson(2, "ang-1/2", "niem-2/2")), mapOf("ang" to LessonGroups.NONE)).single().groups
        assertEquals("niem-2/2", LessonGroups.subjectFor(substitution(2, null), mine))
    }

    @Test
    fun choiceMissingFromTheNewPlanIsStaleButNotGoingAndAbsentSubjectsAreKept() {
        // Nowy semestr: zaj.prakt dzielone na 2 zamiast 3 - wybór "2/3" ukrywał wszystkie lekcje.
        val plan = listOf(lesson(1, "zaj.prakt-1/2", "zaj.prakt-2/2"), lesson(2, "ang-1/2", "ang-2/2"), lesson(3, "relig"))
        val selections = mapOf("zaj.prakt" to "2/3", "ang" to "1/2", "relig" to LessonGroups.NONE, "niem" to "1/2")
        assertEquals(setOf("zaj.prakt"), LessonGroups.staleSelections(plan, selections))
        assertEquals(listOf(1, 2), LessonGroups.filter(plan, selections - "zaj.prakt").filter { it.groups.isNotEmpty() && it.number != 3 }.map { it.number })
        assertEquals(emptySet<String>(), LessonGroups.staleSelections(plan, emptyMap()))
    }

    @Test fun religionIsRecognisedForQuickChoice() {
        assertTrue(LessonGroups.isReligion("religia"))
        assertTrue(LessonGroups.isReligion("Relig."))
        assertFalse(LessonGroups.isReligion("wf"))
        assertFalse(LessonGroups.isReligion("j.angielski"))
    }
}
