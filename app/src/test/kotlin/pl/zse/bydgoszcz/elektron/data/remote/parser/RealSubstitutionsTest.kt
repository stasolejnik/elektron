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
}
