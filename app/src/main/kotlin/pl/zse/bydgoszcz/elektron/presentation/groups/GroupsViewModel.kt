package pl.zse.bydgoszcz.elektron.presentation.groups

import pl.zse.bydgoszcz.elektron.domain.sync.SyncCoordinator
import pl.zse.bydgoszcz.elektron.domain.sync.SyncRequest
import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.presentation.common.currentDateFlow
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import javax.inject.Inject

/**
 * Wybór grup zajęciowych dla wybranej klasy. Przedmioty z podziałem wykrywane są
 * z planu zapisanego w bazie (sync planu zapisuje szablon na 4 tygodnie).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val timetableRepo: TimetableRepository,
    notificationsRepo: NotificationsRepository,
    private val coordinator: SyncCoordinator,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    enum class Status { LOADING, READY, NO_GROUPS, FAILED }

    data class State(
        val classId: String? = null,
        val className: String? = null,
        val status: Status = Status.LOADING,
        val subjects: List<LessonGroups.DividedSubject> = emptyList(),
        val selections: Map<String, String> = emptyMap(),
        val retrying: Boolean = false,
        /** Są niezapisane zmiany (szkic w Ustawieniach). */
        val hasChanges: Boolean = false,
        /** Pierwsze dane już są (do tego czasu ekran jest niewidoczny, bez mignięć). */
        val ready: Boolean = false
    )

    private val retrying = MutableStateFlow(false)

    /**
     * Niezapisane zmiany: przedmiot -> wybór (null = wszystkie grupy). Wybory trafiają tu,
     * a do ustawień dopiero po "Zapisz" / "Dalej"; "Anuluj" czyści szkic. ViewModel żyje
     * dłużej niż ekran (nakładka w Ustawieniach), więc szkic jest czyszczony przy otwarciu.
     */
    private val draft = MutableStateFlow<Map<String, String?>>(emptyMap())

    private data class Inputs(
        val classId: String?,
        val lessons: List<pl.zse.bydgoszcz.elektron.domain.model.Lesson>,
        val selections: Map<String, String>
    )

    // Zakres od bieżącej daty (ViewModel żyje przez cały czas działania aplikacji).
    private val inputs = combine(settings.selectedClassId, currentDateFlow()) { cid, day -> cid to day }.flatMapLatest { (cid, day) ->
        if (cid == null) flowOf(Inputs(null, emptyList(), emptyMap()))
        else combine(
            timetableRepo.observeLessons(cid, day.minusDays(7), day.plusDays(28)),
            settings.groupSelections(cid)
        ) { lessons, sel -> Inputs(cid, lessons, sel) }
    }

    val state: StateFlow<State> = combine(
        inputs,
        notificationsRepo.observeLoadedResources(),
        coordinator.initialSyncPending,
        retrying,
        draft
    ) { inp, loaded, pending, retry, pendingChanges ->
        val subjects = LessonGroups.detect(inp.lessons)
        val status = when {
            inp.lessons.isNotEmpty() && subjects.isEmpty() -> Status.NO_GROUPS
            inp.lessons.isNotEmpty() -> Status.READY
            pending || retry -> Status.LOADING
            "timetable" in loaded -> Status.NO_GROUPS   // plan pobrany, ale pusty
            else -> Status.FAILED
        }
        State(
            classId = inp.classId,
            className = inp.lessons.firstOrNull()?.className,
            status = status,
            subjects = subjects,
            selections = merge(inp.selections, pendingChanges),
            retrying = retry,
            hasChanges = merge(inp.selections, pendingChanges) != inp.selections,
            ready = true
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private fun merge(saved: Map<String, String>, changes: Map<String, String?>): Map<String, String> {
        if (changes.isEmpty()) return saved
        val out = saved.toMutableMap()
        changes.forEach { (subject, choice) -> if (choice == null) out.remove(subject) else out[subject] = choice }
        return out
    }

    /** Otwarcie ekranu: zaczynamy od zapisanych wyborów. */
    fun startEditing() {
        draft.value = emptyMap()
    }

    /** choice == null: pokazuj wszystkie grupy; LessonGroups.NONE: nie chodzę. */
    fun setChoice(subject: String, choice: String?) {
        draft.value = draft.value + (subject to choice)
    }

    /**
     * Szybki wybór dla całego typu podziału (np. "1/2" dla wszystkich przedmiotów dzielonych
     * na 2 grupy). [option] == null -> "Wszystkie".
     */
    fun applyDivision(key: String, option: String?) {
        draft.value = draft.value + LessonGroups.applyDivision(state.value.subjects, key, option)
    }

    /** Zapisuje szkic (Zapisz w Ustawieniach, Dalej przy pierwszym uruchomieniu). */
    private suspend fun commit() {
        val cid = settings.selectedClassId.first() ?: return
        val changes = draft.value
        if (changes.isEmpty()) return
        changes.forEach { (subject, choice) -> settings.setGroupSelection(cid, subject, choice) }
        draft.value = emptyMap()
        widgetUpdater.requestUpdate()
    }

    /** Anuluj: odrzuca niezapisane zmiany. */
    fun cancel(onDone: () -> Unit = {}) {
        draft.value = emptyMap()
        onDone()
    }

    fun retry() {
        if (retrying.value) return
        retrying.value = true
        viewModelScope.launch {
            try {
                if (settings.selectedClassId.first() == null) return@launch
                // Przez koordynator: komunikat, "loaded", przypomnienia i widżety - tam.
                coordinator.sync(SyncRequest.of(SyncOutcome.TIMETABLE))
            } finally {
                retrying.value = false
            }
        }
    }

    /** Zamyka krok konfiguracji grup dla bieżącej klasy. */
    fun finish(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            commit()
            settings.selectedClassId.first()?.let { settings.setGroupsConfiguredFor(it) }
            onDone()
        }
    }
}
