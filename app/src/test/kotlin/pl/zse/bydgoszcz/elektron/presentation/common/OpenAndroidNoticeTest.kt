package pl.zse.bydgoszcz.elektron.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OpenAndroidNoticeTest {
    @Test fun firstDayIsFourteenDaysBeforeNewYear2027() {
        assertEquals(LocalDate.of(2026, 12, 18), OpenAndroidNotice.firstDay)
    }

    @Test fun dueFromFirstDayUntilShown() {
        assertFalse(OpenAndroidNotice.isDue(LocalDate.of(2026, 12, 17), shown = false, simulate = false))
        assertTrue(OpenAndroidNotice.isDue(LocalDate.of(2026, 12, 18), shown = false, simulate = false))
        assertTrue(OpenAndroidNotice.isDue(LocalDate.of(2027, 2, 1), shown = false, simulate = false))
        assertFalse(OpenAndroidNotice.isDue(LocalDate.of(2026, 12, 20), shown = true, simulate = false))
    }

    @Test fun developerSimulationIgnoresDate() {
        assertTrue(OpenAndroidNotice.isDue(LocalDate.of(2026, 11, 24), shown = false, simulate = true))
        assertFalse(OpenAndroidNotice.isDue(LocalDate.of(2026, 11, 24), shown = true, simulate = true))
    }
}
