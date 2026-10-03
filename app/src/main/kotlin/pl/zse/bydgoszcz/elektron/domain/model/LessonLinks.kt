package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate

/** Lekcja do pokazania w planie: dzień i numer lekcji (np. z dotkniętego zastępstwa). */
data class LessonTarget(val date: LocalDate, val lessonNumber: Int)

/**
 * Link "otwórz szczegóły tej lekcji" (zakładka Zastępstwa, widżet Zastępstwa):
 * deep link "lesson/<epochDay>/<numer>". Widżet podaje go w extra [EXTRA_LESSON].
 * Dane z intencji są niezaufane (MainActivity jest eksportowana): numer 0..12, data najwyżej
 * rok od dziś - inaczej link jest ignorowany.
 */
object LessonLinks {
    /** Extra intencji z widżetu (rozpoznawane przez MainActivity). */
    const val EXTRA_LESSON = "elektron_lesson"
    private const val PREFIX = "lesson/"
    private const val MAX_DAYS_FROM_TODAY = 366L

    fun deepLink(target: LessonTarget): String = "$PREFIX${target.date.toEpochDay()}/${target.lessonNumber}"

    /** Deep link -> cel; null dla innych linków i błędnych danych. */
    fun parseDeepLink(link: String?, today: LocalDate): LessonTarget? {
        if (link == null || !link.startsWith(PREFIX)) return null
        return fromParts(link.removePrefix(PREFIX).split('/'), today)
    }

    fun isLessonDeepLink(link: String?): Boolean = link?.startsWith(PREFIX) == true

    private fun fromParts(parts: List<String>, today: LocalDate): LessonTarget? {
        if (parts.size != 2) return null
        val epochDay = parts[0].toLongOrNull() ?: return null
        val number = parts[1].toIntOrNull() ?: return null
        if (number !in 0..12) return null
        if (epochDay !in (today.toEpochDay() - MAX_DAYS_FROM_TODAY)..(today.toEpochDay() + MAX_DAYS_FROM_TODAY)) return null
        return LessonTarget(LocalDate.ofEpochDay(epochDay), number)
    }

    /**
     * Którą lekcję otworzyć: lekcja [target] w planie po filtrze grup użytkownika ([selections]).
     * null - lekcji nie ma (zastępstwo za lekcję innej grupy, zwolniona grupa, brak planu).
     */
    fun findLesson(lessons: List<Lesson>, target: LessonTarget, selections: Map<String, String>): Lesson? =
        LessonGroups.filter(lessons, selections).firstOrNull { it.date == target.date && it.number == target.lessonNumber }
}
