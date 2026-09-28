package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Prawdziwe nazwy oddziałów z plan.zse.bydgoszcz.pl/lista.html (wrzesień 2026). */
class ClassNamesTest {

    private val realNames = listOf(
        "1A 1A Ivy", "1B", "1D 1D PBŚ", "1F 1F Woj", "1H 1H Atos", "1I",
        "2A 2A Ivy", "2B", "2D 2D PBŚ", "2F 2F Woj", "2H 2H Atos", "2I",
        "3A 3A Ivy", "3D 3D PBŚ", "3F 3F Woj", "3H 3H Atos",
        "4A 4A Ivy", "4B", "4D 4D PBŚ", "4F 4F Woj", "4H 4H ATOS", "4I",
        "5A 5A Ivy", "5B", "5D", "5F 5F Woj", "5H 5H ATOS", "5I", "5J"
    )

    @Test
    fun noClassNameContainsRepeatedWord() {
        for (raw in realNames) {
            val words = ClassNames.cleanNonNull(raw).split(" ")
            assertFalse("Powtórzenie w \"$raw\" -> $words", words.zipWithNext().any { (a, b) -> a.equals(b, ignoreCase = true) })
        }
    }

    @Test
    fun keepsPatronSuffix() {
        assertEquals("1A Ivy", ClassNames.clean("1A 1A Ivy"))
        assertEquals("1D PBŚ", ClassNames.clean("1D 1D PBŚ"))
        assertEquals("4H ATOS", ClassNames.clean("4H 4H ATOS"))
        assertEquals("5J", ClassNames.clean("5J"))
        assertEquals("1D", ClassNames.clean("1D 1D"))
    }
}
