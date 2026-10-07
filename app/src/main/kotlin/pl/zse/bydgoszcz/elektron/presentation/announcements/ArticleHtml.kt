package pl.zse.bydgoszcz.elektron.presentation.announcements

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

/** Samodzielny dokument zapisanej treści, bez kodu i zewnętrznych zasobów strony. */
object ArticleHtml {
    private val properties = setOf("font-size", "font-weight", "font-style", "text-decoration", "text-align", "vertical-align")

    fun document(fragment: String, baseUrl: String, textColor: String, linkColor: String): String {
        val doc = Jsoup.parseBodyFragment(fragment, baseUrl)
        doc.outputSettings().prettyPrint(false)
        doc.select("script, style, link, meta, base, iframe, object, embed, form, input, button, video, audio, svg, canvas").remove()
        doc.select("img").forEach { image ->
            val alt = image.attr("alt").trim()
            image.replaceWith(Element("span").text(if (alt.isEmpty()) "[Ilustracja dostępna na stronie szkoły]" else "[Ilustracja: $alt]"))
        }
        doc.select("*").forEach { element ->
            val style = element.attr("style").split(';').mapNotNull { declaration ->
                val parts = declaration.split(':', limit = 2)
                if (parts.size != 2) null else {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()
                    if (key in properties && value.matches(Regex("[a-zA-Z0-9 .%+\\-]+"))) "$key:$value" else null
                }
            }.joinToString(";")
            val href = element.absUrl("href").takeIf(::isWebLink)
            val fontSize = element.attr("size").toIntOrNull()?.takeIf { it in 1..7 }
            val start = element.attr("start").toIntOrNull()
            val attributes = element.attributes().map { it.key }
            attributes.forEach(element::removeAttr)
            if (style.isNotBlank()) element.attr("style", style)
            if (element.tagName() == "a" && href != null) element.attr("href", href)
            if (element.tagName() == "font" && fontSize != null) element.attr("size", fontSize.toString())
            if (element.tagName() == "ol" && start != null) element.attr("start", start.toString())
        }
        // Puste akapity z edytora szkoły nie mogą tworzyć kilku pustych wierszy.
        doc.select("p, div").toList().asReversed().forEach { element ->
            if (element.text().replace('\u00a0', ' ').isBlank() && element.children().all { it.tagName() == "br" }) element.remove()
        }
        val foreground = safeColor(textColor)
        val accent = safeColor(linkColor)
        return """<!doctype html><html lang="pl"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
            <style>
            html,body{margin:0;padding:0;background:transparent;color:$foreground}
            body{font-family:system-ui,sans-serif;font-size:16px;line-height:1.45;overflow-wrap:anywhere;padding:4px 0 12px}
            p{margin:0 0 .55em}div{margin:0}h1,h2,h3,h4,h5,h6{line-height:1.25;margin:.8em 0 .4em}
            h1{font-size:1.75em}h2{font-size:1.5em}h3{font-size:1.25em}
            ul,ol{margin:.4em 0 .65em;padding-left:1.5em}li{margin:.15em 0}
            blockquote{margin:.5em 0;padding-left:.8em;border-left:3px solid $accent}
            a{color:$accent}table{max-width:100%;border-collapse:collapse}td,th{padding:.2em .4em}
            pre{white-space:pre-wrap}hr{border:0;border-top:1px solid $foreground;opacity:.2}
            </style></head><body>${doc.body().html()}</body></html>""".trimIndent()
    }

    /** School-hosted pictures only; loaded by the gallery when the reader opens it. */
    fun imageLinks(fragment: String, baseUrl: String): List<String> =
        Jsoup.parseBodyFragment(fragment, baseUrl).select("img[src]").mapNotNull { image ->
            val linked = image.closest("a[href]")?.absUrl("href")
            val fullImage = linked?.takeIf { url -> runCatching {
                val uri = URI(url)
                uri.scheme in listOf("http", "https") && uri.host in setOf("zse.bydgoszcz.pl", "zse.edu.bydgoszcz.pl") &&
                    uri.path.orEmpty().matches(Regex(".*\\.(jpg|jpeg|png|gif|webp)", RegexOption.IGNORE_CASE))
            }.getOrDefault(false) }
            val url = fullImage ?: image.absUrl("src")
            runCatching {
                val uri = URI(url)
                if (uri.scheme !in listOf("http", "https") || uri.host !in setOf("zse.bydgoszcz.pl", "zse.edu.bydgoszcz.pl")) null
                else url.replaceFirst(Regex("^http://"), "https://")
            }.getOrNull()
        }.distinct().take(30)

    private fun safeColor(value: String) = value.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#222222"
    private fun isWebLink(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
