package pl.zse.bydgoszcz.elektron.presentation.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.SchoolClass
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import java.time.DayOfWeek as JavaDayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val repo: TimetableRepository,
    private val substitutionsRepo: SubstitutionsRepository,
    private val notificationsRepo: NotificationsRepository
) : ViewModel() {

    enum class ViewMode { DAY, WEEK }

    data class DayColumn(val date: LocalDate, val dayOfWeek: JavaDayOfWeek, val lessons: List<Lesson>)

    data class State(
        val selectedClassId: String? = null,
        val className: String? = null,
        val mode: ViewMode = ViewMode.DAY,
        val anchorDate: LocalDate = LocalDate.now(),
        val lessonsToday: List<Lesson> = emptyList(),
        val weekDays: List<DayColumn> = emptyList(),
        val isLoading: Boolean = true,
        val classes: List<SchoolClass> = emptyList(),
        val isRefreshing: Boolean = false
    )

    private val anchor = MutableStateFlow(initialAnchor())
    private val mode = MutableStateFlow(ViewMode.DAY)
    private val refreshing = MutableStateFlow(false)

    init {
        // Bug audytu #10: sync sieciowy odpalany jako efekt uboczny wewnątrz flatMapLatest
        // był trudny do przetestowania i kruchy przy zmianach wyżej w łańcuchu Flow.
        // Osobny strumień (cid, poniedziałek tygodnia) z distinctUntilChanged synchronizuje
        // dokładnie wtedy, gdy klasa albo oglądany tydzień faktycznie się zmienia.
        combine(settings.selectedClassId, anchor) { cid, day -> cid to day.with(JavaDayOfWeek.MONDAY) }
            .distinctUntilChanged()
            .onEach { (cid, monday) ->
                if (cid == null) return@onEach
                // Optymalizacja: plan zapisywany jest na 4 tygodnie naraz, a świeżość
                // zapewnia SyncWorker (15 min) + odświeżanie ręczne. Do serwera szkoły
                // idziemy tylko gdy oglądanego tygodnia faktycznie brakuje w bazie.
                if (!repo.hasLessons(cid, monday, monday.plusDays(4))) repo.syncTimetable(cid, monday)
            }
            .launchIn(viewModelScope)
    }

    val state: StateFlow<State> = combine(
        settings.selectedClassId,
        anchor,
        mode,
        repo.observeClasses()
    ) { cid, day, m, classes -> Quad(cid, day, m, classes) }
        .flatMapLatest { q ->
            val cid = q.cid
            if (cid == null) {
                flowOf(State(selectedClassId = null, anchorDate = q.day, mode = q.mode,
                    isLoading = false, classes = q.classes))
            } else {
                val monday = q.day.with(JavaDayOfWeek.MONDAY)
                val friday = monday.plusDays(4)
                // Tylko grupy, na które użytkownik chodzi (lekcje bez podziału zawsze).
                combine(
                    repo.observeLessons(cid, monday, friday),
                    settings.activeGroupSelections
                ) { raw, sel -> LessonGroups.filter(raw, sel) }.map { lessons ->
                    val days = (0L..4L).map { i ->
                        val d = monday.plusDays(i)
                        DayColumn(d, d.dayOfWeek, lessons.filter { it.date == d }.sortedBy { it.number })
                    }
                    State(
                        selectedClassId = cid,
                        className = lessons.firstOrNull()?.className,
                        mode = q.mode,
                        anchorDate = q.day,
                        lessonsToday = lessons.filter { it.date == q.day }.sortedBy { it.number },
                        weekDays = days,
                        isLoading = false,
                        classes = q.classes
                    )
                }
            }
        }
        .combine(refreshing) { s, r -> s.copy(isRefreshing = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /**
     * Pull-to-refresh: wymusza pobranie planu dla oglądanego tygodnia (pomija cache)
     * oraz zastępstw, które nakładają się na plan. Oba równolegle.
     */
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val cid = settings.selectedClassId.first() ?: return@launch
                coroutineScope {
                    launch {
                        repo.syncTimetable(cid, anchor.value)
                            .onSuccess { notificationsRepo.markLoaded("timetable") }
                    }
                    launch {
                        substitutionsRepo.syncAll()
                            .onSuccess { notificationsRepo.markLoaded("subs") }
                    }
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    private data class Quad(val cid: String?, val day: LocalDate, val mode: ViewMode, val classes: List<SchoolClass>)

    fun setMode(m: ViewMode) {
        if (mode.value == m) return
        mode.value = m
    }

    fun previous() {
        anchor.value = when (mode.value) {
            ViewMode.DAY -> skipWeekendBack(anchor.value.minusDays(1))
            ViewMode.WEEK -> anchor.value.minusWeeks(1)
        }
    }

    fun next() {
        anchor.value = when (mode.value) {
            ViewMode.DAY -> skipWeekendFwd(anchor.value.plusDays(1))
            ViewMode.WEEK -> anchor.value.plusWeeks(1)
        }
    }

    fun goToToday() { anchor.value = initialAnchor() }

    private fun initialAnchor(): LocalDate {
        val t = LocalDate.now()
        return when (t.dayOfWeek) {
            JavaDayOfWeek.SATURDAY -> t.plusDays(2)
            JavaDayOfWeek.SUNDAY -> t.plusDays(1)
            else -> t
        }
    }

    private fun skipWeekendFwd(d: LocalDate): LocalDate {
        var x = d
        while (x.dayOfWeek == JavaDayOfWeek.SATURDAY || x.dayOfWeek == JavaDayOfWeek.SUNDAY) x = x.plusDays(1)
        return x
    }
    private fun skipWeekendBack(d: LocalDate): LocalDate {
        var x = d
        while (x.dayOfWeek == JavaDayOfWeek.SATURDAY || x.dayOfWeek == JavaDayOfWeek.SUNDAY) x = x.minusDays(1)
        return x
    }
}
