package pl.zse.bydgoszcz.elektron.presentation.substitutions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionRelevance
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.presentation.common.currentDateFlow
import pl.zse.bydgoszcz.elektron.presentation.common.minuteTicker
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubstitutionsViewModel @Inject constructor(
    private val repo: SubstitutionsRepository,
    settings: SettingsRepository,
    timetableRepo: TimetableRepository,
    private val notificationsRepo: NotificationsRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Trwa pierwsza synchronizacja albo odświeżanie — pusta lista to wtedy "ładowanie", nie "brak". */
    val isLoading: StateFlow<Boolean> = combine(notificationsRepo.observeInitialSyncPending(), _isRefreshing) { p, r -> p || r }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Pull-to-refresh: pobiera świeże zastępstwa ze strony szkoły. */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                repo.syncAll()
                    .onSuccess {
                        notificationsRepo.markLoaded("subs")
                        notificationsRepo.setLastSyncError(null)
                    }
                    .onFailure { notificationsRepo.setLastSyncError(it.message ?: "Błąd odświeżania zastępstw") }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** [label]: "Dziś · 28 września" — liczone tu, żeby po północy zmieniło się samo. */
    data class DayGroup(val date: LocalDate, val label: String, val items: List<Substitution>)

    private val classIdFlow = settings.selectedClassId
    // Bieżąca data (przebudowywana po północy) — ViewModel żyje przez cały czas działania appki.
    private val startDay = currentDateFlow()

    private val classInfoFlow = classIdFlow.flatMapLatest { cid ->
        if (cid == null) flowOf(null to null)
        else timetableRepo.observeClasses().map { list ->
            val c = list.firstOrNull { it.id == cid }
            c?.id to c?.shortName
        }
    }

    private val lessonsFlow = combine(classIdFlow, startDay) { cid, day -> cid to day }.flatMapLatest { (cid, day) ->
        if (cid == null) flowOf(emptyList())
        // Zakres ~miesiąca: lekcje potrzebne też do oceny, czy zastępstwo dla konkretnej
        // grupy (np. "2D(1)") dotyczy grupy użytkownika — nie tylko do ucinania dzisiejszych.
        else timetableRepo.observeLessons(cid, day, day.plusDays(30))
    }

    // Optymalizacja: dawniej observeFrom(2000-01-01) — cała 60-dniowa historia była
    // czytana z bazy przy każdej zmianie, a potem i tak odrzucana (sub.date < today).
    // Ticker przelicza listę co 30 s, żeby zakończone dziś lekcje znikały na bieżąco.
    val days: StateFlow<List<DayGroup>> = combine(
        classInfoFlow,
        startDay.flatMapLatest { day -> repo.observeFrom(day) },
        lessonsFlow,
        settings.activeGroupSelections,
        minuteTicker()
    ) { (_, short), subs, lessons, groups, _ ->
        if (short == null) return@combine emptyList()
        val today = LocalDate.now()
        // Wszystkie dzisiejsze i przyszłe — także z zakończonych już lekcji. Szkoła publikuje
        // zastępstwa tylko na bieżący dzień, więc dawniej po lekcjach lista była pusta, choć
        // na stronie zastępstwa wciąż były. (Strona główna nadal pokazuje tylko nadchodzące.)
        subs.filter { SubstitutionRelevance.matchesClass(it, short) }
            .filter { LessonGroups.substitutionRelevant(it, lessons, groups) }
            .filter { sub -> sub.date >= today }
            .groupBy { it.date }
            .toSortedMap()
            .map { (d, list) -> DayGroup(d, dayLabel(d, today), list.sortedWith(compareBy({ it.lessonNumber }))) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val PL = java.util.Locale("pl", "PL")
private val DAY_FMT = java.time.format.DateTimeFormatter.ofPattern("d MMMM", PL)

internal fun dayLabel(date: LocalDate, today: LocalDate): String {
    val prefix = when (date) {
        today -> "Dziś"
        today.plusDays(1) -> "Jutro"
        else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, PL).replaceFirstChar { it.titlecase(PL) }
    }
    return "$prefix · ${date.format(DAY_FMT)}"
}
