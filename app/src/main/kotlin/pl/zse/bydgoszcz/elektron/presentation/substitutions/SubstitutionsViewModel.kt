package pl.zse.bydgoszcz.elektron.presentation.substitutions

import pl.zse.bydgoszcz.elektron.domain.sync.SyncRequest
import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome
import pl.zse.bydgoszcz.elektron.domain.sync.SyncCoordinator
import java.time.LocalDateTime
import java.time.LocalTime
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
    private val coordinator: SyncCoordinator
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Trwa pierwsza synchronizacja albo odświeżanie — pusta lista to wtedy "ładowanie", nie "brak". */
    val isLoading: StateFlow<Boolean> = combine(coordinator.initialSyncPending, _isRefreshing) { p, r -> p || r }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Pull-to-refresh: pobiera świeże zastępstwa ze strony szkoły. */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Przez koordynator: komunikat błędu, przypomnienia, widżety, powiadomienia - tam.
                coordinator.sync(SyncRequest.of(SyncOutcome.SUBSTITUTIONS))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** [label]: "Dziś · 28 września" — liczone tu, żeby po północy zmieniło się samo. */
    data class DayGroup(val date: LocalDate, val label: String, val items: List<Substitution>)

    /** Lista + czy schowano dzisiejsze zastępstwa (lekcje już się skończyły). */
    private data class Content(
        val days: List<DayGroup>,
        val todayHidden: Boolean,
        val ready: Boolean = true,
        /** Zastępstwa klasy dla grup, do których użytkownik nie należy (nie minione). */
        val otherGroups: List<Substitution> = emptyList()
    )

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
    private val content: StateFlow<Content> = combine(
        classInfoFlow,
        startDay.flatMapLatest { day -> repo.observeFrom(day) },
        lessonsFlow,
        settings.activeGroupSelections,
        minuteTicker()
    ) { (_, short), subs, lessons, groups, _ ->
        if (short == null) return@combine Content(emptyList(), false)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        // Zastępstwo znika z zakładki po końcu SWOJEJ lekcji (dzwonki z planu klasy, także lekcje
        // innych grup) - w planie lekcji zostaje. Dawniej dopiero po wszystkich lekcjach dnia.
        val ends = SubstitutionRelevance.lessonEnds(lessons)
        val (relevant, otherGroups) = subs.filter { SubstitutionRelevance.matchesClass(it, short) }
            .filter { sub -> sub.date >= today }
            .partition { LessonGroups.substitutionRelevant(it, lessons, groups) }
        val (over, shown) = relevant.partition { SubstitutionRelevance.isOver(it, now, ends) }
        Content(
            days = shown.groupBy { it.date }
                .toSortedMap()
                .map { (d, list) -> DayGroup(d, dayLabel(d, today), list.sortedWith(compareBy({ it.lessonNumber }))) },
            todayHidden = over.any { it.date == today },
            otherGroups = otherGroups.filterNot { SubstitutionRelevance.isOver(it, now, ends) }
                .sortedWith(compareBy({ it.date }, { it.lessonNumber }))
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Content(emptyList(), false, ready = false))

    val days: StateFlow<List<DayGroup>> = content.map { it.days }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Zastępstwa innych grup klasy - ukryte, ale do pokazania na żądanie ("Pokaż zastępstwa
     * innych grup"), żeby nie wyglądało, że aplikacja coś zgubiła (bot na Discordzie grup nie zna).
     */
    val otherGroups: StateFlow<List<Substitution>> = content.map { it.otherGroups }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _showOtherGroups = MutableStateFlow(false)
    val showOtherGroups: StateFlow<Boolean> = _showOtherGroups

    fun toggleOtherGroups() {
        _showOtherGroups.value = !_showOtherGroups.value
    }

    /** Pierwsze dane już są - do tego czasu lista niewidoczna (bez mignięcia "Brak zastępstw"). */
    val ready: StateFlow<Boolean> = content.map { it.ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Dzisiejsze zastępstwa schowane po ich lekcjach - ekran mówi, gdzie ich szukać. */
    val todayHidden: StateFlow<Boolean> = content.map { it.todayHidden }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
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
