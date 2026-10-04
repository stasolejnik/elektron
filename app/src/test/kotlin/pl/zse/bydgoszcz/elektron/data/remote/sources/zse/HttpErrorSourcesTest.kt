package pl.zse.bydgoszcz.elektron.data.remote.sources.zse

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Błąd HTTP strony szkoły to porażka synchronizacji, nie "pusta lista = sukces".
 * Dawniej lista klas, oba kanały RSS i archiwum zwracały przy 5xx pustą listę, a sync
 * liczył je jako udane źródła.
 */
class HttpErrorSourcesTest {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(503).message("Service Unavailable").body("".toResponseBody()).build()
        }
        .build()

    private fun assertHttpError(block: suspend () -> Any?) = runBlocking {
        try {
            block()
            fail("Oczekiwano IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("HTTP 503"))
        }
    }

    @Test fun sidebarThrows() = assertHttpError { ZseTimetableSource(client).fetchSidebar() }
    @Test fun newsFeedThrows() = assertHttpError { ZseRssAnnouncementsSource(client).fetchNewsFeed() }
    @Test fun latestFeedThrows() = assertHttpError { ZseRssAnnouncementsSource(client).fetchLatestFeed() }
    @Test fun articleThrows() = assertHttpError { ZseRssAnnouncementsSource(client).fetchArticleHtml("https://zse.bydgoszcz.pl/test.html") }
    @Test fun archivePageThrows() = assertHttpError { ZseRssAnnouncementsSource(client).fetchArchivePage(3) }
}
