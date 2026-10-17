package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Informacje szkoły ze strony zastępstw (np. o oddawaniu krwi) - który dzień pokazać na stronie głównej. */
object SchoolNotices {

    /**
     * Informacja dla dnia, który pokazuje strona główna: dziś, a po dzisiejszych lekcjach następny dzień
     * z lekcjami ([nextLessonDate], inaczej jutro). Dzień bez lekcji (weekend, dzień wolny): najpierw
     * dzisiejsza informacja, potem tego następnego dnia. [lessons] po filtrze grup; zwolnione się nie liczą.
     */
    fun forDashboard(
        notices: Map<LocalDate, String>,
        lessons: List<Lesson>,
        now: LocalDateTime,
        nextLessonDate: LocalDate?
    ): Pair<LocalDate, String>? {
        val today = now.toLocalDate()
        val held = lessons.filter { it.date == today && SubstitutionDisplay.takesPlace(it) }
        val next = nextLessonDate?.takeIf { it > today } ?: today.plusDays(1)
        val days = when {
            held.any { it.timeTo > now.toLocalTime() } -> listOf(today)
            held.isNotEmpty() -> listOf(next)
            else -> listOf(today, next)
        }
        return days.firstNotNullOfOrNull { d -> notices[d]?.takeIf { it.isNotBlank() }?.let { d to it } }
    }
}
