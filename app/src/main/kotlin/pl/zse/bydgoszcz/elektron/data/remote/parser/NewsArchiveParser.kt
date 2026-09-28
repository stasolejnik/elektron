package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Archiwum aktualności ze strony głównej szkoły: https://zse.bydgoszcz.pl/?menu=1&item=0&nnr=N
 * (76 stron po 5 wpisów — RSS zawiera tylko najnowsze wpisy).
 *
 * Fragment listy (wrzesień 2026, w uproszczeniu):
 *   <a href="https://zse.bydgoszcz.pl/kolo-algorytmiczno-programistyczne-w1,10,116887.html">
 *     <img src="https://zse.edu.bydgoszcz.pl/assets/zse/pages/images/img_00116887_01_001m.jpg"></a>
 *   <h3>KOŁO ALGORYTMICZNO-PROGRAMISTYCZNE</h3>
 *   <p>Wiktor z 3H proponuje uruchomienie koła ...</p>
 *   <a href="https://zse.bydgoszcz.pl/kolo-algorytmiczno-programistyczne-w1,10,116887.html">Czytaj więcej!</a>
 *   <hr>
 *
 * Parser NIE polega na klasach CSS (mogą się zmienić przy odświeżeniu szablonu strony), tylko
 * na strukturze: link "Czytaj więcej" do artykułu ("...-w1,<kategoria>,<numer>.html"), a obok
 * nagłówek, obrazek i opis. Lista nie zawiera dat — datę bierzemy ze strony artykułu
 * ([parseArticleDate]: "Opublikowano: 2026-09-11 09:54").
 */
object NewsArchiveParser {

    private val ARTICLE_URL = Regex("-w1,\\d+,\\d+\\.html")
    private const val HEADINGS = "h1, h2, h3, h4, h5"
    private val PUBLISHED = Regex("Opublikowano:\\s*(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})")
    private val PUBLISHED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm")
    private val SCHOOL_ZONE = ZoneId.of("Europe/Warsaw")

    fun parseList(doc: Document): List<ArchiveItemDto> {
        val out = LinkedHashMap<String, ArchiveItemDto>()
        for (link in doc.select("a[href]")) {
            if (!link.text().contains("Czytaj więcej", ignoreCase = true)) continue
            val url = absolute(link, "href") ?: continue
            if (!ARTICLE_URL.containsMatchIn(url) || url in out) continue
            val parts = itemParts(link) ?: continue
            val title = parts.heading.text().trim().takeIf { it.isNotEmpty() } ?: continue
            out[url] = ArchiveItemDto(url, title, parts.excerpt, parts.imageUrl)
        }
        return out.values.toList()
    }

    /** Data publikacji ze strony artykułu, np. "Opublikowano: 2026-09-11 09:54". */
    fun parseArticleDate(doc: Document): Instant? {
        val m = PUBLISHED.find(doc.text()) ?: return null
        return runCatching {
            LocalDateTime.parse("${m.groupValues[1]} ${m.groupValues[2]}", PUBLISHED_FMT)
                .atZone(SCHOOL_ZONE).toInstant()
        }.getOrNull()
    }

    private class Parts(val heading: Element, val imageUrl: String?, val excerpt: String?)

    private fun itemParts(link: Element): Parts? {
        // 1) Wpis we własnym kontenerze: najwyższy przodek linku zawierający dokładnie jeden nagłówek.
        var box: Element? = null
        for (anc in link.parents()) {
            val n = anc.select(HEADINGS).size
            if (n == 1) box = anc else if (n > 1) break
        }
        if (box != null) {
            val heading = box.selectFirst(HEADINGS) ?: return null
            val img = box.selectFirst("img")?.let { absolute(it, "src") }
            val excerpt = box.select("p").map { it.text().trim() }
                .filter { it.isNotEmpty() && !it.contains("Czytaj więcej", ignoreCase = true) }
                .joinToString(" ").ifBlank { null }
            return Parts(heading, img, excerpt)
        }
        // 2) Płaska struktura: wpisy jako rodzeństwo w jednym kontenerze, rozdzielone <hr>.
        //    Idziemy wstecz od linku do poprzedniego <hr> / poprzedniego "Czytaj więcej".
        var anchor: Element = link
        while (true) {
            val parent = anchor.parent() ?: break
            if (parent.select(HEADINGS).size > 1) break
            anchor = parent
        }
        var heading: Element? = null
        var img: String? = null
        val paragraphs = mutableListOf<String>()
        var node = anchor.previousElementSibling()
        while (node != null && node.tagName() != "hr" &&
            node.select("a").none { it.text().contains("Czytaj więcej", ignoreCase = true) }
        ) {
            if (heading == null && node.`is`(HEADINGS)) heading = node
            else if (heading == null) node.selectFirst(HEADINGS)?.let { heading = it }
            if (img == null) (if (node.tagName() == "img") node else node.selectFirst("img"))
                ?.let { img = absolute(it, "src") }
            if (node.tagName() == "p") paragraphs.add(0, node.text().trim())
            node = node.previousElementSibling()
        }
        val h = heading ?: return null
        return Parts(h, img, paragraphs.filter { it.isNotEmpty() }.joinToString(" ").ifBlank { null })
    }

    /** Pełny adres (obsługuje też adresy zaczynające się od "//"). */
    private fun absolute(el: Element, attr: String): String? {
        val abs = el.absUrl(attr)
        if (abs.isNotBlank()) return abs
        val raw = el.attr(attr).trim()
        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http") -> raw
            else -> null
        }
    }
}
