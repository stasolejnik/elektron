package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto

/**
 * Regresja na prawdziwych planach ZSE (plan.zse.bydgoszcz.pl, 30.09.2026) z test/resources/plany:
 * 1F (o4) - zajęcia wojskowe zapisane tekstem, sala "@", WF łączony "#1AF",
 * 5B (o26) - "5B,5D zaj uni", 5J (o31) - puste komórki "---".
 */
class RealPlansTest {

    private fun plan(id: String): TimetableDto {
        val stream = javaClass.getResourceAsStream("/plany/$id.html") ?: error("brak /plany/$id.html")
        return stream.use { OptivumTimetableParser.parse(Jsoup.parse(it, "UTF-8", "https://plan.zse.bydgoszcz.pl/plany/"), id) }
    }

    private fun TimetableDto.subjects() = lessons.flatMap { it.groups }.mapNotNull { it.subject }

    @Test
    fun class1F() {
        val p = plan("o4")
        assertTrue(p.layoutOk)
        assertEquals(45, p.lessons.count { it.groups.isNotEmpty() })
        // Środa, lekcje 0-2: zajęcia wojskowe wpisane tekstem ("St WP1 zaj wojskowe").
        val wednesday = p.lessons.filter { it.dayIndex == 3 && it.number in 0..2 }.map { it.groups.single() }
        assertEquals(List(3) { "zaj wojskowe" }, wednesday.map { it.subject })
        assertTrue(wednesday.all { it.teacherCode == "St" && it.room == "WP1" })
        // WF łączony z 1A: przedmiot wf-j2, znacznik w classRef.
        val joint = p.lessons.flatMap { it.groups }.filter { it.classRef == "#1AF" }
        assertTrue(joint.isNotEmpty() && joint.all { it.subject == "wf-j2" })
    }

    @Test
    fun truncatedResponseIsNotAcceptedAsAFullPlan() {
        val html = javaClass.getResourceAsStream("/plany/o4.html")!!.use { String(it.readBytes(), Charsets.UTF_8) }
        // Zerwane połączenie po kilku wierszach tabeli: Jsoup domyka tabelę, wiersze wyglądają poprawnie.
        val rows = Regex("<tr").findAll(html).map { it.range.first }.toList()
        val cut = html.substring(0, rows[rows.size / 2])
        val p = OptivumTimetableParser.parse(Jsoup.parse(cut, "https://plan.zse.bydgoszcz.pl/plany/"), "o4")
        assertTrue(p.lessons.isNotEmpty())
        assertTrue("ucięty plan nie może zastąpić zapisanego", !p.layoutOk)
    }

    @Test
    fun class5B() {
        val uni = plan("o26").lessons.flatMap { it.groups }.filter { it.subject == "zaj uni" }
        assertEquals(2, uni.size)
        assertTrue(uni.all { it.classRef == "5B,5D" })
    }

    @Test
    fun noParsingArtifactsInAnyPlan() {
        for (id in listOf("o4", "o26", "o31")) {
            val subjects = plan(id).subjects()
            assertTrue("$id: ${subjects.filter { '@' in it || it.startsWith("#") || it.all { c -> c == '-' } }}",
                subjects.none { '@' in it || it.startsWith("#") || it.all { c -> c == '-' } })
        }
    }
}
