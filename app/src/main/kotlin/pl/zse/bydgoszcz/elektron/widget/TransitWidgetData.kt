package pl.zse.bydgoszcz.elektron.widget

import pl.zse.bydgoszcz.elektron.data.repository.TransitJourneyRepository
import pl.zse.bydgoszcz.elektron.domain.model.DateTexts
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.TransitCommute
import pl.zse.bydgoszcz.elektron.domain.model.TransitDirection
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourney
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourneys
import pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences
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
    /**
     * Kurs w widżecie. [stop] - do szkoły przystanek, na którym wysiadasz (przy strzałce), ze szkoły - na którym
     * wsiadasz (cel jest jeden i stoi w podtytule). Po prawej [until] i [time] odjazdu, pod spodem [detail]
     * jak w karcie zakładki; [urgent] - spóźnienie na lekcję (pogrubione).
     */
    data class Row(val key: String, val line: String, val stop: String, val toSchool: Boolean, val until: String,
        val time: String, val detail: String?, val urgent: Boolean, val description: String,
        /** Strzałka przed [stop]: do szkoły - przystanek wysiadki, ze szkoły przy jednym przystanku - kierunek pojazdu. */
        val arrow: Boolean = toSchool)

    /** Stan odświeżania widżetu (TransitWidgetRefresher): trwa albo ostatnie się nie udało. */
    data class Status(val refreshing: Boolean = false, val failed: Boolean = false)

    sealed interface State {
        data object Failed : State
        /** Przejazdy wyłączone albo bez Twojego przystanku - wskazówka zamiast kursów. */
        data class Message(val text: String, val target: String) : State
        data class Ready(
            val toSchool: Boolean,
            val title: String,
            /** "Do: Dworzec Główny · sprawdzono 15:02" (albo "sprawdzam…", "nie udało się odświeżyć"). */
            val subtitle: String,
            val rows: List<Row>,
            /** Tekst zamiast kursów, gdy [rows] puste. */
            val empty: String?,
            /** Zakładka do otwarcia po dotknięciu ("transit" albo "dashboard", gdy zakładki nie ma na pasku). */
            val target: String
        ) : State
    }

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    const val MAX_ROWS = 6
    /** Kursy dalej niż za tyle nie trafiają do widżetu. */
    const val MAX_AHEAD_MS = 3 * 60 * 60_000L

    /** Kierunek, który pokazuje widżet: automatyczny według planu (bez ręcznego wyboru z zakładki). */
    fun direction(lessons: List<Lesson>, now: LocalDateTime): TransitDirection = TransitCommute.autoDirection(lessons, now)

    /** Zapisany wynik dotyczy tego, co widżet pokazuje: kierunek, przystanki, przesiadki (jak w zakładce). */
    fun matches(result: TransitJourneyRepository.Result, prefs: TransitPreferences, direction: TransitDirection): Boolean =
        prefs.canLoadJourneys && result.direction == direction && result.destinationKey == prefs.destination?.key &&
            result.originKey == prefs.originKey && result.allowTransfers == prefs.allowTransfers

    /**
     * Czy widżet sam pobiera kursy (bez dotknięcia): w porze dojazdu i w ciągu dnia (6:00-21:00) - dawniej tylko
     * w porze dojazdu, a poza nią trzeba było dotknąć widżetu, żeby cokolwiek pokazał.
     */
    fun autoRefresh(lessons: List<Lesson>, now: LocalDateTime): Boolean =
        TransitCommute.dashboardDirection(lessons, now) != null || now.hour in AUTO_FROM_HOUR until AUTO_TO_HOUR
    const val AUTO_FROM_HOUR = 6
    const val AUTO_TO_HOUR = 21

    fun build(prefs: TransitPreferences, lessons: List<Lesson>, result: TransitJourneyRepository.Result?, now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(), status: Status = Status(), auto: Boolean = autoRefresh(lessons, now)): State {
        val target = if (prefs.visible) "transit" else "dashboard"
        val destination = prefs.destination
        if (!prefs.canLoadJourneys || destination == null)
            return State.Message(if (destination == null) "Wybierz swój przystanek w Ustawieniach → Przejazdy."
                else "Włącz Przejazdy w Ustawieniach → Przejazdy.", target)
        val direction = direction(lessons, now)
        val toSchool = direction == TransitDirection.TO_SCHOOL
        val title = if (toSchool) "Do szkoły" else "Ze szkoły"
        // Twój przystanek raz, w podtytule ("Z:" / "Do:" - nazw przystanków się nie odmienia).
        val route = (if (toSchool) "Z: " else "Do: ") + destination.name
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val matching = result?.takeIf { matches(it, prefs, direction) }
        val state = when {
            status.refreshing -> "sprawdzam…"
            status.failed -> "nie udało się odświeżyć"
            matching != null -> "sprawdzono " + DateTexts.past(LocalDateTime.ofInstant(Instant.ofEpochMilli(matching.fetchedAt), zone), now.toLocalDate())
            else -> null
        }
        val subtitle = listOfNotNull(route, state).joinToString(" · ")
        // Bez wyniku widżet sam sprawdza (w ciągu dnia) - "Sprawdzam połączenia…" zamiast prośby o dotknięcie.
        val checking = "Sprawdzam połączenia…"
        val failedText = "Nie udało się sprawdzić połączeń. Dotknij, aby spróbować ponownie."
        if (matching == null)
            return State.Ready(toSchool, title, subtitle, emptyList(), when {
                status.refreshing -> checking
                status.failed -> failedText
                auto -> checking
                else -> "Dotknij, aby sprawdzić połączenia."
            }, target)
        val upcoming = visible(matching.journeys, prefs, toSchool, nowMs)
        // Od najbliższego kursu; z ustawieniem "Kursy na czas przed lekcją" do szkoły - od kursu z zapasem (jak w zakładce).
        val lessonStartMs = if (toSchool) TransitCommute.target(lessons, now)?.start?.atZone(zone)?.toInstant()?.toEpochMilli() else null
        val first = if (toSchool) TransitJourneys.anchorToSchool(upcoming, lessonStartMs, marginMinutes = prefs.arrivalMarginMinutes) else 0
        // Najwyżej 3 godziny naprzód - nocny kurs "za 6 h 15 min" pod popołudniowymi wyglądał dziwnie.
        val rows = upcoming.drop(first).filter { it.departureMs - nowMs <= MAX_AHEAD_MS }.take(MAX_ROWS).map { row(it, toSchool, nowMs, lessonStartMs, zone, byStop = prefs.strictOrigin == null) }
        return State.Ready(toSchool, title, subtitle, rows, when {
            rows.isNotEmpty() -> null
            status.refreshing -> checking
            status.failed -> failedText
            auto -> checking
            else -> "Brak nadchodzących połączeń. Dotknij, aby sprawdzić."
        }, target)
    }

    /** Kursy, które widżet może pokazać: jeszcze nie odjechały, a ze szkoły (przy "Szkoła") - zdążysz do nich dojść. */
    fun visible(journeys: List<TransitJourney>, prefs: TransitPreferences, toSchool: Boolean, nowMs: Long): List<TransitJourney> =
        TransitJourneys.upcoming(journeys, nowMs, if (toSchool) null else prefs.strictOrigin, prefs.allowTransfers)
            .let { if (prefs.strictOrigin == null) TransitJourneys.walkable(it, nowMs) else it }
            // Od najbliższego odjazdu (ze szkoły wynik jest według przyjazdu do celu).
            .sortedBy { it.departureMs }

    private fun time(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).format(TIME)

    /** "za 11 min", "za 1 h 23 min" - jak w zakładce (wspólne [pl.zse.bydgoszcz.elektron.domain.model.TransitTimes]). */
    fun until(departureMs: Long, nowMs: Long): String = pl.zse.bydgoszcz.elektron.domain.model.TransitTimes.until(departureMs, nowMs)

    /** "12 min", "1 h 12 min", "2 h". */
    private fun short(minutes: Long): String = pl.zse.bydgoszcz.elektron.domain.model.TransitTimes.duration(minutes)

    /** [byStop] - ze szkoły przy "Szkoła" (kursy z różnych przystanków): nazwa przystanku zamiast kierunku pojazdu. */
    private fun row(trip: TransitJourney, toSchool: Boolean, nowMs: Long, lessonStartMs: Long?, zone: ZoneId, byStop: Boolean = true): Row {
        val ride = trip.rides.first()
        val until = until(trip.departureMs, nowMs)
        val transfer = if (trip.transfers > 0) "przesiadka" else null
        var urgent = false
        val detail = if (toSchool) {
            // Jak karta w zakładce: godzina wysiadki na przystanku przy szkole, zapas do lekcji z dojściem.
            val slack = lessonStartMs?.let { start -> trip.schoolArrivalMs?.let { TransitCommute.slackMinutes(it, start) } }
            listOfNotNull("przyjazd ${time(trip.arrivalMs, zone)}", when {
                slack == null -> null
                slack < 0 -> "spóźnienie ${short(-slack)}".also { urgent = true }
                slack == 0L -> "na styk"
                else -> "zapas ${short(slack)}"
            }, transfer).joinToString(" · ")
        } else {
            // Ze szkoły: kiedy będziesz na swoim przystanku (bez "wyjdź o…", jak w zakładce).
            listOfNotNull("przyjazd ${time(trip.arrivalMs, zone)}", transfer).joinToString(" · ")
        }
        val time = time(trip.departureMs, zone)
        // Ze szkoły przy jednym przystanku jego nazwa powtarzałaby się w każdym wierszu - kierunek pojazdu pomaga go rozpoznać.
        val title = when {
            toSchool -> ride.toName
            byStop -> ride.fromName
            else -> ride.direction
        }
        val where = if (toSchool) "do przystanku ${ride.toName}" else if (byStop) "z przystanku ${ride.fromName}" else "w kierunku ${ride.direction}"
        val description = listOfNotNull("Linia ${ride.line} $where", "$until, o $time", detail).joinToString(", ")
        return Row(trip.key, ride.line, title, toSchool, until, time, detail, urgent, description, arrow = toSchool || !byStop)
    }

    /**
     * Kiedy przerysować: co minutę, gdy są kursy ("za X min"); bez kursów - za 5 min (widżet sam
     * poprosi wtedy o odświeżenie, z limitem częstotliwości w odświeżaczu).
     */
    fun nextTick(state: State, now: LocalDateTime): LocalDateTime? = when (state) {
        is State.Ready -> if (state.rows.isNotEmpty()) now.withSecond(0).withNano(0).plusMinutes(1) else now.plusMinutes(5)
        else -> null
    }
}
