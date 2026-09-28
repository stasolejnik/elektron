package pl.zse.bydgoszcz.elektron.data.remote.sources

import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto

/**
 * Źródło ogłoszeń (RSS 2.0, UTF-8). Implementacja: ZseRssAnnouncementsSource.
 */
interface AnnouncementsSource {
    suspend fun fetchNewsFeed(): List<RssItemDto>
    suspend fun fetchLatestFeed(): List<RssItemDto>
    suspend fun fetchArticleHtml(url: String): String?

    /** Strona [page] (od 1) archiwum aktualności ze strony głównej — starsze wpisy niż w RSS. */
    suspend fun fetchArchivePage(page: Int): List<ArchiveItemDto>

    /** Data publikacji artykułu (lista archiwum jej nie zawiera). */
    suspend fun fetchArticleDate(url: String): java.time.Instant?
}
