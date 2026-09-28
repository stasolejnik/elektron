package pl.zse.bydgoszcz.elektron.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GitHubReleasesTest {

    private val json = """
        [
          {"tag_name": "v0.6.0-alpha", "html_url": "https://github.com/stasolejnik/elektron/releases/tag/v0.6.0-alpha", "draft": true,  "prerelease": true},
          {"tag_name": "v0.5.0-alpha", "html_url": "https://github.com/stasolejnik/elektron/releases/tag/v0.5.0-alpha", "draft": false, "prerelease": true},
          {"tag_name": "v0.4.0-alpha", "html_url": "https://evil.example/phishing", "draft": false, "prerelease": true}
        ]
    """.trimIndent()

    @Test
    fun parsesReleasesAndFlags() {
        val releases = GitHubReleases.parse(json)
        assertEquals(listOf("v0.6.0-alpha", "v0.5.0-alpha", "v0.4.0-alpha"), releases.map { it.tag })
        assertTrue(releases[0].draft)
        assertTrue(releases[1].prerelease)
    }

    @Test
    fun foreignLinksFallBackToReleasesPage() {
        // Otwieramy tylko strony wydań repozytorium eLektrona.
        assertEquals(GitHubReleases.REPO_RELEASES_PAGE, GitHubReleases.parse(json)[2].pageUrl)
    }
}
