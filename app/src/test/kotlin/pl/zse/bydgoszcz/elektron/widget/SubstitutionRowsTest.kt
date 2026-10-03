package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SubstitutionRowsTest {

    @Test
    fun capacityFromHeight() {
        assertEquals(1, SubstitutionRows.capacity(0f))
        assertEquals(1, SubstitutionRows.capacity(40f))         // mniejszy niż nagłówek
        assertEquals(1, SubstitutionRows.capacity(Float.NaN))
        assertEquals(1, SubstitutionRows.capacity(56f + 51f))   // jeszcze nie mieści się pełny wiersz
        assertEquals(1, SubstitutionRows.capacity(56f + 52f))
        assertEquals(1, SubstitutionRows.capacity(56f + 103f))
        assertEquals(2, SubstitutionRows.capacity(56f + 104f))
        assertEquals(3, SubstitutionRows.capacity(250f))        // typowy widżet 4x3
        assertEquals(6, SubstitutionRows.capacity(56f + 6 * 52f))
        assertEquals(6, SubstitutionRows.capacity(2000f))       // bardzo duży - najwyżej 6
    }

    @Test
    fun allFitWithoutMoreRow() {
        assertEquals(SubstitutionRows.Layout(0, 0), SubstitutionRows.layout(250f, 0))
        assertEquals(SubstitutionRows.Layout(3, 0), SubstitutionRows.layout(250f, 3))
        assertEquals(SubstitutionRows.Layout(1, 0), SubstitutionRows.layout(110f, 1))
    }

    @Test
    fun overflowShowsMoreRow() {
        // 3 miejsca, 5 zastępstw: 2 wiersze + "+3 więcej".
        assertEquals(SubstitutionRows.Layout(2, 3), SubstitutionRows.layout(250f, 5))
        // Bardzo mały widżet: zawsze jedno zastępstwo i "+N więcej".
        assertEquals(SubstitutionRows.Layout(1, 4), SubstitutionRows.layout(60f, 5))
        assertEquals(SubstitutionRows.Layout(1, 1), SubstitutionRows.layout(110f, 2))
        // Bardzo duży: 5 wierszy + "+N więcej" (razem 6).
        assertEquals(SubstitutionRows.Layout(5, 15), SubstitutionRows.layout(2000f, 20))
    }
}
