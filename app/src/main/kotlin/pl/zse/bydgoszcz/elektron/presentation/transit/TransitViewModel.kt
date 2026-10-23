package pl.zse.bydgoszcz.elektron.presentation.transit

import pl.zse.bydgoszcz.elektron.domain.util.AppClock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import pl.zse.bydgoszcz.elektron.data.repository.TransitPreferencesRepository
import pl.zse.bydgoszcz.elektron.data.repository.TransitStopCatalog
import pl.zse.bydgoszcz.elektron.data.repository.TransitJourneyRepository
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences
import pl.zse.bydgoszcz.elektron.domain.model.TransitCommute
import pl.zse.bydgoszcz.elektron.domain.model.TransitDirection
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourneys
import pl.zse.bydgoszcz.elektron.domain.model.TransitLive
import pl.zse.bydgoszcz.elektron.domain.model.LiveStatus
import pl.zse.bydgoszcz.elektron.data.repository.TransitLiveRepository
import pl.zse.bydgoszcz.elektron.data.repository.TransitSchoolDay
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import pl.zse.bydgoszcz.elektron.presentation.announcements.SearchText
import javax.inject.Inject

@HiltViewModel
class TransitViewModel @Inject constructor(private val preferencesRepository: TransitPreferencesRepository,
    private val catalog: TransitStopCatalog,
    private val journeyRepository: TransitJourneyRepository,
    /** Plan lekcji do kierunku i lekcji docelowej; null (testy) - zawsze ze szkoły. */
    private val schoolDay: TransitSchoolDay? = null,
    /** Opóźnienia z tablic przystanków; null (testy) - same godziny rozkładowe. */
    private val liveRepository: TransitLiveRepository? = null) : ViewModel() {
    data class Settings(val preferences: TransitPreferences = TransitPreferences(), val ready: Boolean = false, val failed: Boolean = false)
    val settings = preferencesRepository.preferences.map { Settings(it, ready = true) }
        .catch { emit(Settings(ready = true, failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())
    /**
     * Kierunek i lekcja docelowa. Automatycznie: do szkoły do połowy dnia lekcji, potem ze szkoły.
     * Ręczny wybór obowiązuje, dopóki automatyczny kierunek się nie zmieni (np. następnego ranka).
     */
    data class Commute(val direction: TransitDirection = TransitDirection.FROM_SCHOOL,
        val auto: TransitDirection = TransitDirection.FROM_SCHOOL,
        val target: TransitCommute.Target? = null,
        /** Do szkoły: od kiedy szukać (epoch ms). */
        val searchFromMs: Long? = null) {
        val toSchool get() = direction == TransitDirection.TO_SCHOOL
        val targetMs get() = target?.start?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }
    private var schoolLessons: List<pl.zse.bydgoszcz.elektron.domain.model.Lesson> = emptyList()
    private var manualDirection: Pair<TransitDirection, TransitDirection>? = null
    private val _commute = MutableStateFlow(computeCommute())
    val commute: StateFlow<Commute> = _commute
    private fun computeCommute(): Commute {
        if (schoolDay == null) return Commute()
        val now = AppClock.now()
        val auto = TransitCommute.autoDirection(schoolLessons, now)
        val manual = manualDirection?.takeIf { it.first == auto }
        if (manual == null) manualDirection = null
        val direction = manual?.second ?: auto
        val target = TransitCommute.target(schoolLessons, now)
        val from = TransitCommute.searchFrom(target?.start, now).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Pełne minuty - wartość nie zmienia się przy każdym przeliczeniu (stabilny klucz zapytania).
        return Commute(direction, auto, target, (from / 60_000L) * 60_000L)
    }
    private fun updateCommute() { _commute.value = computeCommute() }
    /** Przełącznik "Do szkoły / Ze szkoły". */
    fun setDirection(direction: TransitDirection) {
        val auto = _commute.value.auto
        manualDirection = if (direction == auto) null else auto to direction
        updateCommute()
    }

    val query = MutableStateFlow("")
    val stops = MutableStateFlow<List<TransitDestination>>(emptyList())
    private data class IndexedStop(val stop: TransitDestination, val normalized: String, val distance: Double)
    private val indexedStops = stops.map { all -> all.map {
        IndexedStop(it, SearchText.normalize(it.name), pl.zse.bydgoszcz.elektron.domain.model.SchoolTransit.distance(it.latitude, it.longitude))
    }.sortedWith(compareBy<IndexedStop> { it.distance }.thenBy { it.normalized }) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val filteredStops = combine(query, indexedStops) { text, all ->
        val words = SearchText.normalize(text).split(' ').filter { it.isNotBlank() }
        all.filter { stop -> words.all { it in stop.normalized } }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val matchingStops = filteredStops.map { all -> all.map { it.stop }.sortedBy { it.name } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Bez limitu odległości (dawniej 1 km) - od najbliższego szkoły.
    val originStops = filteredStops.map { all -> all.map { it.stop } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val results = matchingStops.map { it.take(80) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settingsError = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)
    val saving = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val stale = MutableStateFlow(false)

    val journeys = MutableStateFlow<TransitJourneyRepository.Result?>(null)
    val journeyLoading = MutableStateFlow(false)
    val journeyError = MutableStateFlow<String?>(null)
    val moreLoading = MutableStateFlow(false)
    private var journeyJob: Job? = null
    private var journeyMinimumCount = 1
    private var automaticRetryAt = 0L
    private var automaticRetryKey: RequestKey? = null
    private var walkingJob: Job? = null
    private var journeyKey: RequestKey? = null
    private var walkingKey: RequestKey? = null
    /** Co jest szukane: kierunek, Twój przystanek, przystanek przy szkole (tylko ze szkoły), przesiadki. */
    private data class RequestKey(val direction: TransitDirection, val destination: String?, val origin: String?, val transfers: Boolean)
    /** Przystanek przy szkole (null - "Szkoła": najbliższe przystanki) - w obu kierunkach. */
    @Suppress("UNUSED_PARAMETER")
    private fun originFor(prefs: TransitPreferences, direction: TransitDirection = _commute.value.direction) = prefs.preferredOrigin
    private fun requestKey(prefs: TransitPreferences, direction: TransitDirection = _commute.value.direction) = prefs.takeIf { it.canLoadJourneys }?.let {
        RequestKey(direction, it.destination?.key, originFor(it, direction)?.key, it.allowTransfers)
    }
    private fun keyOf(result: TransitJourneyRepository.Result) = RequestKey(result.direction, result.destinationKey, result.originKey, result.allowTransfers)
    private fun matches(result: TransitJourneyRepository.Result, prefs: TransitPreferences) =
        prefs.canLoadJourneys && keyOf(result) == requestKey(prefs)
    /** Wynik dla bieżącego wyboru (kierunek, przystanki, przesiadki) - ekran pokazuje tylko taki. */
    fun matchesCurrent(result: TransitJourneyRepository.Result?): Boolean = result != null && matches(result, settings.value.preferences)
    init {
        schoolDay?.let { source ->
            viewModelScope.launch { source.lessons.collect { schoolLessons = it; updateCommute() } }
            // Kierunek i lekcja docelowa zmieniają się z czasem także bez otwartej zakładki: dawniej
            // przeliczane tylko przy odświeżeniu zakładki, więc karta na stronie głównej (pokazywana,
            // gdy kierunek się zgadza) po południu albo po zmianie symulowanego czasu się nie pojawiała.
            viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
                    updateCommute()
                }
            }
        }
        // Zmiana symulowanego czasu (tryb dewelopera): wyniki z innej chwili są nieaktualne.
        viewModelScope.launch {
            AppClock.offsetMs.drop(1).collect {
                updateCommute()
                cancelJourneys(); journeys.value = null; live.value = emptyMap(); journeyError.value = null
                automaticRetryKey = null
                if (journeyConsumers.isNotEmpty()) refreshJourneys(journeyMinimumCount)
            }
        }
        viewModelScope.launch {
            combine(settings.filter { it.ready }, _commute.map { it.direction }) { s, direction -> requestKey(s.preferences, direction) }
                .distinctUntilChanged().collect { key ->
                // Wartość już nieaktualna (obserwator działający na innym wątku obsłużył ją po nowszej, np.
                // w testach z dispatcherem Unconfined): nie przerywamy pracy dla bieżącego wyboru - inaczej
                // trwające zapytanie było przerywane i wysyłane od nowa (dodatkowe zapytanie do planera).
                if (key != requestKey(settings.value.preferences)) return@collect
                if (journeys.value?.let(::keyOf) != key) { journeys.value = null; live.value = emptyMap() }
                // The screen can start the new request before this observer receives settings.
                // Cancel only work belonging to the old selection.
                if (journeyKey != key) cancelJourneyRequest()
                if (walkingKey != key) cancelWalkingRequest()
                journeyError.value = null
            }
        }
        // Ostatni pełny wynik na dysk: po ponownym uruchomieniu aplikacji odjazdy są widoczne od razu.
        viewModelScope.launch {
            journeys.filterNotNull().filter { it.complete }.distinctUntilChanged().collect { result ->
                runCatchingCancellable { journeyRepository.saveLast(result) }
            }
        }
    }
    private fun cancelJourneyRequest() {
        journeyJob?.cancel(); journeyJob = null; journeyKey = null
        // W trakcie odczytu zapisu (pierwsze otwarcie) nadal "ładowanie" - bez mignięcia pustej karty.
        journeyLoading.value = restoringCache; moreLoading.value = false
    }
    private fun cancelWalkingRequest() { walkingJob?.cancel(); walkingJob = null; walkingKey = null }
    private val journeyConsumers = mutableSetOf<Any>()
    fun startJourneySession(consumer: Any, minimumCount: Int = 1) {
        journeyConsumers.add(consumer); refreshJourneys(minimumCount); startLive()
    }
    private var cacheChecked = false
    private var restoringCache = false
    private var restoreMinimumCount = 1
    fun refreshJourneys(minimumCount: Int = 1) {
        if (journeyConsumers.isEmpty()) return
        // Co minutę (ekran) - kierunek i lekcja docelowa mogły się zmienić.
        updateCommute()
        // Ekran woła odświeżenie kilka razy przy starcie - czekają na odczyt zapisu, żeby świeży
        // wynik nie wywołał zapytania.
        if (restoringCache) { restoreMinimumCount = maxOf(restoreMinimumCount, minimumCount); return }
        if (!cacheChecked) {
            // Najpierw zapisany wynik (jeśli świeży, bez żadnego zapytania - jak po minucie w aplikacji).
            cacheChecked = true; restoringCache = true; restoreMinimumCount = minimumCount
            // Bez "Brak nadchodzących połączeń" na karcie w trakcie odczytu.
            journeyLoading.value = true
            viewModelScope.launch {
                try { restoreCachedJourneys() } finally {
                    restoringCache = false
                    if (journeyJob?.isActive != true) journeyLoading.value = false
                }
                refreshJourneys(restoreMinimumCount)
            }
            return
        }
        if (requestKey(settings.value.preferences) == automaticRetryKey && android.os.SystemClock.elapsedRealtime() < automaticRetryAt) return
        loadJourneys(minimumCount = minimumCount)
    }
    private var savedStopsChecked = false
    /**
     * Raz na sesję, po pierwszym wyszukiwaniu - udanym albo nie (katalog jest już wczytany, zwykle bez
     * dodatkowego zapytania): zapisany cel i przystanek początkowy wobec katalogu. Zmienione numery stanowisk -
     * ten sam przystanek z katalogu (patrz TransitStops).
     */
    private fun checkSavedStops() {
        if (savedStopsChecked) return
        savedStopsChecked = true
        viewModelScope.launch {
            val prefs = settings.first { it.ready }.takeUnless { it.failed }?.preferences ?: return@launch
            if (prefs.destination == null && prefs.preferredOrigin == null) return@launch
            val fresh = runCatchingCancellable { catalog.load() }.getOrNull()?.takeUnless { it.stale } ?: return@launch
            val destination = pl.zse.bydgoszcz.elektron.domain.model.TransitStops.refreshed(prefs.destination, fresh.stops)
            val origin = pl.zse.bydgoszcz.elektron.domain.model.TransitStops.refreshed(prefs.preferredOrigin, fresh.stops)
            val replacements = listOfNotNull(destination?.let { prefs.destination!!.key to it }, origin?.let { prefs.preferredOrigin!!.key to it }).toMap()
            if (replacements.isNotEmpty()) {
                android.util.Log.i("Transit", "Zapisany przystanek ma nowe numery stanowisk - aktualizuję")
                runCatchingCancellable { preferencesRepository.refreshStops(replacements) }
            }
        }
    }
    private suspend fun restoreCachedJourneys() {
        val ready = settings.first { it.ready }
        if (ready.failed || journeys.value != null) return
        val cached = runCatchingCancellable { journeyRepository.loadLast() }.getOrNull() ?: return
        if (journeys.value == null && matches(cached, settings.value.preferences)) journeys.value = cached
    }
    fun stopJourneySession(consumer: Any) {
        if (journeyConsumers.remove(consumer) && journeyConsumers.isEmpty()) { cancelJourneys(); liveJob?.cancel(); liveJob = null }
    }

    /** Opóźnienia na żywo kursów z listy (klucz kursu -> stan), tylko dla bieżącego wyniku. */
    val live = MutableStateFlow<Map<String, LiveStatus>>(emptyMap())
    private val liveSeenAt = mutableMapOf<String, Long>()
    private var liveJob: Job? = null
    private fun startLive() {
        if (liveRepository == null || liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            // Nowa lista kursów - od razu nowe zapytanie, potem co minutę.
            journeys.map { result -> result?.takeIf(::matchesCurrent)?.journeys?.map { it.key }.orEmpty() }
                .distinctUntilChanged().collectLatest {
                    while (true) { refreshLive(); kotlinx.coroutines.delay(LIVE_REFRESH_MS) }
                }
        }
    }
    private suspend fun refreshLive() {
        val repository = liveRepository ?: return
        // Symulowany czas (tryb dewelopera) nie pasuje do pojazdów na ulicy.
        if (AppClock.simulated) { live.value = emptyMap(); return }
        val result = journeys.value?.takeIf(::matchesCurrent) ?: run { live.value = emptyMap(); return }
        val now = AppClock.millis()
        val boards = TransitLive.boards(result.journeys, now)
        val fetched = kotlinx.coroutines.coroutineScope {
            boards.map { board -> async { board to runCatchingCancellable { repository.departures(board.first, board.second) }.getOrNull() } }
                .map { it.await() }
        }.filter { it.second != null }.associate { it.first to it.second!! }
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val current = journeys.value?.takeIf(::matchesCurrent) ?: return
        val next = mutableMapOf<String, LiveStatus>()
        current.journeys.forEach { trip ->
            val ride = trip.rides.first()
            val board = fetched[ride.fromId to ride.line]
            if (board != null) {
                TransitLive.match(ride, board)?.let { next[trip.key] = it; liveSeenAt[trip.key] = elapsed }
            } else {
                // Tablica chwilowo bez odpowiedzi - poprzedni stan jeszcze przez 3 minuty.
                val previous = live.value[trip.key]
                if (previous != null && elapsed - (liveSeenAt[trip.key] ?: 0L) <= 3 * 60_000L) next[trip.key] = previous
            }
        }
        liveSeenAt.keys.retainAll(next.keys)
        live.value = next
    }
    fun cancelJourneys() { cancelJourneyRequest(); cancelWalkingRequest() }
    fun loadJourneys(force: Boolean = false, more: Boolean = false, minimumCount: Int = 1) {
        val prefs = settings.value.preferences
        val destination = prefs.destination ?: return
        if (!prefs.canLoadJourneys) return
        val commuteNow = _commute.value
        val toSchool = commuteNow.toSchool
        val origin = originFor(prefs, commuteNow.direction)
        val fromMs = if (toSchool) commuteNow.searchFromMs ?: AppClock.millis() else null
        val old = journeys.value?.takeIf { matches(it, prefs) }
        val reusable = !force && old?.canReuse(destination.key, AppClock.millis(), origin?.key, prefs.allowTransfers, commuteNow.direction, fromMs) == true
        val requestedCount = minimumCount.coerceIn(1, 300)
        val existingCount = old?.journeys?.count { it.departureMs > AppClock.millis() } ?: 0
        if (!more && reusable && (existingCount >= requestedCount || old?.nextWhenMs == null)) {
            journeyError.value = null
            // Dojście "niedostępne" przez brak sieci - po powrocie internetu ponawiamy (dawniej dopiero
            // z nowym wynikiem, do 10 minut później).
            val retryWalking = old!!.journeys.any { !it.walkAvailable && !it.walkPending } && walkingJob?.isActive != true &&
                journeyRepository.walkingRetryDue()
            val current = if (!retryWalking) old else old.copy(journeys = old.journeys.map {
                if (it.walkAvailable) it else it.copy(walkPending = true)
            }).also { journeys.value = it }
            if (current.journeys.any { it.walkPending } && walkingJob?.isActive != true) loadWalking(current)
            return
        }
        val append = more || (reusable && existingCount < requestedCount)
        val after = if (append) old?.nextWhenMs ?: return else null
        val key = requestKey(prefs)
        if (journeyJob?.isActive == true && journeyKey == key && !force) {
            journeyMinimumCount = maxOf(journeyMinimumCount, requestedCount)
            return
        }
        if (walkingKey != key) cancelWalkingRequest()
        cancelJourneyRequest()
        journeyKey = key
        journeyMinimumCount = requestedCount
        journeyLoading.value = !more; moreLoading.value = more; journeyError.value = null
        journeyJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val thisJob = coroutineContext[Job]
            try {
                runCatchingCancellable {
                    var cursor = after
                    var cursors = if (append) old?.platformCursors else null
                    var merge = append
                    // A planner page often contains one ride. Fill the requested display
                    // batch, not just one server page. Publish each page without waiting for walks.
                    repeat(12) {
                        // Pierwsza strona: wyniki cząstkowe, dopóki nic nie jest pokazane (pierwsze uruchomienie,
                        // nowy cel). Gotowej listy nie podmieniamy niepełną - bez skakania kart.
                        val onPartial: (suspend (TransitJourneyRepository.Result) -> Unit)? = if (merge || toSchool) null else { partial ->
                            // Repozytorium woła to z wątku IO; stan ViewModelu (journeyJob, ustawienia) czytamy
                            // i zmieniamy na głównym, jak wszędzie indziej - bez wyścigu ze zmianą celu.
                            kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                                val current = settings.value.preferences
                                val shown = journeys.value?.takeIf { matches(it, current) }
                                val nothingShown = shown == null || !shown.complete || shown.journeys.none { it.departureMs > AppClock.millis() }
                                if (journeyJob === thisJob && nothingShown && matches(partial, current)) journeys.value = partial
                            }
                        }
                        val result = if (toSchool) journeyRepository.findToSchool(destination, fromMs!!, cursor, prefs.allowTransfers, origin)
                            else journeyRepository.find(destination, origin, cursor, prefs.allowTransfers, cursors, onPartial)
                        val current = settings.value.preferences
                        if (!matches(result, current)) return@runCatchingCancellable
                        val previous = journeys.value?.takeIf { matches(it, current) } ?: old
                        val published = if (merge && previous != null) result.copy(
                            journeys = kotlinx.coroutines.withContext(Dispatchers.Default) {
                                if (toSchool) TransitJourneys.rankToSchool(previous.journeys + result.journeys, AppClock.millis(), current.allowTransfers)
                                else TransitJourneys.rank(previous.journeys + result.journeys, AppClock.millis(), originFor(current, TransitDirection.FROM_SCHOOL), current.allowTransfers)
                            },
                            boardingStops = (previous.boardingStops + result.boardingStops).distinctBy { it.key },
                            partialFailure = previous.partialFailure || result.partialFailure,
                            // Dociągnięcie do wyniku cząstkowego (przerwane pierwsze sprawdzenie) nie czyni go pełnym.
                            complete = previous.complete && result.complete) else result
                        if (!matches(published, settings.value.preferences)) return@runCatchingCancellable
                        journeys.value = published
                        automaticRetryKey = null; automaticRetryAt = 0L
                        checkSavedStops()
                        loadWalking(published)
                        val next = published.nextWhenMs
                        if (published.journeys.size >= journeyMinimumCount || next == null || (cursor != null && next <= cursor!!)) return@runCatchingCancellable
                        merge = true
                        cursor = next
                        cursors = published.platformCursors
                    }
                }.onFailure {
                    // Nieaktualne numery stanowisk przystanku początkowego kończą się błędem, nie pustym wynikiem.
                    checkSavedStops()
                    if (requestKey(settings.value.preferences) == key) {
                        automaticRetryKey = key
                        automaticRetryAt = android.os.SystemClock.elapsedRealtime() + 60_000L
                        journeyError.value = "Nie udało się sprawdzić połączeń. Spróbuj ponownie."
                    }
                }
            } finally {
                if (journeyJob === coroutineContext[Job]) { journeyLoading.value = false; moreLoading.value = false }
            }
        }
        journeyJob?.start()
    }

    private fun loadWalking(result: TransitJourneyRepository.Result) {
        if (!matches(result, settings.value.preferences)) return
        val key = keyOf(result)
        if (walkingKey != key) cancelWalkingRequest()
        if (walkingJob?.isActive == true || result.journeys.none { it.walkPending }) return
        walkingKey = key
        walkingJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                runCatchingCancellable { journeyRepository.enrichWalking(result) }.onSuccess { enriched ->
                    val current = journeys.value
                    if (current != null && matches(enriched, settings.value.preferences) && matches(current, settings.value.preferences)) {
                        val paths = enriched.journeys.associateBy { it.rides.first().fromId }
                        journeys.value = current.copy(journeys = current.journeys.map { trip ->
                            val path = paths[trip.rides.first().fromId]
                            if (!trip.walkPending || path == null) trip else trip.copy(walkMinutes = path.walkMinutes,
                                distanceMeters = path.distanceMeters, walkAvailable = path.walkAvailable, walkPending = false)
                        })
                    }
                }.onFailure {
                    if (journeys.value === result) journeys.value = result.copy(journeys = result.journeys.map { it.copy(walkPending = false) })
                }
            } finally {
                if (walkingJob === coroutineContext[Job]) walkingJob = null
            }
            // Only new boarding stops need another batch after pagination.
            journeys.value?.takeIf { matches(it, settings.value.preferences) && it.journeys.any { trip -> trip.walkPending } }?.let(::loadWalking)
        }
        walkingJob?.start()
    }

    private var loadJob: Job? = null
    fun cancelLoad() { loadJob?.cancel(); loading.value = false }
    fun loadStops(force: Boolean = false) {
        if (loadJob?.isActive == true) return
        loading.value = true; error.value = null
        loadJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { runCatchingCancellable { catalog.load(force) }
                .onSuccess { stops.value = it.stops; stale.value = it.stale }
                .onFailure { error.value = "Nie udało się pobrać przystanków. Sprawdź połączenie i spróbuj ponownie." }
            } finally { if (loadJob === coroutineContext[Job]) loading.value = false }
        }
        loadJob?.start()
    }
    fun select(destination: TransitDestination, onSaved: () -> Unit) = write(onSaved) { preferencesRepository.select(destination) }
    fun selectOrigin(origin: TransitDestination?, onSaved: () -> Unit = {}) = write(onSaved) { preferencesRepository.selectOrigin(origin) }
    fun setAllowTransfers(allow: Boolean) = writePreference { preferencesRepository.setAllowTransfers(allow) }
    fun setTabVisible(route: String, visible: Boolean) = writePreference { preferencesRepository.setTabVisible(route, visible) }
    fun setShowOnDashboard(show: Boolean) = writePreference { preferencesRepository.setShowOnDashboard(show) }
    fun setEnabled(enabled: Boolean) = writePreference { preferencesRepository.setEnabled(enabled) }
    val navigationError = MutableStateFlow<String?>(null)
    private val preferenceMutex = kotlinx.coroutines.sync.Mutex()
    fun setNavigationOrder(order: List<String>, onDone: (Boolean) -> Unit) = writePreference(onDone) {
        preferencesRepository.setNavigationOrder(order)
    }
    private fun writePreference(onDone: ((Boolean) -> Unit)? = null, action: suspend () -> Unit) {
        if (!settings.value.ready || settings.value.failed) {
            if (onDone != null) navigationError.value = "Nie udało się odczytać ustawień paska. Spróbuj ponownie."
            onDone?.invoke(false); return
        }
        viewModelScope.launch {
            preferenceMutex.lock()
            try {
                settingsError.value = null
                val result = runCatchingCancellable { action() }
                result.onFailure {
                    val message = "Nie udało się zapisać ustawienia. Spróbuj ponownie."
                    settingsError.value = message
                    if (onDone != null) navigationError.value = message
                }
                onDone?.invoke(result.isSuccess)
            } finally { preferenceMutex.unlock() }
        }
    }
    fun clear() = write { preferencesRepository.clearDestination() }
    private fun write(onSaved: () -> Unit = {}, action: suspend () -> Unit) {
        if (saving.value || !settings.value.ready || settings.value.failed) return
        saving.value = true; settingsError.value = null
        viewModelScope.launch {
            try { runCatchingCancellable { action() }.onSuccess { onSaved() }
                .onFailure { settingsError.value = "Nie udało się zapisać ustawienia. Spróbuj ponownie." }
            } finally { saving.value = false }
        }
    }

    companion object {
        const val LIVE_REFRESH_MS = 60_000L
    }
}
