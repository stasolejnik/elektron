package pl.zse.bydgoszcz.elektron.data.remote.sources.zse

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import pl.zse.bydgoszcz.elektron.data.remote.http.readCancellable
import okhttp3.Request
import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto
import pl.zse.bydgoszcz.elektron.data.remote.http.EncodingAwareBody
import pl.zse.bydgoszcz.elektron.data.remote.parser.NewsArticleParser
import pl.zse.bydgoszcz.elektron.data.remote.parser.NewsArchiveParser
import pl.zse.bydgoszcz.elektron.data.remote.parser.RssParser
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja AnnouncementsSource dla RSS zse.bydgoszcz.pl (UTF-8, RSS 2.0).
 * Parsowanie w trybie XML (Parser.xmlParser()).
 */
@Singleton
class ZseRssAnnouncementsSource @Inject constructor(
    private val client: OkHttpClient
) : AnnouncementsSource {

    override suspend fun fetchNewsFeed(): List<RssItemDto> =
        fetchFeed(SchoolEndpoints.School.RSS_NEWS, RssItemDto.Source.RSS_NEWS)

    override suspend fun fetchLatestFeed(): List<RssItemDto> =
        fetchFeed(SchoolEndpoints.School.RSS_LATEST, RssItemDto.Source.RSS_LATEST)

    override suspend fun fetchArticleHtml(url: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).readCancellable { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Ogłoszenie: HTTP ${resp.code}")
            NewsArticleParser.parse(EncodingAwareBody.asDocument(resp))
                ?: throw pl.zse.bydgoszcz.elektron.domain.model.ArticleContentException()
        }
    }

    override suspend fun fetchArchivePage(page: Int): List<ArchiveItemDto> = withContext(Dispatchers.IO) {
        val url = SchoolEndpoints.School.homePage(page)
        client.newCall(Request.Builder().url(url).get().build()).readCancellable { resp ->
            // Błąd serwera to nie "koniec archiwum" - "Pokaż więcej" pokaże błąd i da się ponowić.
            if (!resp.isSuccessful) throw java.io.IOException("Archiwum ogłoszeń: HTTP ${resp.code} dla $url")
            NewsArchiveParser.parseList(EncodingAwareBody.asDocument(resp))
        }
    }

    override suspend fun fetchArticleDate(url: String): Instant? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).get().build()).readCancellable { resp ->
            if (!resp.isSuccessful) return@readCancellable null
            NewsArchiveParser.parseArticleDate(EncodingAwareBody.asDocument(resp))
        }
    }

    private suspend fun fetchFeed(url: String, source: RssItemDto.Source): List<RssItemDto> =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).readCancellable { resp ->
                // Błąd serwera to porażka kanału, nie "brak ogłoszeń" (dawniej liczony jako udany sync).
                if (!resp.isSuccessful) throw java.io.IOException("Ogłoszenia: HTTP ${resp.code} dla $url")
                val doc = EncodingAwareBody.asDocument(resp, xmlMode = true)
                if (doc.selectFirst("rss > channel") == null) {
                    throw pl.zse.bydgoszcz.elektron.domain.model.SchoolPageChangedException("ogłoszenia")
                }
                RssParser.parse(doc, source)
            }
        }

    companion object { private const val TAG = "ZseRssAnnouncementsSource" }
}
