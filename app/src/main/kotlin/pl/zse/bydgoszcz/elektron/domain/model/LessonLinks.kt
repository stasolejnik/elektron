package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate

/** Lekcja do pokazania w planie: dzień i numer lekcji (np. z dotkniętego zastępstwa). */
data class LessonTarget(val date: LocalDate, val lessonNumber: Int)

/**
 * Link "otwórz szczegóły tej lekcji" (zakładka Zastępstwa, widżet Zastępstwa):
 * deep link "lesson/<epochDay>/<numer>". Widżet podaje cel dwoma kanałami: w extra [EXTRA_LESSON]
 * i w data URI intencji ("elektron://lesson/<epochDay>/<numer>", [uri]) - [resolveIntent].
 * Dane z intencji są niezaufane (MainActivity jest eksportowana): numer 0..12, data najwyżej
 * rok od dziś - inaczej link jest ignorowany.
 */
object LessonLinks {
    /** Extra intencji z widżetu (rozpoznawane przez MainActivity). */
    const val EXTRA_LESSON = "elektron_lesson"
    private const val PREFIX = "lesson/"
    private const val URI_PREFIX = "elektron://lesson/"
    private const val MAX_DAYS_FROM_TODAY = 366L

    fun deepLink(target: LessonTarget): String = "$PREFIX${target.date.toEpochDay()}/${target.lessonNumber}"

    /** Data URI intencji widżetu (inne dla każdej lekcji). */
    fun uri(target: LessonTarget): String = "$URI_PREFIX${target.date.toEpochDay()}/${target.lessonNumber}"

    /** Wynik rozstrzygnięcia intencji: cel (albo null), skąd i dlaczego - do logu diagnostycznego. */
    data class Resolution(val target: LessonTarget?, val source: String, val reason: String)

    /**
     * Cel lekcji z intencji: najpierw extra [extra] (deep link), potem data URI [data].
     * Błędne dane w jednym kanale nie blokują drugiego. Obie wartości są niezaufane.
     */
    fun resolveIntent(extra: String?, data: String?, today: LocalDate): Resolution {
        val fromExtra = parseDeepLink(extra, today)
        if (fromExtra != null) return Resolution(fromExtra, "extras", "ok")
        val fromData = data?.takeIf { it.startsWith(URI_PREFIX) }
            ?.let { fromParts(it.removePrefix(URI_PREFIX).split('/'), today) }
        if (fromData != null) return Resolution(fromData, "data", if (extra == null) "ok" else "błędne extras")
        val reason = when {
            extra == null && data == null -> "brak danych lekcji"
            extra != null && data != null -> "błędne extras i data"
            extra != null -> "błędne extras"
            else -> "błędne data"
        }
        return Resolution(null, "-", reason)
    }

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
