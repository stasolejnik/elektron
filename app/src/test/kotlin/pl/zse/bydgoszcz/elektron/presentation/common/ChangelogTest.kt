package pl.zse.bydgoszcz.elektron.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    @Test fun stableUpdateFrom100ShowsOnlyStableEntries() {
        assertEquals(listOf("1.0.2", "1.0.1"), Changelog.newerThan(35, "1.0.2").map { it.versionName })
        assertEquals(listOf("1.0.2", "1.0.1"), Changelog.newerThan(35, "1.0.2-debug").map { it.versionName })
    }

    @Test fun betaBuildsStillShowBetaEntries() {
        assertTrue(Changelog.newerThan(35, "1.0.3-beta1").any { it.versionName == "1.0.1-beta1" })
        assertEquals(listOf("1.0.2"), Changelog.newerThan(37, "1.0.2").map { it.versionName })
    }

    @Test fun newestEntryMatchesBuildVersion() {
        val newest = Changelog.entries.maxBy { it.versionCode }
        assertEquals(pl.zse.bydgoszcz.elektron.BuildConfig.VERSION_CODE, newest.versionCode)
        assertTrue(pl.zse.bydgoszcz.elektron.BuildConfig.VERSION_NAME.startsWith(newest.versionName))
    }

    @Test fun textsHaveNoLongDashes() {
        assertTrue(Changelog.entries.first().items.none { '—' in it || '–' in it })
    }
}
