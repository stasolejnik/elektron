package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Ile zostało do końca lekcji dzisiaj (Ustawienia -> Strona główna, "Czas do końca lekcji"): strona
 * główna, widżet Plan dnia i powiadomienie na żywo liczą to tak samo. Lekcje po filtrze grup; lekcje,
 * z których klasa jest zwolniona, się nie liczą (jak w [LessonClock]). Minuty w górę.
 */
object SchoolDayEnd {

    /** [start] - początek pierwszej lekcji, [end] - koniec ostatniej, [progress] - część dnia za Tobą (0..1). */
    data class Info(val start: LocalTime, val end: LocalTime, val minutesLeft: Long, val progress: Float) {
        /** "2 h 10 min", "45 min". */
        val left: String get() = format(minutesLeft)
    }

    /** Dziś, dopóki ostatnia lekcja się nie skończyła; null - dzień bez lekcji albo po lekcjach. */
    fun of(lessons: List<Lesson>, now: LocalDateTime): Info? {
        val held = lessons.filter { it.date == now.toLocalDate() && SubstitutionDisplay.takesPlace(it) }
        if (held.isEmpty()) return null
        val start = held.minOf { it.timeFrom }
        val end = held.maxOf { it.timeTo }
        val time = now.toLocalTime()
        if (time >= end) return null
        return Info(start, end, LessonClock.minutesCeil(time, end), LessonClock.progress(start, end, time))
    }

    /** Czas w godzinach i minutach: "2 h 10 min", "2 h", "45 min". */
    fun format(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
