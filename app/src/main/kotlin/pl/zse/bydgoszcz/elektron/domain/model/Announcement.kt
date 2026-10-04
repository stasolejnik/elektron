package pl.zse.bydgoszcz.elektron.domain.model

import java.time.Instant

/** Ogłoszenie z RSS albo strony szkoły. */
data class Announcement(
    val id: String,
    val title: String,
    val url: String,
    val publishedAt: Instant,
    val excerpt: String?,
    val coverImageUrl: String?,
    val fullHtml: String?,
    val source: AnnouncementSource,
    val isFavorite: Boolean = false
)

enum class AnnouncementSource { RSS_NEWS, RSS_LATEST, HOME_PAGE }
