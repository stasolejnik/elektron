package pl.zse.bydgoszcz.elektron.data.remote.http

import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Centralny klient HTTP dla wszystkich źródeł eLektron.
 * Cechy: User-Agent z faktyczną wersją appki, timeouty, retry z backoffem,
 * dyskowy cache HTTP (zapytania warunkowe ETag/Last-Modified -> 304 zamiast pełnej strony).
 */
object OkHttpClientProvider {

    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024

    fun create(debug: Boolean, versionName: String, cacheDir: File): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val userAgent = "eLektron/$versionName (+https://github.com/stasolejnik/elektron)"
        return OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), CACHE_SIZE_BYTES))
            .addInterceptor(HeaderInterceptor(userAgent))
            .addInterceptor(logging)
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // callTimeout obejmuje całe wywołanie łącznie z retry z RetryInterceptora.
            .callTimeout(45, TimeUnit.SECONDS)
            // Własny RetryInterceptor już ponawia przy IOException — wbudowane
            // retryOnConnectionFailure dublowało próby (do 6 połączeń zamiast 3).
            .retryOnConnectionFailure(false)
            // Nie podążamy za przekierowaniem z HTTPS na HTTP (obniżenie szyfrowania).
            .followSslRedirects(false)
            .build()
    }

    private class HeaderInterceptor(private val userAgent: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val req = original.newBuilder()
                .header("User-Agent", userAgent)
                // Domyślne Accept (strony szkoły) tylko gdy zapytanie nie ma własnego
                // (np. API GitHuba przy sprawdzaniu aktualizacji).
                .header("Accept", original.header("Accept")
                    ?: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl,en;q=0.5")
                // Zawsze rewalidacja u serwera (If-None-Match / If-Modified-Since).
                // Bez tego OkHttp dla stron z Last-Modified bez Cache-Control liczy
                // heurystyczną "świeżość" i mógłby serwować zastępstwa z cache godzinami.
                // Z no-cache: 304 -> treść z cache (oszczędność transferu), 200 -> nowa treść.
                .cacheControl(CacheControl.Builder().noCache().build())
                .build()
            return chain.proceed(req)
        }
    }

    private class RetryInterceptor(private val maxRetries: Int) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var attempt = 0
            var lastError: IOException? = null
            while (attempt < maxRetries) {
                // Anulowane wywołanie (np. anulowany sync) nie powinno dalej ponawiać.
                if (chain.call().isCanceled()) throw IOException("Canceled")
                try {
                    val response = chain.proceed(chain.request())
                    if (response.code in 500..599 && attempt < maxRetries - 1) {
                        response.close()
                        backoff(attempt)
                        attempt++
                        continue
                    }
                    return response
                } catch (e: IOException) {
                    lastError = e
                    if (attempt >= maxRetries - 1 || chain.call().isCanceled()) throw e
                    backoff(attempt)
                    attempt++
                }
            }
            throw lastError ?: IOException("RetryInterceptor: exhausted retries")
        }

        private fun backoff(attempt: Int) {
            try {
                Thread.sleep((500L * (1L shl attempt)).coerceAtMost(4_000L))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted", e)
            }
        }
    }
}
