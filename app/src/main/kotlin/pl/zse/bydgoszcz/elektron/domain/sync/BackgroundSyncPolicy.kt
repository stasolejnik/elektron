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

    /** Dzień bez lekcji (weekend, święto, ferie, wakacje): zastępstwa najwyżej co tyle sekund. */
    const val REST_DAY_INTERVAL = 45 * 60L
    /** Od tej godziny dnia bez lekcji przed dniem lekcyjnym - znów co przebieg (zastępstwa i informacje na jutro). */
    val EVENING_BEFORE_SCHOOL: LocalTime = LocalTime.of(15, 0)

    /**
     * Dzień bez lekcji, w którym zastępstwa sprawdzane są rzadziej - poza popołudniem przed dniem lekcyjnym
     * (np. niedziela od 15:00), kiedy szkoła publikuje zastępstwa na jutro.
     */
    fun isRestDay(date: java.time.LocalDate, time: LocalTime): Boolean {
        fun schoolDay(d: java.time.LocalDate) = d.dayOfWeek != java.time.DayOfWeek.SATURDAY &&
            d.dayOfWeek != java.time.DayOfWeek.SUNDAY && !pl.zse.bydgoszcz.elektron.domain.model.SchoolCalendar.isDayOff(d)
        if (schoolDay(date)) return false
        return !(time >= EVENING_BEFORE_SCHOOL && schoolDay(date.plusDays(1)))
    }

    /** [time] - lokalna godzina przebiegu (null - reguła nocna wyłączona), [restDay] - dzień bez lekcji ([isRestDay]). */
    fun isDue(source: String, lastSuccess: Long?, now: Long, time: LocalTime? = null, restDay: Boolean = false): Boolean {
        val night = time != null && isNight(time)
        val interval = when (source) {
            SyncOutcome.SIDEBAR -> 24 * 60 * 60L
            SyncOutcome.TIMETABLE -> 6 * 60 * 60L
            SyncOutcome.ANNOUNCEMENTS -> if (night) NIGHT_INTERVAL else 60 * 60L
            // Zastępstwa przy każdym cyklicznym przebiegu, w dni bez lekcji co 45 min, w nocy najwyżej co 2,5 h
            // (push w wersji z GitHuba i tak wywołuje sprawdzenie od razu).
            else -> when {
                night -> NIGHT_INTERVAL
                restDay -> REST_DAY_INTERVAL
                else -> 0L
            }
        }
        if (lastSuccess == null || now < lastSuccess) return true
        // Pierwszy przebieg po północy sprawdza zastępstwa raz: odczyt sprzed północy
        // widżet pokazuje jako "Sprawdzono: wczoraj …".
        if (night && source == SyncOutcome.SUBSTITUTIONS && time!! < NIGHT_TO && now - lastSuccess > time.toSecondOfDay()) return true
        return now - lastSuccess >= interval
    }
}
