package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Jednolity zapis dat w interfejsie: "03.11", "03.11.2026", "dziś, 12:49", "jutro, 07:50". */
object DateTexts {
    private val PL = Locale("pl", "PL")
    val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
    val FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val FULL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    /** Minione chwile ("Sprawdzono …"): dziś sama godzina, potem "wczoraj, 18:05", starsze "03.11, 12:49". */
    fun past(at: LocalDateTime, today: LocalDate): String = when (at.toLocalDate()) {
        today -> at.format(TIME)
        today.minusDays(1) -> "wczoraj, " + at.format(TIME)
        else -> at.format(DAY_MONTH) + ", " + at.format(TIME)
    }

    /** Przyszłe chwile ("Najbliższe: …"): "dziś, 07:50", "jutro, 07:50", w tym tygodniu "czwartek, 07:50", dalej "12.11, 07:50". */
    fun upcoming(at: LocalDateTime, today: LocalDate): String {
        val date = at.toLocalDate()
        val day = when {
            date == today -> "dziś"
            date == today.plusDays(1) -> "jutro"
            date.isAfter(today) && date.isBefore(today.plusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.FULL, PL)
            else -> date.format(DAY_MONTH)
        }
        return "$day, " + at.format(TIME)
    }
}
