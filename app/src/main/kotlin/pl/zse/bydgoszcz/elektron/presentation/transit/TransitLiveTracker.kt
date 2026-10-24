package pl.zse.bydgoszcz.elektron.presentation.transit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    fun clear() { _live.value = emptyMap() }

    private suspend fun refresh() {
        // Symulowany czas (tryb dewelopera) nie pasuje do pojazdów na ulicy.
        if (AppClock.simulated) { clear(); return }
        val (result, prefs) = current() ?: run { clear(); return }
        val now = AppClock.millis()
        // Tylko kursy pokazywane na ekranie (przystanek, przesiadki, dojście przy "Szkoła").
        val shown = TransitJourneys.upcoming(result.journeys, now, if (result.direction == TransitDirection.TO_SCHOOL) null else prefs.preferredOrigin,
            prefs.allowTransfers, _live.value).let { if (prefs.preferredOrigin == null) TransitJourneys.walkable(it, now, _live.value) else it }
        val boards = TransitLive.boards(shown, now)
        val fetched = kotlinx.coroutines.coroutineScope {
            boards.map { board -> async { board to runCatchingCancellable { repository.departures(board.first, board.second) }.getOrNull() } }
                .map { it.await() }
        }.filter { it.second != null }.associate { it.first to it.second!! }
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val latest = current()?.first ?: return
        val next = mutableMapOf<String, LiveStatus>()
        latest.journeys.forEach { trip ->
            val ride = trip.rides.first()
            val board = fetched[ride.fromId to ride.line]
            if (board != null) {
                TransitLive.match(ride, board)?.let { next[trip.key] = it; seenAt[trip.key] = elapsed }
            } else {
                // Tablica chwilowo bez odpowiedzi - poprzedni stan jeszcze przez 3 minuty.
                val previous = _live.value[trip.key]
                if (previous != null && elapsed - (seenAt[trip.key] ?: 0L) <= 3 * 60_000L) next[trip.key] = previous
            }
        }
        seenAt.keys.retainAll(next.keys)
        _live.value = next
    }

    companion object {
        const val REFRESH_MS = 60_000L
    }
}
