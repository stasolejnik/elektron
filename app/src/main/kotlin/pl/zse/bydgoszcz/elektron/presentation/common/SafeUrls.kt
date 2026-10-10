package pl.zse.bydgoszcz.elektron.presentation.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Bezpieczne otwieranie linków.
 *
 * - [isWebUrl]: tylko http/https — nigdy "javascript:", "intent:", "file:" itp. (linki
 *   pochodzą z RSS, archiwum strony i pushy; nie ufamy im na ślepo).
 * - [isSchoolUrl]: dodatkowo tylko domeny szkoły. Tak sprawdzamy linki przychodzące z intentu
 *   do ElektronActivity — ta jest eksportowana (launcher), więc dowolna inna aplikacja mogła ją
 *   uruchomić z własnym linkiem i eLektron otwierał go w przeglądarce (np. stronę udającą szkołę).
 */
object SafeUrls {

    private val SCHOOL_HOSTS = listOf("zse.bydgoszcz.pl", "zse.edu.bydgoszcz.pl")

    /** Adres http(s) z hostem, sparsowany raz — albo null, gdy link jest niebezpieczny. */
    private fun webUri(url: String?): Uri? {
        val uri = url?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        return uri.takeIf { !it.host.isNullOrBlank() }
    }

    fun isWebUrl(url: String?): Boolean = webUri(url) != null

    fun isSchoolUrl(url: String?): Boolean {
        val host = webUri(url)?.host?.lowercase() ?: return false
        return SCHOOL_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /** Obrazek tylko z serwerów szkoły (miniatury z bazy, archiwum i pushy), inaczej null. */
    fun schoolImage(url: String?): String? = url?.takeIf(::isSchoolUrl)

    /** Otwiera link w przeglądarce, jeśli to bezpieczny adres http(s). Zwraca, czy się udało. */
    fun open(context: Context, url: String?): Boolean {
        val uri = webUri(url)
        if (uri == null) {
            Log.w(TAG, "Odrzucono niebezpieczny link: $url")
            return false
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Brak przeglądarki dla $url", e)
            false
        }
    }

    private const val TAG = "SafeUrls"
}
