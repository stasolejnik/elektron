package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto

class OptivumListaParserTest {

    @Test
    fun parsesClassesFromSidebarAndSanitizesDuplicates() {
        val html = """
            <html><body>
            <h4>Oddziały</h4>
            <ul>
              <li><a href="plany/o1.html" target="plan">1A 1A Ivy</a></li>
              <li><a href="plany/o7.html" target="plan">2I</a></li>
              <li><a href="plany/o3.html" target="plan">3A 3A Ivy</a></li>
            </ul>
            <h4>Nauczyciele</h4>
            <ul>
              <li><a href="plany/n1.html" target="plan">M.Chabowski (Ch)</a></li>
            </ul>
            <h4>Sale</h4>
            <ul>
              <li><a href="plany/s1.html" target="plan">1 TELE prac. sieci teleinf. - Kg, Ow</a></li>
            </ul>
            <a href="https://zastepstwa.zse.bydgoszcz.pl" target="plan">Zastepstwa</a>
            <a href="https://altplan.zse.bydgoszcz.pl" target="_blank">altVERplan</a>
            </body></html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val result = OptivumListaParser.parse(doc, "https://plan.zse.bydgoszcz.pl/")

        // Zewnętrzne linki (zastepstwa, altplan) NIE są pozycjami sidebaru.
        assertEquals(5, result.size)

        val classes = result.filter { it.kind == ClassListItemDto.Kind.CLASS }
        assertEquals(3, classes.size)
        // Kolejność z HTML, ale nazwy sanityzowane (bez duplikatów).
        assertEquals("1A Ivy", classes[0].displayName)
        assertEquals("1A", classes[0].shortName)
        assertEquals("o1", classes[0].id)
        assertEquals("https://plan.zse.bydgoszcz.pl/plany/o1.html", classes[0].url)

        assertEquals("2I", classes[1].displayName)
        assertEquals("2I", classes[1].shortName)

        assertEquals("3A Ivy", classes[2].displayName)
    }

    @Test
    fun parsesTeacherKindAndParsesTeacherParenthetical() {
        val html = """
            <a href="plany/n1.html">M.Chabowski (Ch)</a>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val result = OptivumListaParser.parse(doc, "https://plan.zse.bydgoszcz.pl/")
        assertEquals(1, result.size)
        val t = result[0]
        assertEquals(ClassListItemDto.Kind.TEACHER, t.kind)
        assertEquals("M.Chabowski (Ch)", t.displayName)
    }

    @Test
    fun missingLinksDoesNotCrash() {
        val html = "<html><body><p>No links here</p></body></html>"
        val doc = Jsoup.parse(html)
        val result = OptivumListaParser.parse(doc, "https://plan.zse.bydgoszcz.pl/")
        assertTrue(result.isEmpty())
    }
}
