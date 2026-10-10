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
        val cover = HtmlSanitizer.firstImageSrc(dto.description)?.let { schoolImageUrl(it) }
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
        fullHtml = ArticleContent.strip(e.fullHtml),
        isFavorite = e.isFavorite,
        source = runCatching { AnnouncementSource.valueOf(e.source) }
            .getOrDefault(AnnouncementSource.RSS_NEWS)
    )

    /** Zamienia http:// → https:// tylko dla hostów ZSE. */
    /**
     * Miniatura tylko z serwerów szkoły, zawsze przez https; adres spoza szkoły - brak miniatury.
     * Dawniej lista ogłoszeń pobierała obrazek z dowolnego serwera wskazanego w RSS (ujawniając mu
     * adres IP ucznia), wbrew ograniczeniu zdjęć do serwerów szkoły.
     */
    internal fun schoolImageUrl(url: String): String? {
        val absolute = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://zse.bydgoszcz.pl$url"
            else -> url
        }
        val uri = runCatching { java.net.URI(absolute) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (SCHOOL_HOSTS.none { host == it || host.endsWith(".$it") }) return null
        return absolute.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
    }

    private val SCHOOL_HOSTS = listOf("zse.bydgoszcz.pl", "zse.edu.bydgoszcz.pl")

    internal fun parsePubDate(raw: String): Instant? {
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw, RFC822).toInstant() }
            // RFC 822 dopuszcza też jednocyfrowy dzień i "GMT" - wzorzec powyżej ich nie czyta,
            // a ogłoszenie z nieczytelną datą było pomijane bez śladu.
            .recoverCatching { OffsetDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .recoverCatching { Instant.parse(raw) }
            .getOrNull()
    }
}
