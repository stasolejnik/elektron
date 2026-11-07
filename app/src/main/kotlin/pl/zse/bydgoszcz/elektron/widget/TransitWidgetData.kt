package pl.zse.bydgoszcz.elektron.widget

import pl.zse.bydgoszcz.elektron.data.repository.TransitJourneyRepository
import pl.zse.bydgoszcz.elektron.domain.model.DateTexts
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.TransitCommute
import pl.zse.bydgoszcz.elektron.domain.model.TransitDirection
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourney
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourneys
import pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences
import pl.zse.bydgoszcz.elektron.domain.model.TransitTimes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Widżet "Przejazdy": najbliższe kursy w kierunku według pory - do połowy dnia lekcji dojazd do szkoły,
 * potem powrót (jak automatyczny kierunek w zakładce). Dane z ostatniego zapisanego wyniku Przejazdów
 * (zakładka, karta albo odświeżenie widżetu); czysta logika bez Androida - testowalna.
 */
internal object TransitWidgetData {
    data class Row(val key: String, val line: String, val toName: String, val until: String, val detail: String, val description: String)

    sealed interface State {
        data object Failed : State
        /** Przejazdy wyłączone albo bez Twojego przystanku - wskazówka zamiast kursów. */
        data class Message(val text: String, val target: String) : State
        data class Ready(
            val toSchool: Boolean,
            val title: String,
            /** "Sprawdzono 12:15"; null - jeszcze nie sprawdzano (brak pasującego wyniku). */
            val checked: String?,
            val rows: List<Row>,
            /** Tekst zamiast kursów, gdy [rows] puste. */
            val empty: String?,
            /** Zakładka do otwarcia po dotknięciu ("transit" albo "dashboard", gdy zakładki nie ma na pasku). */
            val target: String
        ) : State
    }

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    const val MAX_ROWS = 6

    /** Kierunek, który pokazuje widżet: automatyczny według planu (bez ręcznego wyboru z zakładki). */
    fun direction(lessons: List<Lesson>, now: LocalDateTime): TransitDirection = TransitCommute.autoDirection(lessons, now)

    /** Zapisany wynik dotyczy tego, co widżet pokazuje: kierunek, przystanki, przesiadki (jak w zakładce). */
    fun matches(result: TransitJourneyRepository.Result, prefs: TransitPreferences, direction: TransitDirection): Boolean =
        prefs.canLoadJourneys && result.direction == direction && result.destinationKey == prefs.destination?.key &&
            result.originKey == prefs.originKey && result.allowTransfers == prefs.allowTransfers

    fun build(prefs: TransitPreferences, lessons: List<Lesson>, result: TransitJourneyRepository.Result?, now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault()): State {
        val target = if (prefs.visible) "transit" else "dashboard"
        val destination = prefs.destination
        if (!prefs.canLoadJourneys || destination == null)
            return State.Message(if (destination == null) "Wybierz swój przystanek w Ustawieniach → Przejazdy."
                else "Włącz Przejazdy w Ustawieniach → Przejazdy.", target)
        val direction = direction(lessons, now)
        val toSchool = direction == TransitDirection.TO_SCHOOL
        val title = if (toSchool) "Do szkoły" else "Ze szkoły"
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val matching = result?.takeIf { matches(it, prefs, direction) }
            ?: return State.Ready(toSchool, title, null, emptyList(), "Dotknij, aby sprawdzić połączenia.", target)
        val upcoming = visible(matching.journeys, prefs, toSchool, nowMs)
        // Od najbliższego kursu; z ustawieniem "Kursy na czas przed lekcją" do szkoły - od kursu z zapasem (jak w zakładce).
        val lessonStartMs = if (toSchool) TransitCommute.target(lessons, now)?.start?.atZone(zone)?.toInstant()?.toEpochMilli() else null
        val first = if (toSchool) TransitJourneys.anchorToSchool(upcoming, lessonStartMs, marginMinutes = prefs.arrivalMarginMinutes) else 0
        val rows = upcoming.drop(first).take(MAX_ROWS).map { row(it, toSchool, nowMs, zone) }
        val checked = "Sprawdzono " + DateTexts.past(LocalDateTime.ofInstant(Instant.ofEpochMilli(matching.fetchedAt), zone), now.toLocalDate())
        return State.Ready(toSchool, title, checked, rows, if (rows.isEmpty()) "Brak nadchodzących połączeń. Dotknij, aby sprawdzić." else null, target)
    }

    /** Kursy, które widżet może pokazać: jeszcze nie odjechały, a ze szkoły (przy "Szkoła") - zdążysz do nich dojść. */
    fun visible(journeys: List<TransitJourney>, prefs: TransitPreferences, toSchool: Boolean, nowMs: Long): List<TransitJourney> =
        TransitJourneys.upcoming(journeys, nowMs, if (toSchool) null else prefs.strictOrigin, prefs.allowTransfers)
            .let { if (prefs.strictOrigin == null) TransitJourneys.walkable(it, nowMs) else it }
            // Od najbliższego odjazdu (ze szkoły wynik jest według przyjazdu do celu).
            .sortedBy { it.departureMs }

    private fun time(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).format(TIME)

    /**
     * Wiersz jak karta w zakładce, krócej: linia → przystanek, na którym wysiadasz, "za X min";
     * pod spodem do szkoły - odjazd i o której jesteś na przystanku przy szkole, ze szkoły - skąd i o której.
     */
    private fun row(trip: TransitJourney, toSchool: Boolean, nowMs: Long, zone: ZoneId): Row {
        val ride = trip.rides.first()
        val until = TransitTimes.until(trip.departureMs, nowMs)
        val transfers = if (trip.transfers > 0) " · przesiadka" else ""
        // Jak karta w zakładce: godzina wysiadki na przystanku przy szkole (zapas do lekcji liczy się z dojściem).
        val detail = if (toSchool) "Odjazd ${time(trip.departureMs, zone)} · na przystanku ${time(trip.arrivalMs, zone)}$transfers"
            else "${ride.fromName} · ${time(trip.departureMs, zone)}$transfers"
        val description = "Linia ${ride.line} do przystanku ${ride.toName}, $until, $detail"
        return Row(trip.key, ride.line, ride.toName, until, detail, description)
    }

    /**
     * Kiedy przerysować: co minutę, gdy są kursy ("za X min"); bez kursów - za 15 min (widżet sam
     * poprosi wtedy o odświeżenie w porze dojazdu).
     */
    fun nextTick(state: State, now: LocalDateTime): LocalDateTime? = when (state) {
        is State.Ready -> if (state.rows.isNotEmpty()) now.withSecond(0).withNano(0).plusMinutes(1) else now.plusMinutes(15)
        else -> null
    }
}
