package pl.zse.bydgoszcz.elektron.presentation.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import javax.inject.Inject

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val repo: AnnouncementsRepository,
    private val notificationsRepo: NotificationsRepository
) : ViewModel() {

    private companion object { const val PAGE_SIZE = 10 }

    data class State(
        val items: List<Announcement> = emptyList(),
        val hasMore: Boolean = false,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val loadMoreError: Boolean = false,
        /** Trwa pierwsza synchronizacja — pusta lista to "ładowanie", nie "brak ogłoszeń". */
        val isInitialLoading: Boolean = false,
        val query: String = "",
        /** Pierwsze dane już są (do tego czasu ekran jest niewidoczny, bez mignięć). */
        val ready: Boolean = false
    ) {
        val isSearching: Boolean get() = query.isNotBlank()
    }

    /** Ogłoszenie z tekstem znormalizowanym raz (a nie przy każdym wciśnięciu klawisza). */
    private data class Indexed(val ann: Announcement, val text: String)

    private data class Flags(val loadingMore: Boolean, val error: Boolean, val exhausted: Boolean, val initialPending: Boolean)

    private val visibleCount = MutableStateFlow(PAGE_SIZE)
    private val query = MutableStateFlow("")

    private val indexed = repo.observeAll().map { list ->
        list.map { Indexed(it, SearchText.normalize(it.title + " " + (it.excerpt ?: ""))) }
    }
    private val refreshing = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val loadMoreError = MutableStateFlow(false)
    /** Archiwum strony szkoły się skończyło — nie ma już czego dociągać. */
    private val archiveExhausted = MutableStateFlow(false)

    val state: StateFlow<State> = combine(
        combine(indexed, query) { all, q -> all to q },
        visibleCount,
        refreshing,
        combine(loadingMore, loadMoreError, archiveExhausted, notificationsRepo.observeInitialSyncPending()) { l, e, x, p ->
            Flags(l, e, x, p)
        }
    ) { (all, q), count, refresh, flags ->
        val loading = flags.loadingMore
        val error = flags.error
        val exhausted = flags.exhausted
        val words = SearchText.normalize(q).split(' ').filter { it.isNotBlank() }
        val searching = words.isNotEmpty()
        val items = if (searching) {
            // Wszystkie słowa muszą wystąpić (bez względu na wielkość liter i polskie znaki).
            all.filter { ix -> words.all { w -> ix.text.contains(w) } }.map { it.ann }
        } else all.take(count).map { it.ann }
        State(
            items = items,
            // "Pokaż więcej" jest dostępne, dopóki są wpisy w bazie ALBO archiwum strony
            // (dawniej przycisk znikał po wyczerpaniu kilkudziesięciu wpisów z RSS).
            // Przy wyszukiwaniu: dopóki archiwum się nie skończyło (szukaj w starszych).
            hasMore = if (searching) !exhausted else all.size > count || !exhausted,
            query = q,
            isRefreshing = refresh,
            isLoadingMore = loading,
            loadMoreError = error,
            isInitialLoading = flags.initialPending,
            ready = true
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setQuery(q: String) { query.value = q }

    fun loadMore() {
        if (loadingMore.value) return
        val shown = state.value.items.size
        val searching = state.value.isSearching
        viewModelScope.launch {
            val inDb = repo.count()
            // Przy wyszukiwaniu przeszukana jest już cała baza — od razu sięgamy do archiwum.
            if (!searching && inDb > shown) {
                visibleCount.value = shown + PAGE_SIZE
                return@launch
            }
            // Baza wyczerpana — starsze wpisy z archiwum strony szkoły.
            loadingMore.value = true
            loadMoreError.value = false
            try {
                repo.loadOlder()
                    .onSuccess { r ->
                        if (r.exhausted && r.added == 0) archiveExhausted.value = true
                        visibleCount.value = shown + maxOf(r.added, PAGE_SIZE)
                    }
                    .onFailure { loadMoreError.value = true }
            } finally {
                loadingMore.value = false
            }
        }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.syncAll() }
                    .onSuccess { notificationsRepo.markLoaded("anns") }
            } finally {
                // Dawniej bez try/finally — wyjątek zostawiał wieczny spinner odświeżania.
                refreshing.value = false
            }
        }
    }

}
