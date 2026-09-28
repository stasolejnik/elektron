package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Szybki wybór grup według typu podziału (1/2 2/2, 1/3 2/3 3/3, WF). */
class LessonGroupsDivisionsTest {

    private val subjects = listOf(
        LessonGroups.DividedSubject("ang", listOf("1/2")),
        LessonGroups.DividedSubject("niem", listOf("2/2")),
        LessonGroups.DividedSubject("inf", listOf("1/2", "2/2")),
        LessonGroups.DividedSubject("zaj.prakt", listOf("1/3", "2/3", "3/3")),
        LessonGroups.DividedSubject("rel", listOf("1/1")),
        LessonGroups.DividedSubject("wf", listOf("j1", "j2"))
    )

    @Test
    fun divisionsByType() {
        val d = LessonGroups.divisions(subjects)
        assertEquals(listOf("/2", "/3", "j"), d.map { it.key })          // religia 1/1 bez szybkiego wyboru
        assertEquals(listOf("1/2", "2/2"), d[0].options)
        assertEquals("Podział na 2 grupy", d[0].title)
        assertEquals(listOf("ang", "inf", "niem"), d[0].subjects)
        assertEquals(listOf("j1", "j2"), d[2].options)
    }

    @Test
    fun applyingGroupOneOfTwo() {
        val r = LessonGroups.applyDivision(subjects, "/2", "1/2")
        assertEquals(mapOf("ang" to "1/2", "niem" to LessonGroups.NONE, "inf" to "1/2"), r)
    }

    @Test
    fun resetToAll() {
        val r = LessonGroups.applyDivision(subjects, "/3", null)
        assertEquals(mapOf("zaj.prakt" to null), r)
    }

    @Test
    fun selectedOptionIsDetected() {
        val d = LessonGroups.divisions(subjects).first { it.key == "/2" }
        val sel = mapOf("ang" to "1/2", "niem" to LessonGroups.NONE, "inf" to "1/2")
        assertEquals("1/2", LessonGroups.selectedDivisionOption(d, subjects, sel))
        assertEquals("", LessonGroups.selectedDivisionOption(d, subjects, emptyMap()))
        assertNull(LessonGroups.selectedDivisionOption(d, subjects, mapOf("ang" to "1/2", "inf" to "2/2")))
    }
}
