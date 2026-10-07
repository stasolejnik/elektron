package pl.zse.bydgoszcz.elektron.presentation.announcements

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ArticleHtmlTest {
    private fun render(html: String) = Jsoup.parse(ArticleHtml.document(html, "https://zse.bydgoszcz.pl/news.html", "#EEEEEE", "#00BBDD"))

    @Test fun preservesEmphasisHeadingsListsAndInlineFontSize() {
        val doc = render("""<h2>Nagłówek</h2><p><strong>Pogrubienie</strong> <em>Kursywa</em>
            <span style="font-size:18pt;font-weight:700">Duży tekst</span></p><ol start="3"><li>Pozycja</li></ol>
            <font size="5">Starszy format</font>""")
        assertEquals("Nagłówek", doc.selectFirst("h2")!!.text())
        assertEquals("Pogrubienie", doc.selectFirst("strong")!!.text())
        assertEquals("Kursywa", doc.selectFirst("em")!!.text())
        assertTrue(doc.selectFirst("span")!!.attr("style").contains("font-size:18pt"))
        assertEquals("3", doc.selectFirst("ol")!!.attr("start"))
        assertEquals("5", doc.selectFirst("font")!!.attr("size"))
    }

    @Test fun removesEmptyParagraphsAndExcessiveSourceMarginsButKeepsLineBreaks() {
        val doc = render("<div><p>&nbsp;</p><p><br></p><p style='margin-bottom:100px;line-height:4'>Pierwsza<br>Druga</p></div>")
        assertEquals(1, doc.select("p").size)
        assertEquals(1, doc.select("br").size)
        assertFalse(doc.selectFirst("p")!!.hasAttr("style"))
        assertFalse(doc.body().html().contains("\n\n"))
    }

    @Test fun retainsOnlyExplicitWebLinksWithoutScriptsOrExternalResources() {
        val doc = render("""<script>alert(1)</script><iframe src="https://example.com"></iframe>
            <p onclick="alert(1)" style="background:url(https://example.com);color:red;font-size:18px">Tekst</p>
            <img src="https://example.com/pixel.png" alt="Plakat">
            <a href="javascript:alert(1)">Niebezpieczny</a><a href="/dokument.pdf">Dokument</a>""")
        assertTrue(doc.select("script, iframe, img, [onclick], [src]").isEmpty())
        assertFalse(doc.selectFirst("p")!!.attr("style").contains("url"))
        assertFalse(doc.selectFirst("a")!!.hasAttr("href"))
        assertEquals("https://zse.bydgoszcz.pl/dokument.pdf", doc.select("a")[1].attr("href"))
        assertTrue(doc.text().contains("Ilustracja: Plakat"))
    }

    @Test fun colorsComeFromAppThemeAndHtmlSourceNewlinesDoNotBecomeParagraphBreaks() {
        val doc = render("<p>Tekst\n z edytora\n strony.</p>")
        assertEquals("Tekst z edytora strony.", doc.body().text())
        assertTrue(doc.selectFirst("style")!!.data().contains("color:#EEEEEE"))
        assertTrue(doc.selectFirst("style")!!.data().contains("line-height:1.45"))
    }
    @Test fun galleryUsesOnlyDistinctSchoolImagesAndNormalizesHttps() {
        val images = ArticleHtml.imageLinks("""<img src="//zse.edu.bydgoszcz.pl/poster.jpg"><img src="http://zse.edu.bydgoszcz.pl/poster.jpg">
            <img src="/photo.png"><img src="https://tracker.example/pixel.gif"><img src="file:///private.jpg">""", "https://zse.bydgoszcz.pl/news.html")
        assertEquals(listOf("https://zse.edu.bydgoszcz.pl/poster.jpg", "https://zse.bydgoszcz.pl/photo.png"), images)
    }

    @Test fun galleryUsesFullSizeSchoolPosterInsteadOfThumbnail() {
        val images = ArticleHtml.imageLinks("""<a href="//zse.edu.bydgoszcz.pl/poster-d.jpg"><img src="//zse.edu.bydgoszcz.pl/poster-m.jpg"></a>
            <a href="https://tracker.example/full.jpg"><img src="/safe.png"></a>""", "https://zse.bydgoszcz.pl/news.html")
        assertEquals(listOf("https://zse.edu.bydgoszcz.pl/poster-d.jpg", "https://zse.bydgoszcz.pl/safe.png"), images)
    }

}
