package pl.zse.bydgoszcz.elektron.domain.model

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Komunikat dla użytkownika zamiast surowego wyjątku (dawniej na ekranie trafiało np.
 * "Unable to resolve host zastepstwa.zse.bydgoszcz.pl" albo "HTTP 503 dla https://...").
 */
object SyncErrors {

    const val OFFLINE = "Brak połączenia z internetem albo ze stroną szkoły."
    const val SERVER = "Strona szkoły chwilowo nie odpowiada - spróbuj za chwilę."
    const val GENERIC = "Nie udało się odświeżyć danych."

    private val HTTP_CODE = Regex("HTTP (\\d{3})")

    fun userMessage(t: Throwable): String {
        var e: Throwable? = t
        while (e != null) {
            when (e) {
                is SchoolPageChangedException -> return SchoolPageChangedException.USER_MESSAGE
                is UnknownHostException, is ConnectException, is SocketTimeoutException, is SSLException -> return OFFLINE
                is IOException -> HTTP_CODE.find(e.message.orEmpty())?.let { return SERVER }
            }
            e = e.cause
        }
        return if (t is IOException) OFFLINE else GENERIC
    }
}
