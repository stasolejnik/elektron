package pl.zse.bydgoszcz.elektron.presentation.transit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.data.repository.TransitJourneyRepository
import pl.zse.bydgoszcz.elektron.data.repository.TransitLiveRepository
import pl.zse.bydgoszcz.elektron.domain.model.LiveStatus
import pl.zse.bydgoszcz.elektron.domain.model.TransitDirection
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourneys
import pl.zse.bydgoszcz.elektron.domain.model.TransitLive
import pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences
import pl.zse.bydgoszcz.elektron.domain.util.AppClock
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable

/**
 * Opóźnienia na żywo kursów z listy Przejazdów (klucz kursu -> stan) z tablic przystanków BUSearch.
 * Tylko dla kursów pokazywanych na ekranie i odjeżdżających w ciągu [TransitLive.WINDOW_MINUTES];
 * nowa lista kursów - od razu nowe zapytanie, potem co [REFRESH_MS]. Wydzielone z TransitViewModel.
 */
internal class TransitLiveTracker(
    private val repository: TransitLiveRepository,
    private val scope: CoroutineScope,
    /** Bieżący wynik z ustawieniami (null - nic do śledzenia). */
    private val current: () -> Pair<TransitJourneyRepository.Result, TransitPreferences>?,
    /** Klucze kursów bieżącego wyniku - zmiana uruchamia sprawdzenie od nowa. */
    private val keys: Flow<List<String>>
) {
    private val _live = MutableStateFlow<Map<String, LiveStatus>>(emptyMap())
    val live: StateFlow<Map<String, LiveStatus>> = _live
    private val seenAt = mutableMapOf<String, Long>()
    /** Ostatnio pobrane tablice (stanowisko i linia) z chwilą pobrania - patrz [BOARD_FRESH_MS]. */
    private val boardCache = mutableMapOf<Pair<String, String>, Pair<Long, List<pl.zse.bydgoszcz.elektron.domain.model.LiveDeparture>>>()
    /**
     * Pobierane właśnie tablice. Pobranie żyje w [scope], nie w sprawdzeniu, które je zaczęło: przy wczytywaniu
     * lista kursów zmienia się kilka razy na sekundę, a collectLatest przerywał sprawdzenie razem z zapytaniami -
     * ta sama tablica szła po kilka razy (logi: kilkanaście przerwanych zapytań w sekundę). Teraz kolejne
     * sprawdzenie czeka na to samo pobranie.
     */
    private val inFlight = mutableMapOf<Pair<String, String>, Deferred<List<pl.zse.bydgoszcz.elektron.domain.model.LiveDeparture>?>>()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            keys.distinctUntilChanged().collectLatest {
                while (true) { refresh(); delay(REFRESH_MS) }
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    fun clear() { _live.value = emptyMap(); boardCache.clear(); inFlight.values.forEach { it.cancel() }; inFlight.clear() }

    private suspend fun refresh() {
        // Symulowany czas (tryb dewelopera) nie pasuje do pojazdów na ulicy.
        if (AppClock.simulated) { clear(); return }
        val (result, prefs) = current() ?: run { clear(); return }
        val now = AppClock.millis()
        // Tylko kursy pokazywane na ekranie (przystanek, przesiadki, dojście przy "Szkoła").
        val shown = TransitJourneys.upcoming(result.journeys, now, if (result.direction == TransitDirection.TO_SCHOOL) null else prefs.strictOrigin,
            prefs.allowTransfers, _live.value).let { if (prefs.strictOrigin == null) TransitJourneys.walkable(it, now, _live.value) else it }
        val boards = TransitLive.boards(shown, now)
        // Tablica pobrana przed chwilą - bez ponownego zapytania. Przy wczytywaniu lista kursów zmienia się kilka razy
        // w ciągu sekund (kolejne strony planera), a każda zmiana zaczyna sprawdzanie od nowa - dawniej te same tablice
        // szły dwa-trzy razy pod rząd. Odświeżenie co [REFRESH_MS] i tak pobiera je na nowo (są starsze niż [BOARD_FRESH_MS]).
        val started = android.os.SystemClock.elapsedRealtime()
        val reused = boards.mapNotNull { board -> boardCache[board]?.takeIf { started - it.first < BOARD_FRESH_MS }?.let { board to it.second } }.toMap()
        val downloaded = boards.filter { it !in reused }
            .map { board -> board to (inFlight[board]?.takeIf { it.isActive } ?: download(board)) }
            // Pobranie anulowane przez clear() (zmiana celu) to brak wyniku, a nie koniec pętli odświeżania.
            .mapNotNull { (board, pending) -> awaitOrNull(pending)?.let { board to it } }
            .toMap()
        val elapsed = android.os.SystemClock.elapsedRealtime()
        boardCache.keys.retainAll(boards.toSet())
        val fetched = reused + downloaded
        val latest = current()?.first ?: return
        val next = mutableMapOf<String, LiveStatus>()
        latest.journeys.forEach { trip ->
            val ride = trip.rides.first()
            val board = fetched[ride.fromId to ride.line]
            val matched = board?.let { TransitLive.match(ride, it) }
            if (matched != null) {
                next[trip.key] = matched; seenAt[trip.key] = elapsed
            } else {
                // Tablica chwilowo bez odpowiedzi albo już bez tego kursu (pojazd tuż przy przystanku) - poprzedni
                // stan jeszcze przez 3 minuty, dopóki nie minie przewidywany odjazd. Dawniej spóźniony kurs znikał
                // wtedy od razu, bo jego godzina z rozkładu już minęła.
                val previous = _live.value[trip.key]
                if (previous != null && previous.expectedMs > now && elapsed - (seenAt[trip.key] ?: 0L) <= 3 * 60_000L) next[trip.key] = previous
            }
        }
        seenAt.keys.retainAll(next.keys)
        _live.value = next
    }

    private suspend fun <T> awaitOrNull(pending: Deferred<T?>): T? = try { pending.await() } catch (e: kotlinx.coroutines.CancellationException) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive(); null
    }

    /** Pobranie tablicy w [scope]; wynik trafia do [boardCache] także wtedy, gdy sprawdzenie zostało przerwane. */
    private fun download(board: Pair<String, String>) = scope.async {
        runCatchingCancellable { repository.departures(board.first, board.second) }.getOrNull()
            .also { departures ->
                if (departures != null) boardCache[board] = android.os.SystemClock.elapsedRealtime() to departures
                inFlight.remove(board)
            }
    }.also { inFlight[board] = it }

    companion object {
        const val REFRESH_MS = 60_000L
        /** Tablica młodsza niż tyle jest używana ponownie przy zmianie listy kursów. */
        const val BOARD_FRESH_MS = 20_000L
    }
}
