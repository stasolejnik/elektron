package pl.zse.bydgoszcz.elektron.presentation.announcements

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI

/**
 * Zapisana treść ogłoszenia jako bloki do natywnego widoku (bez WebView): akapity, nagłówki,
 * listy, cytaty, tabele i zdjęcia w tym miejscu, w którym są w poście. Z HTML-a strony bierzemy
 * tylko tekst, podstawowe wyróżnienia i bezpieczne linki - kolory, czcionki i odstępy są z aplikacji.
 * Puste akapity i wielokrotne <br> z edytora strony nie tworzą pustych przerw.
 */
internal object ArticleBlocks {

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val link: String? = null,
        /** Względna wielkość tekstu (1 = zwykła), ograniczona do 0.85..1.4. */
        val scale: Float = 1f
    )

    enum class Align { START, CENTER, END }

    sealed interface Block

    /** [heading] 0 - akapit, 1..3 - nagłówek; [marker] - "•" albo "3." w liście; [depth] - zagnieżdżenie listy/cytatu. */
    data class Text(
        val spans: List<Span>,
        val heading: Int = 0,
        val marker: String? = null,
        val depth: Int = 0,
        val quote: Boolean = false,
        val align: Align = Align.START
    ) : Block {
        val plain: String get() = spans.joinToString("") { it.text }
    }

    /** Zdjęcie ze strony szkoły: [src] do wyświetlenia, [full] - pełna wersja (link wokół miniatury). */
    data class Image(val src: String, val full: String, val alt: String?)

    /** Kolejne zdjęcia bez tekstu pomiędzy - w widoku jako jedno zdjęcie albo siatka. */
    data class Images(val images: List<Image>) : Block

    data class Table(val rows: List<List<List<Span>>>) : Block

    data object Rule : Block

    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "header", "footer", "main", "aside", "figure", "figcaption",
        "center", "pre", "address", "dl", "dt", "dd", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li",
        "blockquote", "table", "hr", "form", "fieldset"
    )
    private val SKIPPED = setOf(
        "script", "style", "link", "meta", "base", "iframe", "object", "embed", "form", "input", "button",
        "select", "textarea", "video", "audio", "svg", "canvas", "noscript", "template", "head", "title"
    )
    private val SCHOOL_HOSTS = setOf("zse.bydgoszcz.pl", "zse.edu.bydgoszcz.pl")
    private val IMAGE_PATH = Regex(".*\\.(jpg|jpeg|png|gif|webp)", RegexOption.IGNORE_CASE)
    private const val MAX_IMAGES = 40

    fun parse(fragment: String, baseUrl: String): List<Block> {
        val body = Jsoup.parseBodyFragment(fragment, baseUrl).body()
        val builder = Builder()
        builder.children(body, Style(), Context())
        builder.flush()
        builder.flushImages()
        return builder.blocks
    }

    private val NAV_CLASS = Regex("(^|[\\s_-])(pager|pagination|prev|next|previous|breadcrumbs?|share|social)([\\s_-]|$)", RegexOption.IGNORE_CASE)
    private val NAV_TEXT = Regex("^[«‹<←\\s]*(poprzedni|poprzednia|następny|następna|nastepny|nastepna|previous|next)\\b.*", RegexOption.IGNORE_CASE)
    private val NAV_IMAGE = Regex("(prev|next|arrow|strzalk|strzałk)[^/]*\\.(png|gif|svg|jpe?g|webp)$", RegexOption.IGNORE_CASE)

    /**
     * Nawigacja strony szkoły wokół artykułu ("Poprzedni / Następny", strzałki, udostępnianie):
     * odnośniki do innych stron, nie treść ogłoszenia - bez nich nie trafiają do widoku jako zdjęcia.
     */
    private fun isNavigation(element: Element, tag: String): Boolean {
        if (NAV_CLASS.containsMatchIn(element.className()) || NAV_CLASS.containsMatchIn(element.id())) return true
        if (tag == "img") return NAV_IMAGE.containsMatchIn(element.attr("src").substringBefore('?'))
        if (tag != "a") return false
        val label = listOf(element.text(), element.attr("title"), element.attr("rel")) +
            element.select("img").flatMap { listOf(it.attr("alt"), it.attr("title")) }
        return label.any { it.trim().length <= 40 && NAV_TEXT.matches(it.trim()) } || element.attr("rel").lowercase() in setOf("prev", "next")
    }

    /** Wszystkie zdjęcia w kolejności z posta (do przeglądarki zdjęć). */
    fun images(blocks: List<Block>): List<Image> = blocks.filterIsInstance<Images>().flatMap { it.images }

    private data class Style(
        val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false,
        val strike: Boolean = false, val link: String? = null, val scale: Float = 1f
    )

    private data class Context(
        val heading: Int = 0, val depth: Int = 0, val quote: Boolean = false,
        val align: Align = Align.START, val pre: Boolean = false
    )

    private class Builder {
        val blocks = mutableListOf<Block>()
        private val spans = mutableListOf<Span>()
        private var context = Context()
        private var marker: String? = null
        private val pendingImages = mutableListOf<Image>()
        private val seenImages = HashSet<String>()
        private var imageCount = 0

        fun children(element: Element, style: Style, context: Context) {
            element.childNodes().forEach { node(it, style, context) }
        }

        private fun node(node: Node, style: Style, context: Context) {
            when (node) {
                is TextNode -> text(node.wholeText, style, context)
                is Element -> element(node, style, context)
            }
        }

        private fun text(raw: String, style: Style, context: Context) {
            val value = if (context.pre) raw.replace('\u00a0', ' ') else raw.replace(Regex("[\\s\u00a0]+"), " ")
            if (value.isEmpty() || (value.isBlank() && spans.isEmpty())) return
            if (value.isNotBlank()) flushImages()
            if (spans.isEmpty()) this.context = context
            append(Span(value, style.bold, style.italic, style.underline, style.strike, style.link, style.scale))
        }

        private fun element(element: Element, parentStyle: Style, parentContext: Context) {
            val tag = element.tagName().lowercase()
            if (tag in SKIPPED || isNavigation(element, tag)) return
            when (tag) {
                "br" -> { if (spans.isNotEmpty()) append(Span("\n")); return }
                "img" -> { image(element, parentStyle, parentContext); return }
                "hr" -> { flush(); flushImages(); if (blocks.isNotEmpty() && blocks.last() != Rule) blocks += Rule; return }
                "table" -> { table(element, parentStyle); return }
            }
            val style = styleOf(element, tag, parentStyle)
            val context = contextOf(element, tag, parentContext)
            when {
                tag == "ul" || tag == "ol" -> {
                    flush()
                    var number = if (tag == "ol") element.attr("start").toIntOrNull()?.coerceIn(-9999, 9999) ?: 1 else 0
                    val inner = context.copy(depth = (parentContext.depth + 1).coerceAtMost(4))
                    element.childNodes().forEach { child ->
                        if (child is Element && child.tagName().equals("li", ignoreCase = true)) {
                            flush()
                            marker = if (tag == "ol") "${number++}." else "\u2022"
                            children(child, styleOf(child, "li", style), contextOf(child, "li", inner))
                            flush()
                            marker = null
                        } else node(child, style, inner)
                    }
                }
                tag == "blockquote" -> {
                    flush()
                    children(element, style, context.copy(quote = true, depth = (parentContext.depth + 1).coerceAtMost(4)))
                    flush()
                }
                tag in BLOCK_TAGS -> { flush(); children(element, style, context); flush() }
                else -> children(element, style, context)
            }
        }

        private fun image(element: Element, style: Style, context: Context) {
            val src = schoolImage(element.absUrl("src"))
            if (src == null) {
                // Zdjęcia spoza strony szkoły nie są pobierane - zostaje opis i link.
                val alt = element.attr("alt").trim()
                val target = style.link ?: element.absUrl("src").takeIf(::isWebLink)
                text(if (alt.isEmpty()) "[Ilustracja na stronie szkoły]" else "[Ilustracja: $alt]",
                    style.copy(italic = true, link = target), context)
                return
            }
            val full = style.link?.let(::schoolImage)?.takeIf { url ->
                runCatching { IMAGE_PATH.matches(URI(url).path.orEmpty()) }.getOrDefault(false)
            } ?: src
            addImage(Image(src, full, element.attr("alt").trim().ifEmpty { null }))
        }

        fun addImage(image: Image) {
            if (imageCount >= MAX_IMAGES || !seenImages.add(image.full)) return
            flush()
            imageCount++
            pendingImages += image
        }

        private fun table(element: Element, style: Style) {
            flush(); flushImages()
            val cellImages = mutableListOf<Image>()
            val rows = element.select("tr").filter { row -> row.closest("table") == element }.mapNotNull { row ->
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }.map { cell ->
                    val cellBuilder = Builder()
                    cellBuilder.children(cell, styleOf(cell, cell.tagName(), style), Context())
                    cellBuilder.flush()
                    cellBuilder.flushImages()
                    cellImages += images(cellBuilder.blocks)
                    // Komórka jako jeden tekst: akapity komórki rozdzielone nowymi wierszami.
                    cellBuilder.blocks.filterIsInstance<Text>().flatMapIndexed { index, block ->
                        if (index == 0) block.spans else listOf(Span("\n")) + block.spans
                    }
                }
                cells.takeIf { it.any { cell -> cell.any { span -> span.text.isNotBlank() } } }
            }
            // Tabela z jedną kolumną (często układ strony, nie dane) to zwykłe akapity.
            if (rows.isNotEmpty() && rows.all { it.size == 1 }) rows.forEach { blocks += Text(trim(it.single())) }
            else if (rows.isNotEmpty()) blocks += Table(rows.map { row -> row.map(::trim) })
            // Zdjęcia z tabeli (np. układ zdjęć w edytorze) - pod tabelą, jako galeria.
            cellImages.forEach(::addImage)
        }

        private fun append(span: Span) {
            val last = spans.lastOrNull()
            if (last != null && last.copy(text = "") == span.copy(text = "")) spans[spans.lastIndex] = last.copy(text = last.text + span.text)
            else spans += span
        }

        /** Zamyka bieżący akapit; znacznik listy zostaje do pierwszego niepustego akapitu punktu. */
        fun flush() {
            val trimmed = trim(spans)
            spans.clear()
            if (trimmed.isEmpty()) return
            // Sam separator po usuniętej nawigacji ("« Poprzedni | Następny »" -> "|").
            if (trimmed.joinToString("") { it.text }.all { it.isWhitespace() || it in "|/\\·•-–—»«" }) { if (marker == null) return }
            flushImages()
            blocks += Text(trimmed, context.heading, marker, context.depth, context.quote, context.align)
            marker = null
        }

        fun flushImages() {
            if (pendingImages.isEmpty()) return
            blocks += Images(pendingImages.toList())
            pendingImages.clear()
        }
    }

    /** Bez spacji i podziałów wierszy na brzegach bloku, najwyżej jeden pusty wiersz w środku. */
    private fun trim(list: List<Span>): List<Span> {
        val text = StringBuilder()
        val result = mutableListOf<Span>()
        list.forEach { span ->
            var value = span.text.replace(Regex(" *\n *"), "\n").replace(Regex("\n{3,}"), "\n\n")
            if (text.isEmpty()) value = value.trimStart(' ', '\n')
            if (text.endsWith(" ") || text.endsWith("\n")) value = value.trimStart(' ')
            if (text.endsWith("\n\n")) value = value.trimStart('\n')
            else if (text.endsWith("\n") && value.startsWith("\n\n")) value = value.substring(1)
            if (value.isNotEmpty()) { result += span.copy(text = value); text.append(value) }
        }
        while (result.isNotEmpty()) {
            val last = result.last()
            val value = last.text.trimEnd(' ', '\n')
            if (value.isEmpty()) result.removeAt(result.lastIndex)
            else { result[result.lastIndex] = last.copy(text = value); break }
        }
        return result.takeIf { spans -> spans.any { it.text.isNotBlank() } } ?: emptyList()
    }

    private fun styleOf(element: Element, tag: String, parent: Style): Style {
        val css = cssOf(element)
        val weight = css["font-weight"]
        val bold = when {
            tag in setOf("b", "strong", "th") -> true
            weight == "bold" || weight == "bolder" -> true
            weight?.toIntOrNull()?.let { it >= 600 } == true -> true
            weight == "normal" || weight?.toIntOrNull()?.let { it < 600 } == true -> false
            else -> parent.bold
        }
        val italic = when {
            tag in setOf("i", "em", "cite") -> true
            css["font-style"] == "italic" || css["font-style"] == "oblique" -> true
            css["font-style"] == "normal" -> false
            else -> parent.italic
        }
        val decoration = css["text-decoration"].orEmpty() + " " + css["text-decoration-line"].orEmpty()
        val underline = tag in setOf("u", "ins") || "underline" in decoration || parent.underline
        val strike = tag in setOf("s", "strike", "del") || "line-through" in decoration || parent.strike
        val link = if (tag == "a") element.absUrl("href").takeIf(::isWebLink) ?: parent.link else parent.link
        val scale = fontScale(css["font-size"], if (tag == "font") element.attr("size") else null) ?: when (tag) {
            "small" -> .85f
            "big" -> 1.15f
            else -> parent.scale
        }
        return Style(bold, italic, underline, strike, link, scale)
    }

    private fun contextOf(element: Element, tag: String, parent: Context): Context {
        val align = when (cssOf(element)["text-align"] ?: element.attr("align").lowercase().ifEmpty { null }) {
            "center" -> Align.CENTER
            "right", "end" -> Align.END
            "left", "start", "justify" -> Align.START
            else -> if (tag == "center") Align.CENTER else parent.align
        }
        val heading = when (tag) {
            "h1", "h2" -> 1
            "h3" -> 2
            "h4", "h5", "h6" -> 3
            else -> parent.heading
        }
        return parent.copy(align = align, heading = heading, pre = parent.pre || tag == "pre")
    }

    private fun cssOf(element: Element): Map<String, String> =
        element.attr("style").split(';').mapNotNull { declaration ->
            val parts = declaration.split(':', limit = 2)
            if (parts.size != 2) null else parts[0].trim().lowercase() to parts[1].trim().lowercase().removeSuffix("!important").trim()
        }.toMap()

    /** "18pt", "24px", "1.2em", "120%", "large" albo <font size="5"> - względem 16 px. */
    private fun fontScale(css: String?, fontSize: String?): Float? {
        val value = css?.let { size ->
            Regex("^([0-9]*\\.?[0-9]+)\\s*(px|pt|em|rem|%)$").find(size)?.destructured?.let { (number, unit) ->
                val n = number.toFloatOrNull() ?: return@let null
                when (unit) {
                    "px" -> n / 16f
                    "pt" -> n / 12f
                    "%" -> n / 100f
                    else -> n
                }
            } ?: when (size) {
                "x-small", "xx-small", "small", "smaller" -> .85f
                "medium" -> 1f
                "large", "larger" -> 1.15f
                "x-large", "xx-large", "xxx-large" -> 1.35f
                else -> null
            }
        } ?: fontSize?.trim()?.let { size ->
            when (size.toIntOrNull()) {
                1, 2 -> .85f
                3 -> 1f
                4 -> 1.15f
                5 -> 1.3f
                6, 7 -> 1.4f
                else -> null
            }
        }
        return value?.coerceIn(.85f, 1.4f)
    }

    /** Tylko zdjęcia ze strony szkoły, zawsze przez https. */
    private fun schoolImage(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host?.lowercase() !in SCHOOL_HOSTS) null
        else url.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
    }.getOrNull()

    private fun isWebLink(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
