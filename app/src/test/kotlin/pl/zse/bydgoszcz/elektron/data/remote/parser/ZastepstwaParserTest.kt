package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZastepstwaParserTest {

    @Test
    fun parsesTeacherSectionWithSubstitutions() {
        val html = """
            <table>
              <tr><td class="st0">Zastępstwa w dniu 25.09.2026 piątek</td></tr>
              <tr><td class="st1">Piotr Augustyn</td></tr>
              <tr>
                <td class="st4">lekcja</td>
                <td class="st5">opis</td>
                <td class="st5">zastępca</td>
                <td class="st6">uwagi</td>
              </tr>
              <tr>
                <td class="st7">0</td>
                <td class="st8">2 D(1) - 316</td>
                <td class="st8">M. Baranowska</td>
                <td class="st9">złączenie grup</td>
              </tr>
              <tr>
                <td class="st10">3</td>
                <td class="st11">2 B(3) - Uczniowie zwolnieni do domu</td>
                <td class="st11"></td>
                <td class="st12">III gr. złączenie grup</td>
              </tr>
              <tr><td class="st1">Maria Kowalska</td></tr>
              <tr>
                <td class="st7">5</td>
                <td class="st8">3 A - 218</td>
                <td class="st8">J. Nowak</td>
                <td class="st9"></td>
              </tr>
            </table>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val result = ZastepstwaParser.parse(doc)

        assertEquals(3, result.size)

        val first = result[0]
        assertEquals("25.09.2026", first.dateRaw)
        assertEquals("Piotr Augustyn", first.originalTeacher)
        assertEquals(0, first.lessonNumber)
        assertEquals("2D", first.classShortName)
        assertEquals(1, first.groupNumber)
        assertEquals("316", first.roomOrInfo)
        assertEquals("M. Baranowska", first.substituteTeacher)
        assertEquals("złączenie grup", first.notes)

        val second = result[1]
        assertEquals(3, second.lessonNumber)
        assertEquals("2B", second.classShortName)
        assertEquals(3, second.groupNumber)
        assertEquals("Uczniowie zwolnieni do domu", second.roomOrInfo)
        assertNull(second.substituteTeacher)

        val third = result[2]
        assertEquals("Maria Kowalska", third.originalTeacher)
        assertEquals(5, third.lessonNumber)
        assertEquals("3A", third.classShortName)
        assertNull(third.groupNumber)
        assertEquals("218", third.roomOrInfo)
    }
}
