package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Pojedyncza lekcja w planie. Substitution jest null w normalnym planie,
 * wypełniane przez use case, gdy dla (date, number, classId) istnieje zastępstwo.
 *
 * [substitutions] - WSZYSTKIE zastępstwa tej lekcji (np. osobne dla grupy 1 i grupy 2);
 * [substitution] to wybrane dla widocznych grup (LessonGroups.pickSubstitution).
 */
data class Lesson(
    val id: String,
    val classId: String,
    val className: String,
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val number: Int,
    val timeFrom: LocalTime,
    val timeTo: LocalTime,
    val groups: List<LessonGroup>,
    val note: String?,
    val substitution: Substitution?,
    val substitutions: List<Substitution> = listOfNotNull(substitution)
)
