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
    fun withoutSubstituteInfoIsHeadline() {
        val s = sub("Uczniowie przychodzą później", null)
        assertEquals("Uczniowie przychodzą później", SubstitutionDisplay.headline(s))
        assertNull(SubstitutionDisplay.place(s))
        assertNull(SubstitutionDisplay.notes(sub("211", "M. Zelek", "  ")))
    }
}
