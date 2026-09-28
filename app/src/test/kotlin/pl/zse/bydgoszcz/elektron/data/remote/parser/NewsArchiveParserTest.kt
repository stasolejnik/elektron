package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.ClassNames
import java.time.ZoneId

class NewsArchiveParserTest {

    private val base = "https://zse.bydgoszcz.pl/"

    @Test
    fun parsesItemsInOwnContainers() {
        val html = """
            <div class="news">
              <a href="https://zse.bydgoszcz.pl/kolo-algorytmiczno-programistyczne-w1,10,116887.html">
                <img src="//zse.edu.bydgoszcz.pl/assets/zse/pages/images/img_00116887_01_001m.jpg"></a>
              <h3>KOŁO ALGORYTMICZNO-PROGRAMISTYCZNE</h3>
              <p>Wiktor z 3H proponuje uruchomienie koła.</p>
              <a href="https://zse.bydgoszcz.pl/kolo-algorytmiczno-programistyczne-w1,10,116887.html">Czytaj więcej!</a>
            </div>
            <div class="news">
              <h3>Bydgoska Rodzina 3+</h3>
              <p>W ramach programu można wypożyczyć podręczniki.</p>
              <a href="/bydgoska-rodzina-3-w1,600,101336.html">Czytaj więcej!</a>
            </div>
            <aside><a href="https://zse.bydgoszcz.pl/drzwi-otwarte-2026-w1,100,64588.html">Drzwi otwarte 2026</a></aside>
        """.trimIndent()
        val items = NewsArchiveParser.parseList(Jsoup.parse(html, base))
        assertEquals(2, items.size) // link z paska "Ważne" (bez "Czytaj więcej") pominięty
        assertEquals("KOŁO ALGORYTMICZNO-PROGRAMISTYCZNE", items[0].title)
        assertEquals("https://zse.edu.bydgoszcz.pl/assets/zse/pages/images/img_00116887_01_001m.jpg", items[0].imageUrl)
        assertEquals("Wiktor z 3H proponuje uruchomienie koła.", items[0].excerpt)
        assertEquals("https://zse.bydgoszcz.pl/bydgoska-rodzina-3-w1,600,101336.html", items[1].url)
        assertNull(items[1].imageUrl)
    }

    @Test
    fun parsesFlatSiblingItems() {
        val html = """
            <div id="content">
              <a href="https://zse.bydgoszcz.pl/a-w1,10,1.html"><img src="https://x/1.jpg"></a>
              <h3>Pierwszy</h3><p>Opis 1</p>
              <a href="https://zse.bydgoszcz.pl/a-w1,10,1.html">Czytaj więcej!</a><hr>
              <a href="https://zse.bydgoszcz.pl/b-w1,10,2.html"><img src="https://x/2.jpg"></a>
              <h3>Drugi</h3><p>Opis 2</p>
              <a href="https://zse.bydgoszcz.pl/b-w1,10,2.html">Czytaj więcej!</a><hr>
            </div>
        """.trimIndent()
        val items = NewsArchiveParser.parseList(Jsoup.parse(html, base))
        assertEquals(listOf("Pierwszy", "Drugi"), items.map { it.title })
        assertEquals(listOf("Opis 1", "Opis 2"), items.map { it.excerpt })
        assertEquals(listOf("https://x/1.jpg", "https://x/2.jpg"), items.map { it.imageUrl })
    }

    @Test
    fun parsesArticleDate() {
        val doc = Jsoup.parse("<h1>X</h1><p>Opublikowano: 2026-09-11 09:54, Numer artykułu: 116887</p>")
        val instant = NewsArchiveParser.parseArticleDate(doc)
        assertNotNull(instant)
        val local = instant!!.atZone(ZoneId.of("Europe/Warsaw")).toLocalDateTime()
        assertEquals("2026-09-11T09:54", local.toString())
    }

    @Test
    fun classNamesRemoveDuplicates() {
        assertEquals("1D", ClassNames.clean("1D 1D"))
        assertEquals("1D technik", ClassNames.clean("1D 1D technik"))
        assertEquals("3H", ClassNames.clean("3H"))
    }
}
