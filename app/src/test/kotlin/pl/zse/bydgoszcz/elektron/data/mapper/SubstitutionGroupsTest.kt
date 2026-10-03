package pl.zse.bydgoszcz.elektron.data.mapper

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.local.LessonEntity
import pl.zse.bydgoszcz.elektron.data.local.LessonGroupEntity
import pl.zse.bydgoszcz.elektron.data.remote.parser.ZastepstwaParser
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import java.time.LocalDate

/**
 * Grupa 1 i grupa 2 mają zastępstwa na tej samej lekcji ("3 A(1) - 221" i "3 A(2) - 105").
 * Dawniej do lekcji trafiało pierwsze z brzegu zastępstwo, a filtr grup zdejmował je,
 * jeśli dotyczyło innej grupy - użytkownik z grupy 2 nie widział swojego zastępstwa.
 */
class SubstitutionGroupsTest {

    private val day = LocalDate.of(2026, 10, 5)

    private fun page(vararg rows: String) = Jsoup.parse(
        "<table><tr><td>Zastępstwa w dniu 05.10.2026 poniedziałek</td></tr>" +
            "<tr><td>techniczny Vacat tech</td></tr>" +
            "<tr><td>lekcja</td><td>opis</td><td>zastępca</td><td>uwagi</td></tr>" +
            rows.joinToString("") { "<tr><td>8</td><td>$it</td><td>M. Bzdawka</td><td>&nbsp;</td></tr>" } +
            "</table>"
    )

    private fun lesson(vararg rows: String): Lesson {
        val subs = ZastepstwaParser.parse(page(*rows)).mapNotNull(SubstitutionMapper::toEntity).map(SubstitutionMapper::toDomain)
        val entity = LessonEntity(
            id = "o5|${day.toEpochDay()}|8", classId = "o5", className = "3A technik informatyk",
            dateEpochDay = day.toEpochDay(), dayOfWeekIso = 1, number = 8,
            timeFrom = "14:20", timeTo = "15:05", note = null
        )
        val groups = listOf("inf-1/2", "inf-2/2").mapIndexed { i, subject ->
            LessonGroupEntity(entity.id, i, subject, "Bz", null, null, "22${i + 1}", null, null, null)
        }
        return TimetableMapper.toDomain(entity, groups, subs)!!
    }

    private fun roomFor(group: String, lesson: Lesson) =
        LessonGroups.filter(listOf(lesson), mapOf("inf" to group)).single().substitution?.roomOrInfo

    @Test
    fun eachGroupGetsItsOwnSubstitution() {
        val lesson = lesson("3 A(1) - 221", "3 A(2) - 105")
        assertEquals(2, lesson.substitutions.size)
        assertEquals("105", roomFor("2/2", lesson))
        assertEquals("221", roomFor("1/2", lesson))
    }

    @Test
    fun orderOnPageDoesNotMatter() {
        val lesson = lesson("3 A(2) - 105", "3 A(1) - 221")
        assertEquals("105", roomFor("2/2", lesson))
        assertEquals("221", roomFor("1/2", lesson))
    }

    @Test
    fun wholeLessonSubstitutionAppliesToEveryGroup() {
        // Wpis bez numeru grupy dotyczy całej lekcji.
        val lesson = lesson("3 A - Uczniowie zwolnieni do domu")
        assertEquals("Uczniowie zwolnieni do domu", roomFor("2/2", lesson))
        assertEquals("Uczniowie zwolnieni do domu", roomFor("1/2", lesson))
    }

    @Test
    fun groupSpecificWinsOverWholeLessonForThatGroup() {
        val lesson = lesson("3 A - 300", "3 A(2) - 105")
        assertEquals("105", roomFor("2/2", lesson))
        assertEquals("300", roomFor("1/2", lesson))
    }

    @Test
    fun withoutGroupSelectionSomeSubstitutionIsShown() {
        assertNotNull(lesson("3 A(1) - 221", "3 A(2) - 105").substitution)
    }
}
