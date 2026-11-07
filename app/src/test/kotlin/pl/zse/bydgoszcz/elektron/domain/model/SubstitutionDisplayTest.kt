package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Prawdziwe wpisy ze strony zastępstw ZSE (28-29.09.2026). */
class SubstitutionDisplayTest {
    private fun sub(info: String, teacher: String?, notes: String? = null) =
        Substitution("x", LocalDate.of(2026, 9, 29), 3, "2H", null, info, teacher, notes, "J.Siepniewska-Stańczyk")

    @Test
    fun rooms() {
        listOf("211", "pc2", "W-6", "105", "PC3").forEach { assertTrue(it, SubstitutionDisplay.isRoom(it)) }
        listOf("Zajęcia Świetlicowe", "Uczniowie przychodzą później").forEach { assertFalse(it, SubstitutionDisplay.isRoom(it)) }
    }

    @Test
    fun teacherIsHeadlineRoomBelow() {
        val s = sub("211", "M. Zelek", "za ostatnią lekcję")
        assertEquals("M. Zelek", SubstitutionDisplay.headline(s))
        assertEquals("s. 211", SubstitutionDisplay.place(s))
        assertEquals("za ostatnią lekcję", SubstitutionDisplay.notes(s))
    }

    @Test
    fun dayViewPutsSchoolInfoFirstElsewhereTeacherFirst() {
        val s = sub("Zajęcia w auli", "M. Zelek", "za ostatnią lekcję")
        // Widok dnia w planie: informacja szkoły na pierwszym planie, nauczyciel pod nią.
        assertEquals("Zajęcia w auli", SubstitutionDisplay.headline(s, infoFirst = true))
        assertEquals("M. Zelek", SubstitutionDisplay.place(s, infoFirst = true))
        // Strona główna, zakładka Zastępstwa, widżety: najpierw nauczyciel.
        assertEquals("M. Zelek", SubstitutionDisplay.headline(s))
        assertEquals("Zajęcia w auli", SubstitutionDisplay.place(s))
        // Sama sala: bez różnicy.
        assertEquals("M. Zelek", SubstitutionDisplay.headline(sub("211", "M. Zelek"), infoFirst = true))
        assertEquals("s. 211", SubstitutionDisplay.place(sub("211", "M. Zelek"), infoFirst = true))
        assertEquals("Zajęcia w auli", SubstitutionDisplay.info(s))
        assertNull(SubstitutionDisplay.info(sub("211", "M. Zelek")))
    }

    @Test
    fun withoutSubstituteInfoIsHeadline() {
        val s = sub("Uczniowie przychodzą później", null)
        assertEquals("Uczniowie przychodzą później", SubstitutionDisplay.headline(s))
        assertNull(SubstitutionDisplay.place(s))
        assertNull(SubstitutionDisplay.notes(sub("211", "M. Zelek", "  ")))
    }

    @Test
    fun exemptionsAreDetected() {
        listOf("Uczniowie zwolnieni do domu", "Uczniowie przychodzą później", "Uczniowie przychodza pozniej",
            "Lekcja odwołana", "zajęcia nie odbywają się").forEach {
            assertTrue(it, SubstitutionDisplay.freesLesson(sub(it, null)))
        }
        // Świetlica i zwykłe zastępstwo - lekcja (zajęcia) się odbywa.
        assertFalse(SubstitutionDisplay.freesLesson(sub("Zajęcia Świetlicowe", null)))
        assertFalse(SubstitutionDisplay.freesLesson(sub("211", "M. Zelek")))
        // Zastępca wpisany - to nie zwolnienie, nawet przy dziwnej uwadze.
        assertFalse(SubstitutionDisplay.freesLesson(sub("211", "M. Zelek", "uczniowie zwolnieni z 1 lekcji")))
    }

    /** Prawdziwe wiersze z października 2026 (archiwum watchera). */
    @Test
    fun windowsAndMovedLessonsFreeTheLesson() {
        assertTrue(SubstitutionDisplay.freesLesson(sub("Okienko dla uczniów", null)))
        listOf(
            "przeniesiona na 22.10.2026 (0)" to "Zajęcia przeniesione na 0 h lek. do sali 215",
            "przeniesiona na 22.10.2026 (1)" to "Zajęcia przeniesione na 1 h lek. do sali 215",
            "przeniesiona na 23.10.2026 (7)" to "Matematyka przełożona na piątek na 7 h lekcyjną.",
            "przeniesiona na 29.10.2026 (0)" to "Zajęcia przeniesione na 0 h lek. do sali 215",
            "przeniesiona na 30.10.2026 (7)" to "Zajęcia przeniesione na piątek na 7 h lek do sali 1015"
        ).forEach { (info, notes) -> assertTrue(info, SubstitutionDisplay.freesLesson(sub(info, null, notes))) }
    }

    @Test
    fun groupWindowsRoomChangesAndLessonsMovedHereStillTakePlace() {
        assertFalse(SubstitutionDisplay.freesLesson(sub("Okienko dla uczniów", null, "okienko dla grupy 2/2")))
        assertFalse(SubstitutionDisplay.freesLesson(sub("Zajęcia Świetlicowe", null)))
        assertFalse(SubstitutionDisplay.freesLesson(sub("Zajęcia Świetlicowe", null, "II gr. zwolniona")))
        assertFalse(SubstitutionDisplay.freesLesson(sub("213", null, "przeniesione z 1h lek.")))
        assertFalse(SubstitutionDisplay.freesLesson(sub("215", null, "Zajęcia przeniesione do sali 215")))
        assertFalse(SubstitutionDisplay.freesLesson(sub("215", null, "Lekcja przeniesiona na salę 215")))
        // Z zastępcą lekcja się odbywa, także "przeniesiona z godz. 0".
        assertFalse(SubstitutionDisplay.freesLesson(sub("216", "R. Śmidoda", "wit.i ap. int - lekcja przeniesiona z godz. 0 z piątku")))
    }

    /** Uwagi z prawdziwych stron (październik-listopad 2026): lekcja na zastępstwie na pierwszym planie. */
    @Test
    fun substitutedLessonFromNotesIsTheHeadlineAndTeacherGoesSecond() {
        val s = sub("111", "D. Musierowicz", "edukacja zdrowotna")
        assertEquals("edukacja zdrowotna", SubstitutionDisplay.headline(s))
        assertEquals("edukacja zdrowotna", SubstitutionDisplay.headline(s, infoFirst = true))
        assertEquals("D. Musierowicz · s. 111", SubstitutionDisplay.place(s))
        assertNull(SubstitutionDisplay.notes(s))
        mapOf(
            "za nieobecny oddział; matematyka" to "matematyka",
            "matematyka, za nieobecny oddział" to "matematyka",
            "historia" to "historia",
            "j. angielski z 0 h lek. sala 204" to "j. angielski",
            "za nieobecny oddział na 8 h lek.; j. ang. zawodowy" to "j. ang. zawodowy",
            "informatyka - teoria" to "informatyka",
            "matematyka (zabrać zeszyty itp.)" to "matematyka",
            "wit.i ap. int - lekcja przeniesiona z godz. 0 z piątku" to "wit.i ap. int",
            "ed. obywatelska z 0 lekcji" to "ed. obywatelska",
            "r_fizyka" to "r_fizyka"
        ).forEach { (notes, lesson) -> assertEquals(notes, lesson, SubstitutionDisplay.lesson(sub("211", "M. Zelek", notes))) }
        assertEquals("za nieobecny oddział", SubstitutionDisplay.notes(sub("211", "M. Zelek", "za nieobecny oddział; matematyka")))
        // Dopisek przy lekcji zostaje w uwagach.
        assertEquals("j. angielski z 0 h lek. sala 204", SubstitutionDisplay.notes(sub("211", "M. Zelek", "j. angielski z 0 h lek. sala 204")))
    }

    @Test
    fun organisationalNotesKeepTheTeacherHeadline() {
        listOf("za nieobecny oddział", "za ostatnią lekcję", "złączenie grup", "przeniesione z 1h lek.",
            "Zajęcia przełożone z 1 godziny lekcyjnej.",
            "za nieobecny oddział; grupa 2/2 wf - teoria; grupa 1/2 zwolniona do domu",
            "za nieobecny oddział; osoby uczeszczajace na religię - j. niemiecki; pozostałe osoby zwolnione do domu"
        ).forEach { notes ->
            val s = sub("211", "M. Zelek", notes)
            assertNull(notes, SubstitutionDisplay.lesson(s))
            assertEquals(notes, "M. Zelek", SubstitutionDisplay.headline(s))
            assertEquals(notes, SubstitutionDisplay.notes(s))
        }
        // Bez zastępcy - informacja szkoły jak dotąd.
        assertNull(SubstitutionDisplay.lesson(sub("Zajęcia Świetlicowe", null, "historia")))
    }
}
