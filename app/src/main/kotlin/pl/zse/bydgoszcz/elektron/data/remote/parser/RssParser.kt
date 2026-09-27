package pl.zse.bydgoszcz.elektron.data.remote.parser

import android.util.Log
import org.jsoup.nodes.Document
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto

/**
 * Parser RSS 2.0 z zse.bydgoszcz.pl (UTF-8). Parsujemy Jsoup w trybie XML:
 *   Jsoup.parse(xml, "", Parser.xmlParser())
 *
 * Przykład <item>:
 *   <item>
 *     <guid isPermaLink="false">https://zse.bydgoszcz.pl/...html</guid>
 *     <pubDate>Tue, 22 Sep 2026 12:42:00 +0200</pubDate>
 *     <title>Wyniki konkursu &quot;elektroniku&quot;</title>
 *     <link>https://zse.bydgoszcz.pl/...-w1,10,117294.html</link>
 *     <description><![CDATA[
 *       <img style="width: 100%;" src="http://zse.edu.bydgoszcz.pl/assets/..." alt="">
 *       <p>Lead paragraph.</p>
 *     ]]></description>
 *   </item>
 *
 * Uwaga: pole <guid> ma isPermaLink="false", więc traktujemy je jako
 * unikalny string identyfikujący item, NIE jako URL do otwarcia.
 */
object RssParser {
    private const val TAG = "RssParser"

    fun parse(doc: Document, source: RssItemDto.Source): List<RssItemDto> {
        val items = doc.select("item")
        if (items.isEmpty()) {
            Log.w(TAG, "Brak <item> w RSS (source=$source)")
            return emptyList()
        }
        val out = mutableListOf<RssItemDto>()
        var warnings = 0
        for (item in items) {
            val guid = item.selectFirst("guid")?.text()?.trim()?.takeIf { it.isNotEmpty() }
                ?: item.selectFirst("link")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            if (guid == null) {
                Log.w(TAG, "Item bez guid ani link — pomijam (source=$source)")
                warnings++
                continue
            }
            val rawTitle = item.selectFirst("title")?.text()?.trim().orEmpty()
            val title = HtmlSanitizer.unescape(rawTitle).orEmpty()
            val link = item.selectFirst("link")?.text()?.trim().orEmpty()
            val pubDate = item.selectFirst("pubDate")?.text()?.trim().orEmpty()
            val description = item.selectFirst("description")?.let { el ->
                el.data().ifBlank { el.html() }.ifBlank { el.text() }
            }?.takeIf { it.isNotBlank() }

            if (title.isEmpty()) warnings++
            if (link.isEmpty()) warnings++

            out += RssItemDto(
                guid = guid,
                title = title,
                link = link,
                pubDate = pubDate,
                description = description,
                source = source
            )
        }
        Log.i(TAG, "Sparsowano ${out.size} itemów RSS (source=$source, ostrzeżeń=$warnings)")
        return out
    }
}
