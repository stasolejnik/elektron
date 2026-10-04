package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.nodes.Document

object NewsArticleParser {
    fun parse(doc: Document): String? {
        // Szkoła używa .main-content, nie article ani .content.
        val container = doc.selectFirst("article, .main-content, .content, main") ?: return null
        if (container.tagName() != "article" &&
            (container.selectFirst("h1, h2, h3") == null || NewsArchiveParser.parseArticleDate(doc) == null)) return null
        val article = container.clone()
        article.selectFirst("h1")?.remove()
        article.select(".page-subtitle, nav, footer, script, style, noscript").remove()
        return article.html().takeIf { article.text().isNotBlank() || article.selectFirst("img") != null }
    }
}
