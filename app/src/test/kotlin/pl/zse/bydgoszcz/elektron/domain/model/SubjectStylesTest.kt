package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectStylesTest {

    @Test fun roundTrip() {
        val map = mapOf(
            "j.polski" to SubjectStyle("Polski", 0xFFE53935),
            "wf" to SubjectStyle(color = 0xFF7CB342),
            "mat" to SubjectStyle(name = "Matma")
        )
        assertEquals(map, SubjectStyles.decode(SubjectStyles.encode(map)))
    }

    @Test fun defaultsAreNotStored() {
        assertEquals("", SubjectStyles.encode(mapOf("fiz" to SubjectStyle())))
        assertTrue(SubjectStyles.decode("fiz\t\t").isEmpty())
    }

    @Test fun groupSuffixSharesStyle() {
        // "wf-j1" i "wf-j2" to ten sam przedmiot - jedna nazwa i kolor dla obu grup.
        val styles = mapOf("wf" to SubjectStyle("WF", 0xFF7CB342))
        assertEquals("WF", SubjectStyles.displayName("wf-j1", styles))
        assertEquals(0xFF7CB342, SubjectStyles.colorOf("wf-j2", styles))
    }

    @Test fun unstyledSubjectKeepsOriginalName() {
        assertEquals("fiz", SubjectStyles.displayName("fiz", emptyMap()))
        assertNull(SubjectStyles.displayName(null, emptyMap()))
        assertNull(SubjectStyles.colorOf("fiz", emptyMap()))
    }

    @Test fun cleanName() {
        assertEquals("Polski", SubjectStyles.cleanName("  Polski\t", "j.polski"))
        assertNull(SubjectStyles.cleanName("   ", "j.polski"))          // pusta = domyślna
        assertNull(SubjectStyles.cleanName("j.polski", "j.polski"))     // jak oryginał = domyślna
        assertEquals(40, SubjectStyles.cleanName("x".repeat(60), "a")!!.length)
    }

    @Test fun brokenLinesAreSkipped() {
        val decoded = SubjectStyles.decode("\n\tbez klucza\t\nmat\tMatma\tZZZ\n")
        assertEquals(mapOf("mat" to SubjectStyle("Matma", null)), decoded)
    }

    @Test fun everyColorHasName() {
        assertEquals(SubjectStyles.PALETTE.size, SubjectStyles.COLOR_NAMES.size)
    }
}
