package pl.zse.bydgoszcz.elektron.data.mapper

import android.util.Log
import pl.zse.bydgoszcz.elektron.data.local.LessonEntity
import pl.zse.bydgoszcz.elektron.data.local.LessonGroupEntity
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.domain.model.DayOfWeek
import pl.zse.bydgoszcz.elektron.domain.model.ClassNames
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroup
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import java.time.LocalDate
import java.time.LocalTime

object TimetableMapper {
    private const val TAG = "TimetableMapper"

    /** Godzina z planu ("08:00", parser zapisuje HH:mm); null, gdy nieczytelna. */
    private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw) }.getOrNull()

    /**
     * Konwersja DTO → Entity. Pomija komórki bez grup i bez notki
     * (puste wiersze po ostatniej lekcji danego dnia — VULCAN emituje
     * wiersze 0..12 niezależnie od tego, czy coś w nich jest).
     */
    fun toEntities(dto: TimetableDto, weekStartMonday: LocalDate): Pair<List<LessonEntity>, List<LessonGroupEntity>> {
        val lessons = mutableListOf<LessonEntity>()
        val groups = mutableListOf<LessonGroupEntity>()
        for (cell in dto.lessons) {
            if (cell.dayIndex !in 1..5) continue
            if (cell.groups.isEmpty() && cell.note == null) continue
            // Bez czytelnej godziny lekcja nie trafia do bazy: dawniej dostawała 00:00, co psuło
            // "po lekcjach" (dzień startowy planu), przypomnienia i "Trwa teraz".
            if (parseTime(cell.timeFrom) == null || parseTime(cell.timeTo) == null) {
                Log.w(TAG, "Pomijam lekcję ${dto.classId} dzień=${cell.dayIndex} nr=${cell.number}: nieczytelna godzina '${cell.timeFrom}'-'${cell.timeTo}'")
                continue
            }
            val date = weekStartMonday.plusDays((cell.dayIndex - 1).toLong())
            val lessonId = buildLessonId(dto.classId, date, cell.number)
            lessons += LessonEntity(
                id = lessonId,
                classId = dto.classId,
                className = ClassNames.cleanNonNull(dto.className),
                dateEpochDay = date.toEpochDay(),
                dayOfWeekIso = cell.dayIndex,
                number = cell.number,
                timeFrom = cell.timeFrom,
                timeTo = cell.timeTo,
                note = cell.note
            )
            cell.groups.forEachIndexed { i, g ->
                groups += LessonGroupEntity(
                    lessonId = lessonId,
                    sortOrder = i,
                    subject = g.subject,
                    teacherCode = g.teacherCode,
                    teacherUrl = g.teacherUrl,
                    teacherFullName = null,
                    room = g.room,
                    roomUrl = g.roomUrl,
                    groupLabel = g.groupLabel,
                    classRef = g.classRef
                )
            }
        }
        return lessons to groups
    }

    fun toDomain(
        lesson: LessonEntity,
        groups: List<LessonGroupEntity>,
        substitutions: List<Substitution>
    ): Lesson? {
        val date = LocalDate.ofEpochDay(lesson.dateEpochDay)
        val dow = DayOfWeek.fromIso(lesson.dayOfWeekIso) ?: DayOfWeek.PONIEDZIALEK
        // Lekcja zapisana przed tą poprawką z nieczytelną godziną - pomijana jak przy zapisie.
        val from = parseTime(lesson.timeFrom)
        val to = parseTime(lesson.timeTo)
        if (from == null || to == null) {
            Log.w(TAG, "Pomijam lekcję ${lesson.id}: nieczytelna godzina '${lesson.timeFrom}'-'${lesson.timeTo}'")
            return null
        }
        val classShort = ClassNames.shortName(lesson.className)
        // Wszystkie zastępstwa tej lekcji (osobne dla grup, np. "3 A(1)" i "3 A(2)"). Dawniej
        // brane było pierwsze z brzegu bez patrzenia na numer grupy.
        val candidates = substitutions.filter {
            it.date == date &&
                it.lessonNumber == lesson.number &&
                it.classShortName.equals(classShort, ignoreCase = true)
        }
        val lessonGroups = groups.sortedBy { it.sortOrder }.map { g ->
            LessonGroup(g.subject, g.teacherCode, g.teacherUrl, g.teacherFullName, g.room, g.roomUrl, g.groupLabel, g.classRef)
        }
        return Lesson(
            id = lesson.id,
            classId = lesson.classId,
            className = ClassNames.cleanNonNull(lesson.className),
            date = date,
            dayOfWeek = dow,
            number = lesson.number,
            timeFrom = from,
            timeTo = to,
            groups = lessonGroups,
            note = lesson.note,
            substitution = LessonGroups.pickSubstitution(candidates, lessonGroups),
            substitutions = candidates
        )
    }

    private fun buildLessonId(classId: String, date: LocalDate, number: Int): String =
        "$classId|${date.toEpochDay()}|$number"
}
