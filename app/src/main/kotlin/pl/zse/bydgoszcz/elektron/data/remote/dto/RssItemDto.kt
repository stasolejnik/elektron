package pl.zse.bydgoszcz.elektron.data.remote.dto

/** Jeden <item> z RSS 2.0 (rss.xml / rsslatest.xml). */
data class RssItemDto(
    val guid: String,
    val title: String,
    val link: String,
    val pubDate: String,
    val description: String?,
    val source: Source
) {
    enum class Source { RSS_NEWS, RSS_LATEST }
}
