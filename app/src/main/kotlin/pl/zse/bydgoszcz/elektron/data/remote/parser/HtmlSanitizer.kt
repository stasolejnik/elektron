package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Entities

/**
 * Drobne narzędzia do czyszczenia HTML-a z RSS i strony szkoły.
 * RSS używa CDATA z HTML-em w <description> — wyciągamy z tego lead,
 * obrazek i czysty tekst. Encje (&quot;, &amp;) dekodujemy przez Jsoup.
 */
object HtmlSanitizer {
    fun stripTags(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return Jsoup.parse(html).text().trim().ifBlank { null }
    }
    fun unescape(escaped: String?): String? {
        if (escaped.isNullOrBlank()) return null
        return Entities.unescape(escaped)
    }
    fun firstParagraph(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return Jsoup.parse(html).selectFirst("p")?.text()?.trim()?.ifBlank { null }
    }
    fun firstImageSrc(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return Jsoup.parse(html).selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
    }
}
