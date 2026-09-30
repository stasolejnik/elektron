package pl.zse.bydgoszcz.elektron.data.update

import org.json.JSONArray
import org.json.JSONObject

/** Wydanie z GitHub Releases API (tylko pola, których potrzebujemy). */
data class GitHubRelease(
    val tag: String,
    val pageUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    /** Plik .apk z Assets (tylko z repozytorium eLektrona), inaczej null. */
    val apkUrl: String? = null,
    /** SHA-256 pliku APK (pole "digest" GitHuba, "sha256:..."), inaczej null. */
    val apkSha256: String? = null,
    val apkSize: Long = 0
)

/**
 * Parser odpowiedzi https://api.github.com/repos/{owner}/{repo}/releases (tablica JSON).
 * Używamy listy, nie /releases/latest — ten pomija pre-release, a wersje alpha/beta
 * są wydawane właśnie jako pre-release.
 */
object GitHubReleases {

    const val REPO_RELEASES_PAGE = "https://github.com/stasolejnik/elektron/releases"
    private const val DOWNLOAD_PREFIX = "$REPO_RELEASES_PAGE/download/"
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    fun parse(json: String): List<GitHubRelease> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val apk = apkAsset(o)
            GitHubRelease(
                tag = tag,
                pageUrl = safePageUrl(o.optString("html_url")),
                draft = o.optBoolean("draft", false),
                prerelease = o.optBoolean("prerelease", false),
                apkUrl = apk?.optString("browser_download_url"),
                apkSha256 = apk?.optString("digest")
                    ?.removePrefix("sha256:")?.lowercase()?.takeIf { SHA256_HEX.matches(it) },
                apkSize = apk?.optLong("size", 0L) ?: 0L
            )
        }
    }

    /**
     * Pierwszy plik .apk z Assets, pobierany wyłącznie z wydań repozytorium eLektrona
     * (https://github.com/stasolejnik/elektron/releases/download/...). Inny adres - brak APK
     * (aplikacja otworzy wtedy stronę wydania).
     */
    private fun apkAsset(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        return (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.firstOrNull { a ->
            a.optString("name").endsWith(".apk", ignoreCase = true) &&
                a.optString("browser_download_url").startsWith(DOWNLOAD_PREFIX)
        }
    }

    /** Otwieramy tylko strony wydań repozytorium eLektrona; cokolwiek innego -> lista wydań. */
    private fun safePageUrl(url: String): String =
        if (url.startsWith("$REPO_RELEASES_PAGE/")) url else REPO_RELEASES_PAGE
}
