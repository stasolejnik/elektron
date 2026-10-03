package pl.zse.bydgoszcz.elektron.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.local.LessonEntity
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonCellDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonGroupDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import java.time.LocalDate
import java.time.LocalTime

class TimetableMapperTest {

    private val monday = LocalDate.of(2026, 10, 5)

    private fun cell(number: Int, from: String, to: String, subject: String? = "mat") = LessonCellDto(
        number, from, to, 1, listOf(LessonGroupDto(subject, "Ch", null, "105", null, null, null)), null
    )

    private fun dto(vararg cells: LessonCellDto, className: String = "1D 1D PBŚ") =
        TimetableDto("o3", className, null, null, cells.toList())

    @Test
    fun skipsLessonWithUnreadableTime() {
        // Dawniej lekcja bez godziny dostawała 00:00 (psuło "po lekcjach" i przypomnienia).
        val (lessons, groups) = TimetableMapper.toEntities(
            dto(cell(1, "08:00", "08:45"), cell(2, "", ""), cell(3, "25:00", "25:45"), cell(4, "10:40", "1x:25")),
            monday
        )
        assertEquals(listOf(1), lessons.map { it.number })
        assertEquals(listOf(lessons.single().id), groups.map { it.lessonId })
    }

    @Test
    fun storedLessonWithUnreadableTimeIsNotShown() {
        val entity = LessonEntity("o3|1|2", "o3", "1D", monday.toEpochDay(), 1, 2, "", "08:45", null)
        assertNull(TimetableMapper.toDomain(entity, emptyList(), emptyList()))
        val ok = TimetableMapper.toDomain(entity.copy(timeFrom = "08:00"), emptyList(), emptyList())
        assertEquals(LocalTime.of(8, 0), ok?.timeFrom)
    }

    @Test
    fun emptyAndLongNames() {
        val long = "x".repeat(500)
        val (lessons, groups) = TimetableMapper.toEntities(dto(cell(1, "08:00", "08:45", subject = long), className = ""), monday)
        assertEquals(1, lessons.size)
        assertEquals(long, groups.single().subject)
        val domain = TimetableMapper.toDomain(lessons.single(), groups, emptyList())
        assertEquals("", domain?.className)
        assertEquals(long, domain?.groups?.single()?.subject)
        // Grupa bez przedmiotu, nauczyciela i sali nie wywraca mapowania.
        val (l2, g2) = TimetableMapper.toEntities(
            dto(LessonCellDto(1, "08:00", "08:45", 1, listOf(LessonGroupDto(null, null, null, null, null, null, null)), null)),
            monday
        )
        assertEquals(null, TimetableMapper.toDomain(l2.single(), g2, emptyList())?.groups?.single()?.subject)
    }

    @Test
    fun substitutionMatchedByClassShortName() {
        val sub = Substitution("s", monday, 1, "1D", null, "105", "X", null, "Y")
        val other = sub.copy(id = "s2", classShortName = "1E")
        val (lessons, groups) = TimetableMapper.toEntities(dto(cell(1, "08:00", "08:45")), monday)
        val lesson = TimetableMapper.toDomain(lessons.single(), groups, listOf(sub, other))
        assertEquals(listOf("s"), lesson?.substitutions?.map { it.id })
    }
}
