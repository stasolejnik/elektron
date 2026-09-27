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
        val isRefreshing: Boolean = false
    )

    private val visibleCount = MutableStateFlow(PAGE_SIZE)
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<State> = combine(
        repo.observeAll(),
        visibleCount,
        refreshing
    ) { all, count, refresh ->
        State(items = all.take(count), hasMore = all.size > count, isRefreshing = refresh)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun loadMore() { visibleCount.value += PAGE_SIZE }

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
