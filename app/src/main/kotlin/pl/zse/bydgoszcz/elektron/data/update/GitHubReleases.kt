package pl.zse.bydgoszcz.elektron.data.update

import org.json.JSONArray

/** Wydanie z GitHub Releases API (tylko pola, których potrzebujemy). */
data class GitHubRelease(
    val tag: String,
    val pageUrl: String,
    val draft: Boolean,
    val prerelease: Boolean
)

/**
 * Parser odpowiedzi https://api.github.com/repos/{owner}/{repo}/releases (tablica JSON).
 * Używamy listy, nie /releases/latest — ten pomija pre-release, a wersje alpha/beta
 * są wydawane właśnie jako pre-release.
 */
object GitHubReleases {

    const val REPO_RELEASES_PAGE = "https://github.com/stasolejnik/elektron/releases"

    fun parse(json: String): List<GitHubRelease> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GitHubRelease(
                tag = tag,
                pageUrl = safePageUrl(o.optString("html_url")),
                draft = o.optBoolean("draft", false),
                prerelease = o.optBoolean("prerelease", false)
            )
        }
    }

    /** Otwieramy tylko strony wydań repozytorium eLektrona; cokolwiek innego -> lista wydań. */
    private fun safePageUrl(url: String): String =
        if (url.startsWith("$REPO_RELEASES_PAGE/")) url else REPO_RELEASES_PAGE
}
