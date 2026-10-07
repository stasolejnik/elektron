package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptivumTimetableParserTest {

    @Test
    fun parsesTwoGroupsInOneCell() {
        val html = """
            <html><head><title>Plan lekcji oddziału - 1A Ivy</title></head>
            <body>
            <table class="tabela">
              <tr>
                <th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th>
                <th>Środa</th><th>Czwartek</th><th>Piątek</th>
              </tr>
              <tr>
                <td class="nr">0</td>
                <td class="g"> 7:05- 7:50</td>
                <td class="l">&nbsp;</td>
                <td class="l">
                  <span style="font-size:85%">
                    <span class="p">wf-j1</span>
                    <a href="n12.html" class="n">Bo</a>
                    <a href="s65.html" class="s">Hala2</a>
                  </span><br>
                  <span class="p">wf</span>-j2
                  <a href="s67.html" class="s">Hala4</a>
                </td>
                <td class="l">&nbsp;</td>
                <td class="l">&nbsp;</td>
                <td class="l">&nbsp;</td>
              </tr>
            </table>
            <div>wygenerowano 19.09.2026 za pomocą programu Plan lekcji Optivum firmy VULCAN</div>
            <div>Obowiązuje od: 21.09.2026 r. - Oddziały 4E i 4J</div>
            </body></html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val dto = OptivumTimetableParser.parse(doc, "o1")

        assertEquals("o1", dto.classId)
        assertEquals("1A Ivy", dto.className)
        assertEquals("19.09.2026", dto.generatedAt)
        assertEquals("21.09.2026", dto.validFrom)

        assertEquals(5, dto.lessons.size) // 5 dni
        val tuesday = dto.lessons.first { it.dayIndex == 2 }
        assertEquals(0, tuesday.number)
        assertEquals("07:05", tuesday.timeFrom)
        assertEquals("07:50", tuesday.timeTo)
        assertEquals(2, tuesday.groups.size)

        val g1 = tuesday.groups[0]
        assertEquals("wf-j1", g1.subject)
        assertEquals("Bo", g1.teacherCode)
        assertEquals("n12.html", g1.teacherUrl)
        assertEquals("Hala2", g1.room)
        assertEquals("s65.html", g1.roomUrl)

        val g2 = tuesday.groups[1]
        assertEquals("Hala4", g2.room)
    }

    @Test
    fun emptyCellIsNotALesson() {
        val html = """
            <table class="tabela">
              <tr><th>Nr</th><th>Godz</th><th>Pon</th><th>Wt</th><th>Śr</th><th>Czw</th><th>Pt</th></tr>
              <tr>
                <td class="nr">0</td>
                <td class="g"> 7:05- 7:50</td>
                <td class="l">&nbsp;</td>
                <td class="l">&nbsp;</td>
                <td class="l">&nbsp;</td>
                <td class="l">&nbsp;</td>
                <td class="l">&nbsp;</td>
              </tr>
            </table>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val dto = OptivumTimetableParser.parse(doc, "o1")
        assertTrue("Puste komórki nie powinny być lekcjami", dto.lessons.all { it.groups.isEmpty() })
    }

    @Test
    fun unknownLayoutIsReported() {
        // Szkoła zmieniła układ strony (brak table.tabela) - parser nie może udawać,
        // że klasa po prostu nie ma lekcji.
        val doc = Jsoup.parse("<html><head><title>Plan lekcji oddziału - 1A</title></head>" +
            "<body><div class=\"nowy-plan\">Poniedziałek</div></body></html>")
        val dto = OptivumTimetableParser.parse(doc, "o1")
        assertTrue(!dto.layoutOk)
        assertTrue(dto.lessons.isEmpty())
    }

    @Test
    fun tableWithoutLessonRowsIsReported() {
        val doc = Jsoup.parse("<html><body><table class=\"tabela\"><tr><th>Nr</th></tr></table></body></html>")
        assertTrue(!OptivumTimetableParser.parse(doc, "o1").layoutOk)
    }

    /** Zrzut z planu 1F: "wf-j2 #1AF Hala4" - grupa WF łączona z 1A. */
    @Test
    fun jointGroupTagIsNotTheSubject() {
        val cell = """
            <span class="p">wf-j2</span> <span class="p">#1AF</span> <a href="s67.html" class="s">Hala4</a><br>
            <span class="p">#1AF</span> <span class="p">wf-j2</span> <a href="s67.html" class="s">Hala4</a><br>
            <span class="p">wf</span>-j2 #1AF <a href="s67.html" class="s">Hala4</a><br>
            <span class="p">wf-j1</span> <a href="n5.html" class="n">Kr</a> <a href="s65.html" class="s">Hala2</a>
        """.trimIndent()
        val html = """
            <html><head><title>Plan lekcji oddziału - 1F Woj</title></head><body>
            <table class="tabela">
              <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
              <tr><td class="nr">1</td><td class="g"> 8:00- 8:45</td>
                <td class="l">&nbsp;</td><td class="l">$cell</td>
                <td class="l">&nbsp;</td><td class="l">&nbsp;</td><td class="l">&nbsp;</td></tr>
            </table></body></html>
        """.trimIndent()
        val tuesday = OptivumTimetableParser.parse(Jsoup.parse(html), "o7").lessons.first { it.dayIndex == 2 }
        assertEquals(listOf("wf-j2", "wf-j2", "wf-j2", "wf-j1"), tuesday.groups.map { it.subject })
        assertEquals(listOf("#1AF", "#1AF", "#1AF", null), tuesday.groups.map { it.classRef })
        assertEquals("Hala4", tuesday.groups[0].room)
        assertEquals("Kr", tuesday.groups[3].teacherCode)
    }

    /** Komórki wpisane zwykłym tekstem (prawdziwe przykłady ze strony ZSE). */
    @Test
    fun plainTextCells() {
        val military = OptivumTimetableParser.parseTextCell("St WP1 zaj wojskowe")!!
        assertEquals("zaj wojskowe", military.subject)
        assertEquals("St", military.teacherCode)
        assertEquals("WP1", military.room)
        // Podwójna spacja jak na stronie ("Cd  WP1 zaj wojskowe").
        assertEquals("Cd", OptivumTimetableParser.parseTextCell("Cd  WP1 zaj wojskowe")!!.teacherCode)

        val uni = OptivumTimetableParser.parseTextCell("5B,5D zaj uni")!!
        assertEquals("zaj uni", uni.subject)
        assertEquals("5B,5D", uni.classRef)

        assertEquals(null, OptivumTimetableParser.parseTextCell("---"))
        assertEquals("Wycieczka klasowa", OptivumTimetableParser.parseTextCell("Wycieczka klasowa")!!.subject)
    }

    /** "@" w <span class="s"> = brak sali - nie może trafić do nazwy przedmiotu. */
    @Test
    fun noRoomMarkIsNotAppendedToSubject() {
        val html = """
            <html><head><title>Plan lekcji oddziału - 2F Woj</title></head><body>
            <table class="tabela">
              <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
              <tr><td class="nr">3</td><td class="g"> 9:50-10:35</td>
                <td class="l">&nbsp;</td><td class="l">&nbsp;</td>
                <td class="l"><span class="p">zaj.woj.</span> <a href="n19.html" class="n">Cd</a> <span class="s">@</span></td>
                <td class="l">&nbsp;</td><td class="l">&nbsp;</td></tr>
            </table></body></html>
        """.trimIndent()
        val g = OptivumTimetableParser.parse(Jsoup.parse(html), "o10").lessons.first { it.dayIndex == 3 }.groups.single()
        assertEquals("zaj.woj.", g.subject)
        assertEquals("Cd", g.teacherCode)
        assertEquals(null, g.room)
    }
    @Test fun partialTableIsRejectedEvenWhenAnotherRowIsValid() {
        val good = "<tr><td class='nr'>1</td><td class='g'>8:00-8:45</td>" +
            (1..5).joinToString("") { "<td class='l'><span class='p'>mat</span></td>" } + "</tr>"
        val broken = good.replace("8:00-8:45", "brak godziny")
        val dto = OptivumTimetableParser.parse(Jsoup.parse("<table class='tabela'>$good$broken</table>"), "o3")
        assertTrue(dto.lessons.isNotEmpty())
        assertTrue(!dto.layoutOk)
        assertTrue(!OptivumTimetableParser.parse(Jsoup.parse("<table class='tabela'>$good${good.replace("class='nr'>1", "class='nr'>x")}</table>"), "o3").layoutOk)
    }

}
