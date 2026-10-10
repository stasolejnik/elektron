package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test

class UpdateChannelTest {
    @Test fun stableChannelAcceptsOnlyStableReleases() {
        assertTrue(UpdateChannel.STABLE.accepts("v1.0.1", prerelease = false))
        assertFalse(UpdateChannel.STABLE.accepts("v1.0.1-beta1", prerelease = false))
        assertFalse(UpdateChannel.STABLE.accepts("v1.0.1-rc2", prerelease = false))
        // Oznaczone na GitHubie jako Pre-release - też nie, nawet bez dopisku w numerze.
        assertFalse(UpdateChannel.STABLE.accepts("v1.0.1", prerelease = true))
    }

    @Test fun betaChannelAcceptsBetasAndStableReleases() {
        assertTrue(UpdateChannel.BETA.accepts("v1.0.1-beta1", prerelease = true))
        assertTrue(UpdateChannel.BETA.accepts("v1.0.1", prerelease = false))
    }

    @Test fun unknownOrMissingKeyMeansStable() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey(null))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey("nightly"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromKey("beta"))
    }

    @Test fun stableVersionDetection() {
        assertTrue(AppVersion.isStable("1.0.0"))
        assertTrue(AppVersion.isStable("v1.0.0-debug"))
        assertFalse(AppVersion.isStable("1.0.0-alpha"))
        assertFalse(AppVersion.isStable("1.0.0-dev"))
    }
}
