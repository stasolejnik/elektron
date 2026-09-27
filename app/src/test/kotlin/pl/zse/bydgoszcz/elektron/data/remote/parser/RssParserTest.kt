package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto

class RssParserTest {

    @Test
    fun parsesTwoItemsWithGuidAndCdata() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Aktualności</title>
                <item>
                  <guid isPermaLink="false">https://zse.bydgoszcz.pl/xyz-w1,10,117294.html</guid>
                  <pubDate>Tue, 22 Sep 2026 12:42:00 +0200</pubDate>
                  <title>Wyniki konkursu &amp;quot;elektroniku&amp;quot;</title>
                  <link>https://zse.bydgoszcz.pl/xyz-w1,10,117294.html</link>
                  <description><![CDATA[<img src="http://zse.edu.bydgoszcz.pl/assets/a.png"><p>Lead paragraph.</p>]]></description>
                </item>
                <item>
                  <guid isPermaLink="false">https://zse.bydgoszcz.pl/abc-w1,10,117300.html</guid>
                  <pubDate>Wed, 23 Sep 2026 08:00:00 +0200</pubDate>
                  <title>Bez CDATA</title>
                  <link>https://zse.bydgoszcz.pl/abc-w1,10,117300.html</link>
                  <description>plain text</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val items = RssParser.parse(doc, RssItemDto.Source.RSS_NEWS)

        assertEquals(2, items.size)
        val first = items[0]
        assertEquals("https://zse.bydgoszcz.pl/xyz-w1,10,117294.html", first.guid)
        assertEquals("Tue, 22 Sep 2026 12:42:00 +0200", first.pubDate)
        assertTrue("Encje powinny być zdekodowane", first.title.contains("\"elektroniku\""))
        assertNotNull(first.description)
        assertTrue(first.description!!.contains("<p>Lead paragraph.</p>"))
        assertTrue(first.description!!.contains("assets/a.png"))
        assertEquals(RssItemDto.Source.RSS_NEWS, first.source)
    }

    @Test
    fun emptyChannelReturnsEmptyList() {
        val xml = "<?xml version=\"1.0\"?><rss><channel></channel></rss>"
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val items = RssParser.parse(doc, RssItemDto.Source.RSS_LATEST)
        assertTrue(items.isEmpty())
    }
}
