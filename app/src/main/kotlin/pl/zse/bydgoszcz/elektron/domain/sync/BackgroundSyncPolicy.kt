package pl.zse.bydgoszcz.elektron.domain.sync

import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome
import java.time.LocalTime

object BackgroundSyncPolicy {
    /** Noc: szkoła nie publikuje wtedy zastępstw ani ogłoszeń - sprawdzanie co 15 min tylko budziło radio. */
    val NIGHT_FROM: LocalTime = LocalTime.of(23, 0)
    val NIGHT_TO: LocalTime = LocalTime.of(5, 0)
    /** Krócej niż próg "Sprawdzono: …" widżetu (3 h): przy cyklu co 15 min napis nie pojawia się w nocy. */
    const val NIGHT_INTERVAL = 150 * 60L

    fun isNight(time: LocalTime): Boolean = time >= NIGHT_FROM || time < NIGHT_TO

    /** [time] - lokalna godzina przebiegu (null - reguła nocna wyłączona). */
    fun isDue(source: String, lastSuccess: Long?, now: Long, time: LocalTime? = null): Boolean {
        val night = time != null && isNight(time)
        val interval = when (source) {
            SyncOutcome.SIDEBAR -> 24 * 60 * 60L
            SyncOutcome.TIMETABLE -> 6 * 60 * 60L
            SyncOutcome.ANNOUNCEMENTS -> if (night) NIGHT_INTERVAL else 60 * 60L
            // Zastępstwa przy każdym cyklicznym przebiegu, w nocy najwyżej co 2,5 h
            // (push w wersji z GitHuba i tak wywołuje sprawdzenie od razu).
            else -> if (night) NIGHT_INTERVAL else 0L
        }
        if (lastSuccess == null || now < lastSuccess) return true
        // Pierwszy przebieg po północy sprawdza zastępstwa raz: odczyt sprzed północy
        // widżet pokazuje jako "Sprawdzono: wczoraj …".
        if (night && source == SyncOutcome.SUBSTITUTIONS && time!! < NIGHT_TO && now - lastSuccess > time.toSecondOfDay()) return true
        return now - lastSuccess >= interval
    }
}
