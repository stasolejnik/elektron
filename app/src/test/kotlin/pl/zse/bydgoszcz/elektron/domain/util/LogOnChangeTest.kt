package pl.zse.bydgoszcz.elektron.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogOnChangeTest {
    @Test fun repeatedStateIsLoggedOnce() {
        val log = LogOnChange()
        assertTrue(log.changed(listOf("LESSON", "08:45")))
        assertFalse("odświeżenie paska co minutę - bez wpisu", log.changed(listOf("LESSON", "08:45")))
        assertTrue(log.changed(listOf("BREAK", "08:55")))
        assertTrue("powrót do poprzedniego stanu to też zmiana", log.changed(listOf("LESSON", "08:45")))
    }
}
