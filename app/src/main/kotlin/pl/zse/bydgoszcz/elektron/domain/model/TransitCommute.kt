package pl.zse.bydgoszcz.elektron.domain.model

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Kierunek Przejazdów: rano z Twojego przystanku do szkoły, po lekcjach ze szkoły. */
enum class TransitDirection { TO_SCHOOL, FROM_SCHOOL }

/**
 * Dojazd i powrót według planu lekcji. Liczą się tylko lekcje, które się odbywają (bez zwolnień
 * z zastępstw), jak w powiadomieniu na żywo i czasie do końca lekcji.
 */
object TransitCommute {
    /** Szukanie dojazdu zaczyna się najwcześniej tyle przed lekcją (dalej - kursy za wcześnie). */
    const val LEAD_MINUTES = 90L

    /** Docelowa lekcja do dojazdu - tylko w najbliższych dniach (plan obejmuje tydzień). */
    private const val TARGET_DAYS = 7L

    data class Target(val lesson: Lesson, val start: LocalDateTime)

    private fun held(lessons: List<Lesson>, date: java.time.LocalDate) =
        lessons.filter { it.date == date && SubstitutionDisplay.takesPlace(it) }

    /**
     * Przełączenie z dojazdu na powrót w połowie dnia lekcji: środek między początkiem pierwszej
     * a końcem ostatniej lekcji. Dzień bez lekcji - południe.
     */
    fun switchTime(lessons: List<Lesson>, date: java.time.LocalDate): LocalTime {
        val day = held(lessons, date)
        if (day.isEmpty()) return LocalTime.NOON
        val start = day.minOf { it.timeFrom }
        val end = day.maxOf { it.timeTo }
        return start.plusSeconds(Duration.between(start, end).seconds.coerceAtLeast(0) / 2)
    }

    fun autoDirection(lessons: List<Lesson>, now: LocalDateTime): TransitDirection =
        if (now.toLocalTime().isBefore(switchTime(lessons, now.toLocalDate()))) TransitDirection.TO_SCHOOL
        else TransitDirection.FROM_SCHOOL

    /** Najbliższa lekcja, która się odbędzie i jeszcze się nie zaczęła - na nią liczy się dojazd. */
    fun target(lessons: List<Lesson>, now: LocalDateTime): Target? =
        lessons.asSequence().filter { SubstitutionDisplay.takesPlace(it) }
            .map { Target(it, it.date.atTime(it.timeFrom)) }
            .filter { it.start.isAfter(now) && it.start.isBefore(now.plusDays(TARGET_DAYS)) }
            .minByOrNull { it.start }

    /** Początek szukania dojazdu: zwykle teraz; gdy lekcja jest daleko (np. wieczorem na jutro) - [LEAD_MINUTES] przed nią. */
    fun searchFrom(target: LocalDateTime?, now: LocalDateTime): LocalDateTime =
        target?.minusMinutes(LEAD_MINUTES)?.takeIf { it.isAfter(now) } ?: now

    /** Zapas w pełnych minutach między dotarciem do szkoły a lekcją; ujemny - spóźnienie. */
    fun slackMinutes(atSchoolMs: Long, lessonStartMs: Long): Long = Math.floorDiv(lessonStartMs - atSchoolMs, 60_000L)

    /**
     * Kiedy karta Przejazdów ma się pokazać na stronie głównej: dojazd od 2 godzin przed pierwszą
     * lekcją do jej początku, powrót od początku ostatniej lekcji do godziny po zajęciach.
     * Dzień bez lekcji - bez karty.
     */
    fun dashboardDirection(lessons: List<Lesson>, now: LocalDateTime): TransitDirection? {
        val day = held(lessons, now.toLocalDate())
        if (day.isEmpty()) return null
        val first = day.minOf { it.timeFrom }
        val last = day.maxWith(compareBy<Lesson> { it.timeTo }.thenBy { it.timeFrom })
        val time = now.toLocalTime()
        val date = now.toLocalDate()
        return when {
            !now.isBefore(date.atTime(first).minusHours(2)) && time.isBefore(first) -> TransitDirection.TO_SCHOOL
            !time.isBefore(last.timeFrom) && now.isBefore(date.atTime(last.timeTo).plusHours(1)) -> TransitDirection.FROM_SCHOOL
            else -> null
        }
    }
}

/** Odjazd z tablicy przystanku (BUSearch): opóźnienie na żywo, gdy pojazd jest śledzony. */
data class LiveDeparture(val line: String, val atMs: Long, val delayMinutes: Int?, val live: Boolean)

/** Stan kursu na żywo: o ile minut później (ujemne - wcześniej) i kiedy faktycznie odjedzie. */
data class LiveStatus(val delayMinutes: Int, val expectedMs: Long)

object TransitLive {
    /** Kursy odjeżdżające dalej niż za tyle minut nie mają jeszcze sensownych danych na żywo. */
    const val WINDOW_MINUTES = 30L

    /** Najwyżej tyle zapytań o tablice przy jednym odświeżeniu. */
    const val MAX_BOARDS = 4

    /**
     * Kurs z planera na tablicy przystanku: ta sama linia i godzina rozkładowa (czas na tablicy
     * minus opóźnienie) w granicach 90 s. Kursy bez podanego opóźnienia (tylko rozkład) - bez stanu.
     */
    fun match(ride: TransitRide, board: List<LiveDeparture>): LiveStatus? =
        board.asSequence().filter { it.live && it.delayMinutes != null && it.line.equals(ride.line, ignoreCase = true) }
            .map { it to kotlin.math.abs(it.atMs - it.delayMinutes!! * 60_000L - ride.departureMs) }
            .filter { it.second <= 90_000L }
            .minByOrNull { it.second }
            ?.first?.let { LiveStatus(it.delayMinutes!!, ride.departureMs + it.delayMinutes * 60_000L) }

    /** Kursy, o które warto zapytać tablice: odjazd w ciągu [WINDOW_MINUTES], po jednym na stanowisko i linię. */
    fun boards(journeys: List<TransitJourney>, nowMs: Long): List<Pair<String, String>> =
        journeys.asSequence().map { it.rides.first() }
            .filter { it.departureMs > nowMs - 15 * 60_000L && it.departureMs <= nowMs + WINDOW_MINUTES * 60_000L }
            .map { it.fromId to it.line }.distinct().take(MAX_BOARDS).toList()

    /** Odjazd z uwzględnieniem opóźnienia. */
    fun expected(journey: TransitJourney, live: Map<String, LiveStatus>): Long =
        live[journey.key]?.expectedMs ?: journey.departureMs
}
