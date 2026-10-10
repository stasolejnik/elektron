package pl.zse.bydgoszcz.elektron.presentation.announcements

import org.junit.Assert.*
import org.junit.Test
import pl.zse.bydgoszcz.elektron.presentation.announcements.ArticleBlocks.Images
import pl.zse.bydgoszcz.elektron.presentation.announcements.ArticleBlocks.Text

class ArticleBlocksTest {
    private val base = "https://zse.bydgoszcz.pl/aktualnosci/1-news.html"
    private fun parse(html: String) = ArticleBlocks.parse(html, base)
    private fun texts(html: String) = parse(html).filterIsInstance<Text>().map { it.plain }

    @Test fun paragraphsKeepTextAndEmphasis() {
        val blocks = parse("<p>Zwykły <strong>pogrubiony</strong> i <em>kursywa</em>.</p><p>Drugi akapit.</p>")
        assertEquals(listOf("Zwykły pogrubiony i kursywa.", "Drugi akapit."), blocks.map { (it as Text).plain })
        val spans = (blocks[0] as Text).spans
        assertTrue(spans.single { it.text == "pogrubiony" }.bold)
        assertTrue(spans.single { it.text == "kursywa" }.italic)
        assertFalse(spans.first().bold)
    }

    @Test fun emptyParagraphsAndRepeatedBreaksDoNotCreateGaps() {
        val blocks = parse("<p>&nbsp;</p><p><br></p><div>  </div><p>Pierwsza<br><br><br><br>Druga<br></p><p> </p>")
        assertEquals(listOf("Pierwsza\n\nDruga"), blocks.map { (it as Text).plain })
    }

    @Test fun sourceNewlinesAndSpacesCollapseLikeInBrowser() {
        assertEquals(listOf("Tekst z edytora strony."), texts("<p>Tekst\n   z edytora\n strony. </p>"))
        assertEquals(listOf("Jeden dwa"), texts("<p><b>Jeden </b> dwa</p>"))
    }

    @Test fun imagesStayInTheirPlaceInTheArticle() {
        val blocks = parse("""<p>Wstęp.</p><p><a href="/images/foto1-duze.jpg"><img src="/images/foto1.jpg" alt="Automat"></a></p>
            <p>Środek tekstu.</p><p><img src="https://zse.bydgoszcz.pl/images/foto2.jpg"></p><p><img src="http://zse.bydgoszcz.pl/images/foto3.jpg"></p>
            <p>Koniec.</p>""")
        assertEquals(listOf("Text", "Images", "Text", "Images", "Text"), blocks.map { it::class.simpleName })
        val first = (blocks[1] as Images).images.single()
        assertEquals("https://zse.bydgoszcz.pl/images/foto1.jpg", first.src)
        assertEquals("https://zse.bydgoszcz.pl/images/foto1-duze.jpg", first.full)
        assertEquals("Automat", first.alt)
        // Zdjęcia jedno pod drugim bez tekstu pomiędzy tworzą jedną galerię; http -> https.
        assertEquals(listOf("https://zse.bydgoszcz.pl/images/foto2.jpg", "https://zse.bydgoszcz.pl/images/foto3.jpg"),
            (blocks[3] as Images).images.map { it.src })
    }

    @Test fun inlineImageSplitsParagraphAtItsPosition() {
        val blocks = parse("<p>Przed zdjęciem <img src='/a.jpg'> po zdjęciu</p>")
        assertEquals(listOf("Text", "Images", "Text"), blocks.map { it::class.simpleName })
        assertEquals("Przed zdjęciem", (blocks[0] as Text).plain)
        assertEquals("po zdjęciu", (blocks[2] as Text).plain)
    }

    @Test fun foreignImagesAreNotLoadedAndDuplicatesAreSkipped() {
        val blocks = parse("""<img src="https://tracker.example/pixel.gif"><img src="/a.jpg"><img src="/a.jpg">
            <a href="https://tracker.example/full.jpg"><img src="/b.jpg"></a><img src="file:///private.jpg" alt="Plik">""")
        val images = ArticleBlocks.images(blocks)
        assertEquals(listOf("https://zse.bydgoszcz.pl/a.jpg", "https://zse.bydgoszcz.pl/b.jpg"), images.map { it.src })
        // Link spoza szkoły wokół zdjęcia nie staje się "pełną wersją".
        assertEquals("https://zse.bydgoszcz.pl/b.jpg", images[1].full)
        val placeholders = blocks.filterIsInstance<Text>().map { it.plain }
        assertTrue(placeholders.any { it.contains("[Ilustracja na stronie szkoły]") })
        assertTrue(placeholders.any { it.contains("[Ilustracja: Plik]") })
    }

    @Test fun listsKeepMarkersNumbersAndNesting() {
        val blocks = parse("<ol start='3'><li>Trzeci</li><li><p>Czwarty</p><ul><li>Podpunkt</li></ul></li></ol>")
            .filterIsInstance<Text>()
        assertEquals(listOf("Trzeci", "Czwarty", "Podpunkt"), blocks.map { it.plain })
        assertEquals(listOf("3.", "4.", "•"), blocks.map { it.marker })
        assertEquals(listOf(1, 1, 2), blocks.map { it.depth })
    }

    @Test fun headingsAlignmentAndFontSizeAreMapped() {
        val blocks = parse("""<h2>Nagłówek</h2><p style="text-align:center">Środek</p>
            <p><span style="font-size:24pt">Duży</span> <font size="1">mały</font></p>""").filterIsInstance<Text>()
        assertEquals(1, blocks[0].heading)
        assertEquals(ArticleBlocks.Align.CENTER, blocks[1].align)
        assertEquals(1.4f, blocks[2].spans.first().scale)
        assertEquals(.85f, blocks[2].spans.last().scale)
    }

    @Test fun onlyWebLinksAreKeptAndRelativeOnesAreResolved() {
        val spans = (parse("""<p><a href="javascript:alert(1)">Zły</a> <a href="/plik.pdf">Plik</a></p>""")[0] as Text).spans
        assertNull(spans.first { it.text.trim() == "Zły" }.link)
        assertEquals("https://zse.bydgoszcz.pl/plik.pdf", spans.first { it.text == "Plik" }.link)
    }

    @Test fun scriptsFormsAndEmbedsAreDropped() {
        assertEquals(listOf("Tekst"), texts("<script>alert(1)</script><style>p{}</style><iframe src='x'></iframe><p onclick='x'>Tekst</p><form><input value='a'></form>"))
    }

    @Test fun tablesKeepCellsButLayoutTablesBecomeParagraphsAndGalleries() {
        val data = parse("<table><tr><th>Klasa</th><th>Sala</th></tr><tr><td>2H</td><td>115</td></tr><tr><td> </td><td></td></tr></table>")
        val table = data.single() as ArticleBlocks.Table
        assertEquals(2, table.rows.size)
        assertEquals("Klasa", table.rows[0][0].joinToString("") { it.text })
        assertTrue(table.rows[0][0].first().bold)
        val layout = parse("<table><tr><td><p>Opis</p></td></tr><tr><td><img src='/x.jpg'><img src='/y.jpg'></td></tr></table>")
        assertEquals("Opis", (layout[0] as Text).plain)
        assertEquals(2, (layout[1] as Images).images.size)
    }

    @Test fun articleLikeTheSchoolPageKeepsEveryParagraph() {
        val paragraphs = (1..40).joinToString("") { "<p>Akapit numer $it z dłuższym tekstem ogłoszenia.</p>" }
        val blocks = parse("<div class='row'><div class='col-12 baguettebox'>$paragraphs<p><a href='/g/1.jpg'><img src='/g/1m.jpg'></a><a href='/g/2.jpg'><img src='/g/2m.jpg'></a></p></div></div>")
        assertEquals(41, blocks.size)
        assertEquals("Akapit numer 40 z dłuższym tekstem ogłoszenia.", (blocks[39] as Text).plain)
        assertEquals(listOf("https://zse.bydgoszcz.pl/g/1.jpg", "https://zse.bydgoszcz.pl/g/2.jpg"), (blocks[40] as Images).images.map { it.full })
    }

    @Test fun previousAndNextNavigationOfTheSchoolPageIsNotShown() {
        val blocks = parse("""<p>Treść.</p><p><img src="/a.jpg"></p>
            <div class="pager"><a href="/poprzedni.html"><img src="/img/strzalka-lewo.png"> Poprzedni</a></div>
            <p><a href="/nastepny.html" title="Następny artykuł"><img src="/img/arrow-right.png" alt="Następny"></a></p>
            <p><a href="/inny.html">« Poprzedni</a> | <a href="/kolejny.html">Następny »</a></p>
            <p><a href="/zapisy.html"><img src="/plakat.jpg" alt="Plakat zapisów"></a></p>""")
        assertEquals(listOf("https://zse.bydgoszcz.pl/a.jpg", "https://zse.bydgoszcz.pl/plakat.jpg"), ArticleBlocks.images(blocks).map { it.src })
        val text = blocks.filterIsInstance<Text>().joinToString(" ") { it.plain }
        assertFalse(text.contains("Poprzedni"))
        assertFalse(text.contains("Następny"))
        assertTrue(text.contains("Treść."))
        assertFalse("bez samotnego separatora", blocks.filterIsInstance<Text>().any { it.plain.trim() == "|" })
    }
}
