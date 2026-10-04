package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun ordering() {
        assertTrue(AppVersion.isNewer("0.5.0-beta", "0.5.0-alpha"))
        assertTrue(AppVersion.isNewer("0.5.0", "0.5.0-rc1"))
        assertTrue(AppVersion.isNewer("0.5.0-rc2", "0.5.0-rc1"))
        assertTrue(AppVersion.isNewer("0.5.1-alpha", "0.5.0"))
        assertTrue(AppVersion.isNewer("1.0.0-beta", "0.9.9"))
        assertTrue(AppVersion.isNewer("0.10.0", "0.9.0"))       // liczbowo, nie tekstowo
    }

    @Test
    fun rcHotfixOrdering() {
        assertTrue(AppVersion.isNewer("v1.0.0-rc5.1", "1.0.0-rc5"))
        assertTrue(AppVersion.isNewer("1.0.0-rc5.10", "1.0.0-rc5.2"))
        assertTrue(AppVersion.isNewer("1.0.0-rc6", "1.0.0-rc5.1"))
        assertTrue(AppVersion.isNewer("1.0.0", "1.0.0-rc5.1"))
        assertFalse(AppVersion.isNewer("v1.0.0-rc5.1", "1.0.0-rc5.1-debug"))
        assertFalse(AppVersion.isNewer("1.0.0-rc5", "1.0.0-rc5.1"))
    }

    @Test
    fun tagsAndDebugBuilds() {
        assertFalse(AppVersion.isNewer("v0.5.0-alpha", "0.5.0-alpha-debug"))   // ta sama wersja
        assertTrue(AppVersion.isNewer("v0.6.0-alpha", "0.5.0-alpha-debug"))
        assertFalse(AppVersion.isNewer("v0.4.0-alpha", "0.5.0-alpha"))
    }
}
