package pl.zse.bydgoszcz.elektron.data.remote.sources

import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto

/**
 * Źródło ogłoszeń (RSS 2.0, UTF-8). Implementacja: ZseRssAnnouncementsSource.
 */
interface AnnouncementsSource {
    suspend fun fetchNewsFeed(): List<RssItemDto>
    suspend fun fetchLatestFeed(): List<RssItemDto>
    suspend fun fetchArticleHtml(url: String): String?
}
