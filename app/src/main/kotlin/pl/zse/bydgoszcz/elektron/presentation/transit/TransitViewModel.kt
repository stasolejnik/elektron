package pl.zse.bydgoszcz.elektron.presentation.transit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import pl.zse.bydgoszcz.elektron.data.repository.TransitPreferencesRepository
import pl.zse.bydgoszcz.elektron.data.repository.TransitStopCatalog
import pl.zse.bydgoszcz.elektron.data.repository.TransitJourneyRepository
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import pl.zse.bydgoszcz.elektron.presentation.announcements.SearchText
import javax.inject.Inject

@HiltViewModel
class TransitViewModel @Inject constructor(private val preferencesRepository: TransitPreferencesRepository,
    private val catalog: TransitStopCatalog,
    private val journeyRepository: TransitJourneyRepository) : ViewModel() {
    data class Settings(val preferences: TransitPreferences = TransitPreferences(), val ready: Boolean = false, val failed: Boolean = false)
    val settings = preferencesRepository.preferences.map { Settings(it, ready = true) }
        .catch { emit(Settings(ready = true, failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())
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
    val originStops = filteredStops.map { all -> all.filter { it.distance <= 1000 }.map { it.stop } }
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
    private var walkingJob: Job? = null
    private fun matches(result: TransitJourneyRepository.Result, prefs: TransitPreferences) =
        prefs.visible && result.destinationKey == prefs.destination?.key && result.originKey == prefs.preferredOrigin?.key && result.allowTransfers == prefs.allowTransfers
    init {
        viewModelScope.launch {
            settings.filter { it.ready }.map { it.preferences.takeIf { prefs -> prefs.visible }?.let { prefs -> Triple(prefs.destination?.key, prefs.preferredOrigin?.key, prefs.allowTransfers) } }.distinctUntilChanged().collect { key ->
                if (journeys.value?.let { Triple(it.destinationKey, it.originKey, it.allowTransfers) } != key) journeys.value = null
                cancelJourneys()
                journeyError.value = null
            }
        }
    }
    fun cancelJourneys() { journeyJob?.cancel(); walkingJob?.cancel(); journeyLoading.value = false; moreLoading.value = false }
    fun loadJourneys(force: Boolean = false, more: Boolean = false) {
        val prefs = settings.value.preferences
        val destination = prefs.destination ?: return
        if (!prefs.visible) return
        val old = journeys.value?.takeIf { matches(it, prefs) }
        val after = if (more) old?.nextWhenMs ?: return else null
        if (!more && !force && old?.canReuse(destination.key, System.currentTimeMillis(), prefs.preferredOrigin?.key, prefs.allowTransfers) == true) {
            journeyError.value = null
            if (old.journeys.any { it.walkPending } && walkingJob?.isActive != true) loadWalking(old)
            return
        }
        if (journeyJob?.isActive == true && !force) return
        cancelJourneys()
        journeyLoading.value = !more; moreLoading.value = more; journeyError.value = null
        journeyJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { runCatchingCancellable { journeyRepository.find(destination, prefs.preferredOrigin, after, prefs.allowTransfers) }
                .onSuccess { result ->
                    val current = settings.value.preferences
                    if (matches(result, current)) {
                        val published = if (more && old != null) result.copy(journeys = pl.zse.bydgoszcz.elektron.domain.model.TransitJourneys.rank(old.journeys + result.journeys, System.currentTimeMillis(), current.preferredOrigin, current.allowTransfers), boardingStops = (old.boardingStops + result.boardingStops).distinctBy { it.key }) else result
                        journeys.value = published
                        loadWalking(published)
                    }

                }
                .onFailure { if (settings.value.preferences.destination?.key == destination.key && settings.value.preferences.preferredOrigin?.key == prefs.preferredOrigin?.key && settings.value.preferences.allowTransfers == prefs.allowTransfers) journeyError.value = "Nie udało się sprawdzić połączeń. Spróbuj ponownie." }
            } finally { if (journeyJob === coroutineContext[Job]) { journeyLoading.value = false; moreLoading.value = false } }
        }
        journeyJob?.start()
    }

    private fun loadWalking(result: TransitJourneyRepository.Result) {
        walkingJob?.cancel()
        if (result.journeys.none { it.walkPending }) return
        walkingJob = viewModelScope.launch {
            runCatchingCancellable { journeyRepository.enrichWalking(result) }.onSuccess { enriched ->
                val current = journeys.value
                if (matches(enriched, settings.value.preferences) && current === result) journeys.value = enriched
            }.onFailure {
                if (journeys.value === result) journeys.value = result.copy(journeys = result.journeys.map { it.copy(walkPending = false) })
            }
        }
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
    fun setAllowTransfers(allow: Boolean) = write { preferencesRepository.setAllowTransfers(allow) }
    fun setEnabled(enabled: Boolean) = write { preferencesRepository.setEnabled(enabled) }
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
}
