package pl.zse.bydgoszcz.elektron.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto

/** Zajęcia łączone ("Wychowanie fizyczne - Zajęcia Świetlicowe", 9.10.2026): klasa i grupa z planu. */
class ClasslessSubstitutionsTest {
    private val entry = SubstitutionDto("09.10.2026", "Dariusz Czyżewski", 3, "", null, "Zajęcia Świetlicowe", null, null, subject = "Wychowanie fizyczne")

    @Test fun teacherIsMatchedBySurnameAndInitial() {
        assertTrue(ClasslessSubstitutions.teacherMatches("Dariusz Czyżewski", "D.Czyżewski"))
        assertTrue(ClasslessSubstitutions.teacherMatches("Dariusz Czyżewski", "D. Czyzewski"))
        assertTrue(ClasslessSubstitutions.teacherMatches("Anna Kowalska-Nowak", "A.Kowalska-Nowak"))
        assertTrue(ClasslessSubstitutions.teacherMatches("Łukasz Łęcki", "Ł.Łęcki"))
        assertFalse(ClasslessSubstitutions.teacherMatches("Dariusz Czyżewski", "M.Czyżewski"))   // inny inicjał
        assertFalse(ClasslessSubstitutions.teacherMatches("Dariusz Czyżewski", "D.Chabowski"))
        assertFalse(ClasslessSubstitutions.teacherMatches("Dariusz Czyżewski", "Cz"))            // sam skrót
        assertFalse(ClasslessSubstitutions.teacherMatches("Dariusz Czyżewski", null))
    }

    @Test fun joinedLessonGetsTheClassAndGroupOfTheTeachersLesson() {
        val slots = listOf(
            ClasslessSubstitutions.Slot("09.10.2026", 3, "1D", "wf-1/2", "M.Chabowski"),
            ClasslessSubstitutions.Slot("09.10.2026", 3, "1D", "wf-2/2", "D.Czyżewski"),
            ClasslessSubstitutions.Slot("09.10.2026", 3, "1E", "wf", "D.Czyżewski"),        // bez podziału - cała klasa
            ClasslessSubstitutions.Slot("09.10.2026", 4, "1D", "wf-2/2", "D.Czyżewski"),     // inna lekcja
            ClasslessSubstitutions.Slot("10.10.2026", 3, "1D", "wf-2/2", "D.Czyżewski")      // inny dzień
        )
        val resolved = ClasslessSubstitutions.resolve(listOf(entry), slots)
        assertEquals(listOf("1D" to 2, "1E" to null), resolved.map { it.classShortName to it.groupNumber })
        assertTrue(resolved.all { it.roomOrInfo == "Zajęcia Świetlicowe" && it.originalTeacher == "Dariusz Czyżewski" && it.lessonNumber == 3 })
        // Nauczyciela nie ma w zapisanym planie - wpis nie dotyczy żadnej zapisanej klasy.
        assertTrue(ClasslessSubstitutions.resolve(listOf(entry.copy(originalTeacher = "Jan Kowalski")), slots).isEmpty())
        // Ta sama grupa dwa razy w planie (np. dwa wpisy grupy) - jedno zastępstwo.
        assertEquals(1, ClasslessSubstitutions.resolve(listOf(entry), slots.take(2) + slots[1]).size)
    }

    @Test fun jointGroupWithoutTeacherInTheClassPlanIsMatchedBySubject() {
        // Plan klasy przy zajęciach łączonych: "wf-j2 #1AF Hala5" - bez nauczyciela (o4.html, o26.html).
        val slots = listOf(
            ClasslessSubstitutions.Slot("09.10.2026", 3, "1F", "wf-j2", null, "#1AF"),
            ClasslessSubstitutions.Slot("09.10.2026", 3, "1A", "wf-j2", "D.Czyżewski"),       // klasa z nauczycielem w planie
            ClasslessSubstitutions.Slot("09.10.2026", 3, "2B", "j.angielski-1/2", null, "#2BC"), // inny przedmiot
            ClasslessSubstitutions.Slot("09.10.2026", 3, "3C", "wf-j1", null, "#3CD"),
            ClasslessSubstitutions.Slot("09.10.2026", 3, "3C", "wf-j2", null, "#3CD"))         // dwie grupy - nie wiadomo, która
        val resolved = ClasslessSubstitutions.resolve(listOf(entry), slots)
        assertEquals(listOf("1A" to 2, "1F" to 2), resolved.map { it.classShortName to it.groupNumber }.sortedBy { it.first })
    }

    @Test fun jointGroupIsNotGuessedWithoutTheTeacherInASharingClassPlan() {
        // Zapisany tylko plan 1F - nie wiadomo, czy nieobecny nauczyciel prowadzi akurat tę grupę.
        val onlyOwnClass = listOf(ClasslessSubstitutions.Slot("09.10.2026", 3, "1F", "wf-j2", null, "#1AF"))
        assertTrue(ClasslessSubstitutions.resolve(listOf(entry), onlyOwnClass).isEmpty())
        // Nauczyciel prowadzi w 1A INNĄ grupę (j1) - grupa j2 1F zostaje bez zmian (np. bez fałszywego zwolnienia).
        val otherGroup = onlyOwnClass + ClasslessSubstitutions.Slot("09.10.2026", 3, "1A", "wf-j1", "D.Czyżewski")
        assertEquals(listOf("1A" to 1), ClasslessSubstitutions.resolve(listOf(entry), otherGroup).map { it.classShortName to it.groupNumber })
        // Klasa nauczyciela spoza łączenia (#1AF) też nie potwierdza.
        val outside = onlyOwnClass + ClasslessSubstitutions.Slot("09.10.2026", 3, "2B", "wf-j2", "D.Czyżewski")
        assertEquals(listOf("2B"), ClasslessSubstitutions.resolve(listOf(entry), outside).map { it.classShortName })
    }

    @Test fun jointClassReferencesAreSplitIntoClasses() {
        assertEquals(setOf("1A", "1F"), ClasslessSubstitutions.jointClasses("#1AF"))
        assertEquals(setOf("1A", "2B"), ClasslessSubstitutions.jointClasses("#1A2B"))
        assertTrue(ClasslessSubstitutions.jointClasses(null).isEmpty())
    }

    @Test fun twoTeachersWithTheSameSurnameAndInitialAreNotGuessed() {
        val slots = listOf(ClasslessSubstitutions.Slot("09.10.2026", 3, "1D", "wf-2/2", "D.Czyżewski"))
        assertEquals(1, ClasslessSubstitutions.resolve(listOf(entry), slots, listOf("D.Czyżewski", "M.Chabowski")).size)
        assertTrue(ClasslessSubstitutions.resolve(listOf(entry), slots, listOf("D.Czyżewski", "D.Czyżewski", "M.Chabowski")).isEmpty())
    }

    @Test fun subjectsFromThePageMatchPlanAbbreviations() {
        assertTrue(ClasslessSubstitutions.subjectMatches("Wychowanie fizyczne", "wf-j2"))
        assertTrue(ClasslessSubstitutions.subjectMatches("Język angielski", "j.angielski-2/2"))
        assertTrue(ClasslessSubstitutions.subjectMatches("Religia", "religia"))
        assertTrue(ClasslessSubstitutions.subjectMatches("Informatyka", "inf"))
        assertFalse(ClasslessSubstitutions.subjectMatches("Wychowanie fizyczne", "j.angielski-1/2"))
        assertFalse(ClasslessSubstitutions.subjectMatches("Matematyka", "wf-j2"))
        assertFalse(ClasslessSubstitutions.subjectMatches(null, "wf"))
    }
}
