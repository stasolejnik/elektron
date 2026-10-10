package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto

/**
 * Regresja na prawdziwej stronie zastępstw z 01.10.2026 (test/resources/zastepstwa).
 * Klasy CSS w tym eksporcie: wpisy st7, st11, st14, st4..., a st10 to PUSTY odstęp.
 * Stary parser (rozpoznawanie wierszy po "st7"/"st10") odczytał 1 wpis z 32.
 */
class RealSubstitutionsTest {

    private fun parse(): List<SubstitutionDto> {
        val stream = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html") ?: error("brak zasobu")
        return stream.use { ZastepstwaParser.parse(Jsoup.parse(it, "UTF-8", "https://zastepstwa.zse.bydgoszcz.pl/")) }
    }

    @Test
    fun allEntriesAreRead() {
        val subs = parse()
        assertEquals(32, subs.size)
        assertTrue(subs.all { it.dateRaw == "01.10.2026" })
        assertEquals(8, subs.map { it.originalTeacher }.distinct().size)
    }

    @Test
    fun class1DGroup2() {
        // Wpis, który wykrył bot Discorda, a aplikacja zgubiła.
        val s = parse().single { it.classShortName == "1D" }
        assertEquals("Izabela Kowalska", s.originalTeacher)
        assertEquals(7, s.lessonNumber)
        assertEquals(2, s.groupNumber)
        assertEquals("Zajęcia Świetlicowe", s.roomOrInfo)
        assertEquals(null, s.substituteTeacher)
    }

    @Test
    fun teacherContextSwitchesPerBlock() {
        val subs = parse()
        assertEquals("Vakat EduZdr.", subs.first().originalTeacher)
        assertEquals("Informatyka1 Vacat", subs.last().originalTeacher)
        assertEquals("Uczniowie zwolnieni do domu", subs.single { it.classShortName == "2F" }.roomOrInfo)
    }

    @Test
    fun pageDatesAreRead() {
        val stream = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html") ?: error("brak zasobu")
        val doc = stream.use { Jsoup.parse(it, "UTF-8", "https://zastepstwa.zse.bydgoszcz.pl/") }
        assertEquals(setOf("01.10.2026"), ZastepstwaParser.pageDates(doc))
    }

    @Test
    fun dayWithoutEntriesStillHasDate() {
        // Szkoła odwołała wszystkie zastępstwa dnia: nagłówek jest, wpisów nie ma.
        val doc = Jsoup.parse("<table><tr><td class=st0>Zastępstwa w dniu 05.10.2026 poniedziałek</td></tr></table>")
        assertEquals(setOf("05.10.2026"), ZastepstwaParser.pageDates(doc))
        assertTrue(ZastepstwaParser.parse(doc).isEmpty())
    }

    @Test
    fun completePageHasNoIncompleteDays() {
        val stream = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html") ?: error("brak zasobu")
        val doc = stream.use { Jsoup.parse(it, "UTF-8", "https://zastepstwa.zse.bydgoszcz.pl/") }
        assertTrue(ZastepstwaParser.parseDetailed(doc).incompleteDates.isEmpty())
    }

    @Test
    fun unreadableEntryMarksDayIncomplete() {
        // Celowo uszkodzony wpis (nietypowy zapis klasy, którego nie obejmuje DESC_RE).
        val html = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val broken = html.replace("1 D(2) - Zajęcia Świetlicowe", "1Dg2 - Zajęcia Świetlicowe")
        assertTrue(broken != html)
        val result = ZastepstwaParser.parseDetailed(Jsoup.parse(broken, "https://zastepstwa.zse.bydgoszcz.pl/"))
        assertEquals(31, result.items.size)
        assertEquals(setOf("01.10.2026"), result.incompleteDates)
    }
    @Test
    fun joinedLessonWithoutClassIsRecognizedAndDoesNotMarkTheDayIncomplete() {
        // 9.10.2026: "Wychowanie fizyczne - Zajęcia Świetlicowe" (zajęcia łączone, bez klasy) - dawniej
        // dzień "niekompletny" i stały błąd na stronie głównej.
        val html = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val joined = html.replace("1 D(2) - Zajęcia Świetlicowe", "Wychowanie fizyczne - Zajęcia Świetlicowe")
        assertTrue(joined != html)
        val result = ZastepstwaParser.parseDetailed(Jsoup.parse(joined, "https://zastepstwa.zse.bydgoszcz.pl/"))
        assertEquals(31, result.items.size)                       // wpis bez klasy osobno - id zgodne z watch.py
        assertTrue(result.incompleteDates.isEmpty())
        val joinedEntry = result.classless.single()
        assertEquals("", joinedEntry.classShortName)
        assertEquals("Wychowanie fizyczne", joinedEntry.subject)
        assertEquals("Zajęcia Świetlicowe", joinedEntry.roomOrInfo)
        assertEquals("01.10.2026", joinedEntry.dateRaw)
        // Zapis zaczynający się od cyfry (nieznana postać klasy) nadal oznacza dzień niekompletny.
        val digits = ZastepstwaParser.parseDetailed(Jsoup.parse(html.replace("1 D(2) - Zajęcia Świetlicowe", "1D,2A - wf"),
            "https://zastepstwa.zse.bydgoszcz.pl/"))
        assertEquals(setOf("01.10.2026"), digits.incompleteDates)
    }

    @Test fun wrongColumnCountAndMalformedLessonNumberMarkDayIncomplete() {
        val header = "<table><tr><td>Zastępstwa w dniu 01.10.2026</td></tr><tr><td>Nauczyciel</td></tr>"
        for (row in listOf("<tr><td>2</td></tr>", "<tr><td>2</td><td>1D - 105</td><td>Zastępca</td></tr>",
            "<tr><td>2a</td><td>1D - 105</td><td>Zastępca</td><td></td></tr>")) {
            val result = ZastepstwaParser.parseDetailed(Jsoup.parse(header + row + "</table>"))
            assertEquals(setOf("01.10.2026"), result.incompleteDates)
        }
    }


    @Test
    fun truncatedResponseIsRecognisedByMissingFooter() {
        val html = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html")!!.use { String(it.readBytes(), Charsets.UTF_8) }
        val whole = Jsoup.parse(html, "https://zastepstwa.zse.bydgoszcz.pl/")
        assertTrue(ZastepstwaParser.hasFooter(whole))
        // Odpowiedź urwana w połowie tabeli: dzień jest rozpoznany, ale bez stopki - nie może zastąpić zapisanych wpisów.
        val cut = Jsoup.parse(html.substring(0, html.length / 2), "https://zastepstwa.zse.bydgoszcz.pl/")
        assertTrue(ZastepstwaParser.pageDates(cut).isNotEmpty())
        org.junit.Assert.assertFalse(ZastepstwaParser.hasFooter(cut))
    }
}
