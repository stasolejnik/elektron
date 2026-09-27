package pl.zse.bydgoszcz.elektron.data.mapper

import android.util.Log
import pl.zse.bydgoszcz.elektron.data.local.AnnouncementEntity
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto
import pl.zse.bydgoszcz.elektron.data.remote.parser.HtmlSanitizer
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.AnnouncementSource
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object AnnouncementMapper {
    private const val TAG = "AnnouncementMapper"
    private val RFC822: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

    fun toEntity(dto: RssItemDto): AnnouncementEntity? {
        val published = parsePubDate(dto.pubDate)
        if (published == null) {
            Log.w(TAG, "Nie udało się sparsować pubDate: ${dto.pubDate} (guid=${dto.guid})")
            return null
        }
        val excerpt = HtmlSanitizer.firstParagraph(dto.description)
            ?: HtmlSanitizer.stripTags(dto.description)
        val cover = HtmlSanitizer.firstImageSrc(dto.description)?.let { normalizeImageUrl(it) }
        val source = when (dto.source) {
            RssItemDto.Source.RSS_NEWS -> AnnouncementSource.RSS_NEWS.name
            RssItemDto.Source.RSS_LATEST -> AnnouncementSource.RSS_LATEST.name
        }
        return AnnouncementEntity(
            id = dto.guid,
            title = dto.title,
            url = dto.link,
            publishedAtEpochSeconds = published.epochSecond,
            excerpt = excerpt,
            coverImageUrl = cover,
            fullHtml = null,
            isRead = false,
            source = source
        )
    }

    fun toDomain(e: AnnouncementEntity): Announcement = Announcement(
        id = e.id,
        title = e.title,
        url = e.url,
        publishedAt = Instant.ofEpochSecond(e.publishedAtEpochSeconds),
        excerpt = e.excerpt,
        coverImageUrl = e.coverImageUrl,
        fullHtml = e.fullHtml,
        source = runCatching { AnnouncementSource.valueOf(e.source) }
            .getOrDefault(AnnouncementSource.RSS_NEWS)
    )

    /** Zamienia http:// → https:// tylko dla hostów ZSE. */
    private fun normalizeImageUrl(url: String): String {
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("http://") &&
            (url.contains("zse.edu.bydgoszcz.pl") || url.contains("zse.bydgoszcz.pl"))
        ) {
            return url.replaceFirst("http://", "https://")
        }
        return url
    }

    private fun parsePubDate(raw: String): Instant? {
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw, RFC822).toInstant() }
            .recoverCatching { Instant.parse(raw) }
            .getOrNull()
    }
}
