package pl.zse.bydgoszcz.elektron.presentation.timetable

import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionDisplay
import java.time.LocalDate
import java.time.LocalDateTime

internal object WeekOpening {
    /** Inny tydzień niż startowy i bieżący: bez wybranego dnia, siatka od góry. */
    fun selectedDay(days: List<LocalDate>, preferred: LocalDate?, today: LocalDate): LocalDate? =
        preferred?.takeIf { it in days } ?: today.takeIf { it in days }

    fun startMinute(day: LocalDate?, lessons: List<Lesson>, now: LocalDateTime): Int? {
        val actual = lessons.filter { it.date == day && SubstitutionDisplay.takesPlace(it) }
        if (day == now.toLocalDate() && actual.any { it.timeTo > now.toLocalTime() }) return now.toLocalTime().toSecondOfDay() / 60
        return actual.minOfOrNull { it.timeFrom.toSecondOfDay() / 60 }
    }
}
