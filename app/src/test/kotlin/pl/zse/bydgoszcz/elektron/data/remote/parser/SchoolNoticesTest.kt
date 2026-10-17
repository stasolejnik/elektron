package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Informacje szkoły pod datą na stronie zastępstw (prawdziwy eksport z 15.10.2026, ISO-8859-2). */
class SchoolNoticesTest {

    private fun real(): Document = javaClass.getResourceAsStream("/zastepstwa/2026-10-15_192535.html")!!
        .use { Jsoup.parse(it, "ISO-8859-2", "https://zastepstwa.zse.bydgoszcz.pl/") }

    @Test fun realPageNoticeKeepsLinesWithoutEdgeBlanks() {
        val notices = ZastepstwaParser.notices(real())
        assertEquals(setOf("16.10.2026"), notices.keys)
        assertEquals(
            "W Piątek w sali nr 5 zaistnieje możliwość Honorowego oddania krwi.\n" +
                "Możliwość oddawania krwi będzie pomiędzy godziną 8:00, a godziną 13:30.\n" +
                "Osoby, które oddadzą krew, otrzymają podpisane osobiście przez Dyrektora usprawiedliwienie z zajęć.",
            notices.getValue("16.10.2026"))
    }

    @Test fun realPageDateAndEntriesStillParse() {
        val doc = real()
        assertEquals(setOf("16.10.2026"), ZastepstwaParser.pageDates(doc))
        val result = ZastepstwaParser.parseDetailed(doc)
        assertTrue(result.items.isNotEmpty())
        assertTrue(result.items.all { it.dateRaw == "16.10.2026" })
        assertTrue(result.incompleteDates.isEmpty())
        assertEquals("Vakat EduZdr.", result.items.first().originalTeacher)
        assertTrue(ZastepstwaParser.hasFooter(doc))
    }

    @Test fun oldPageWithoutNoticeHasNone() {
        val doc = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html")!!
            .use { Jsoup.parse(it, "UTF-8", "https://zastepstwa.zse.bydgoszcz.pl/") }
        assertTrue(ZastepstwaParser.notices(doc).isEmpty())
        assertEquals(32, ZastepstwaParser.parse(doc).size)
    }

    @Test fun severalDaysEachWithOwnNoticeOrNoneAndDayWithoutEntries() {
        val doc = Jsoup.parse("""
            <table>
            <tr><td class=st3 colspan=4><nobr>Zastępstwa w dniu 19.10.2026 poniedziałek<br><br>Apel o 8:00<br><br><br>W auli.<br><br></nobr></td></tr>
            <tr><td class=st1 colspan=4>Jan Kowalski</td></tr>
            <tr><td>1</td><td>1 D - 105</td><td>A. Nowak</td><td>&nbsp;</td></tr>
            <tr><td class=st9 colspan=4>Zastępstwa w dniu 20.10.2026 wtorek</td></tr>
            <tr><td class=st1 colspan=4>Jan Kowalski</td></tr>
            <tr><td>2</td><td>2 A - 7</td><td>A. Nowak</td><td>&nbsp;</td></tr>
            <tr><td class=st0 colspan=4>Zastępstwa w dniu 21.10.2026 środa<BR><BR>Dzień bez zajęć dydaktycznych:Egzaminzawodowydlaklastrzecichiczwartych</td></tr>
            </table>""")
        val notices = ZastepstwaParser.notices(doc)
        assertEquals("Apel o 8:00\n\nW auli.", notices["19.10.2026"])
        assertEquals(null, notices["20.10.2026"])
        assertEquals("Dzień bez zajęć dydaktycznych:Egzaminzawodowydlaklastrzecichiczwartych", notices["21.10.2026"])
        assertEquals(setOf("19.10.2026", "20.10.2026", "21.10.2026"), ZastepstwaParser.pageDates(doc))
        val entries = ZastepstwaParser.parseDetailed(doc)
        assertEquals(listOf("19.10.2026", "20.10.2026"), entries.items.map { it.dateRaw })
        assertEquals("Jan Kowalski", entries.items.first().originalTeacher)
    }
}
