package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlanRowIdTest {
    @Test fun changedContentGivesNewRowIdSoTheLauncherRedrawsIt() {
        // Ta sama lekcja 3 w innych kolorach (przezroczystość) albo innego dnia (symulacja czasu).
        assertNotEquals(dayPlanRowId(3, 111), dayPlanRowId(3, 222))
        assertEquals(dayPlanRowId(3, 111), dayPlanRowId(3, 111))
    }

    @Test fun rowIdIsNonNegativeAndKeepsTheLessonNumber() {
        for (hash in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val id = dayPlanRowId(8, hash)
            assertTrue(id >= 0)
            assertEquals(8L, id and 0xFF)
        }
        assertNotEquals(dayPlanRowId(1, 5), dayPlanRowId(2, 5))
    }
}
