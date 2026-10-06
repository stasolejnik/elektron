package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDateTime

object DashboardDepartures {
    /** Only a day with school lessons; freed lessons and other groups do not delay going home. */
    fun isTime(lessons: List<Lesson>, now: LocalDateTime): Boolean {
        val last = lessons.filter { it.date == now.toLocalDate() && SubstitutionDisplay.takesPlace(it) }
            .maxWithOrNull(compareBy<Lesson> { it.timeTo }.thenBy { it.timeFrom }) ?: return false
        return !now.toLocalTime().isBefore(last.timeFrom)
    }
}
