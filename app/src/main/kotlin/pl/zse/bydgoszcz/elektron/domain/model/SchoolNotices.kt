package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Informacja szkoły pod datą na stronie zastępstw: [text] (podział na linie zachowany, bez linii
 * publikacji), [images] - obrazki z serwerów szkoły (np. skrócony plan zajęć), [updated] - ostatnia
 * linia "Publikacja / Aktualizacja: …" (pokazywana małym podpisem, nie jako treść).
 */
data class SchoolNotice(val text: String, val images: List<String> = emptyList(), val updated: String? = null) {
    /** Jest co pokazać na karcie (treść albo obrazek); sama godzina aktualizacji - nie. */
    val hasContent: Boolean get() = text.isNotBlank() || images.isNotEmpty()
}

/** Informacje szkoły ze strony zastępstw (np. o oddawaniu krwi) - który dzień pokazać na stronie głównej. */
object SchoolNotices {
    /**
     * Powiadomienia: nowa albo zmieniona treść informacji na dziś i kolejne dni (sama godzina
     * aktualizacji ani same obrazki - nie). Najwyżej 3, od najbliższego dnia.
     */
    fun toNotify(before: Map<LocalDate, SchoolNotice>, after: Map<LocalDate, SchoolNotice>, today: LocalDate): List<Pair<LocalDate, SchoolNotice>> =
        after.filter { (day, notice) -> !day.isBefore(today) && notice.text.isNotBlank() &&
            before[day]?.text?.trim() != notice.text.trim() }
            .toList().sortedBy { it.first }.take(3)


    /**
     * Informacja dla dnia, który pokazuje strona główna: dziś, a po dzisiejszych lekcjach następny dzień
     * z lekcjami ([nextLessonDate], inaczej jutro). Dzień bez lekcji (weekend, dzień wolny): najpierw
     * dzisiejsza informacja, potem tego następnego dnia. [lessons] po filtrze grup; zwolnione się nie liczą.
     */
    fun forDashboard(
        notices: Map<LocalDate, SchoolNotice>,
        lessons: List<Lesson>,
        now: LocalDateTime,
        nextLessonDate: LocalDate?
    ): Pair<LocalDate, SchoolNotice>? {
        val today = now.toLocalDate()
        val held = lessons.filter { it.date == today && SubstitutionDisplay.takesPlace(it) }
        val next = nextLessonDate?.takeIf { it > today } ?: today.plusDays(1)
        val days = when {
            held.any { it.timeTo > now.toLocalTime() } -> listOf(today)
            held.isNotEmpty() -> listOf(next)
            else -> listOf(today, next)
        }
        return days.firstNotNullOfOrNull { d -> notices[d]?.takeIf { it.hasContent }?.let { d to it } }
    }
}
