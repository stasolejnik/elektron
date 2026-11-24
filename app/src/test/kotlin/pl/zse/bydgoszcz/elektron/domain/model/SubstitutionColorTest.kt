package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstitutionColorTest {
    @Test fun defaultKeepsTheThemeColor() {
        // "Domyślny" - kolor motywu (pomarańczowy albo z tapety), bez nadpisywania.
        assertNull(SubstitutionColors.light(SubstitutionColor.DEFAULT))
        assertNull(SubstitutionColors.dark(SubstitutionColor.DEFAULT))
        assertEquals(SubstitutionColor.DEFAULT, SubstitutionColor.fromKey(null))
        assertEquals(SubstitutionColor.DEFAULT, SubstitutionColor.fromKey("nieznany"))
        assertEquals(SubstitutionColor.TEAL, SubstitutionColor.fromKey("teal"))
    }

    @Test fun everyChoiceHasTonesInBothModesAndNoRed() {
        SubstitutionColor.values().filter { it != SubstitutionColor.DEFAULT }.forEach { color ->
            val light = SubstitutionColors.light(color, 0xFF3366CC)!!
            val dark = SubstitutionColors.dark(color, 0xFF3366CC)!!
            assertTrue(color.name, light.color != dark.color)
        }
        // Czerwień jest tylko dla błędów - nie ma jej wśród gotowych kolorów.
        assertTrue(SubstitutionColor.values().none { it.key == "red" })
    }

    @Test fun customColorUsesThePicker() {
        assertEquals(AccentMath.light(0xFF3366CC).primary, SubstitutionColors.light(SubstitutionColor.CUSTOM, 0xFF3366CC)!!.color)
        assertEquals(AccentMath.dark(0xFF3366CC).container, SubstitutionColors.dark(SubstitutionColor.CUSTOM, 0xFF3366CC)!!.container)
    }
}
