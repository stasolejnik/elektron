package pl.zse.bydgoszcz.elektron.presentation.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.data.repository.LessonNotesRepository
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import pl.zse.bydgoszcz.elektron.work.NoteReminderScheduler
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import javax.inject.Inject

@HiltViewModel
class LessonNotesViewModel @Inject constructor(
    private val repository: LessonNotesRepository,
    private val scheduler: NoteReminderScheduler,
    private val widgetUpdater: WidgetUpdater,
    private val savedState: androidx.lifecycle.SavedStateHandle,
    private val timetable: pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
) : ViewModel() {
    data class State(val notes: List<LessonNote> = emptyList(), val enabled: Boolean = false,
        val timing: NoteReminderSettings = NoteReminderSettings(),
        val ready: Boolean = false, val error: Boolean = false)
    val state = combine(repository.notes, repository.remindersEnabled, repository.reminderTiming) { notes, enabled, timing -> State(notes, enabled, timing, ready = true) }
        .catch { emit(State(ready = true, error = true)) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())
    val error = MutableStateFlow<String?>(null)
    val editorLesson = MutableStateFlow<Lesson?>(null)
    val editorText = MutableStateFlow(savedState.get<String>("editor_text").orEmpty())
    init {
        val classId = savedState.get<String>("editor_class")
        val date = savedState.get<Long>("editor_date")?.let(java.time.LocalDate::ofEpochDay)
        val number = savedState.get<Int>("editor_number")
        if (classId != null && date != null && number != null) viewModelScope.launch {
            runCatchingCancellable { timetable.getLessonsOnce(classId, date, date).firstOrNull { it.number == number } }
                .onSuccess { editorLesson.value = it }
                .onFailure { error.value = "Nie udało się odtworzyć edytowanej lekcji. Szkic został zachowany." }
        }
    }
    fun editText(text: String) { editorText.value = text; savedState["editor_text"] = text }
    fun openEditor(lesson: Lesson) {
        val sameDraft = savedState.get<String>("editor_class") == lesson.classId &&
            savedState.get<Long>("editor_date") == lesson.date.toEpochDay() && savedState.get<Int>("editor_number") == lesson.number
        if (!sameDraft) editText(state.value.notes.firstOrNull { it.key == LessonNote.key(lesson) }?.text.orEmpty())
        savedState["editor_class"] = lesson.classId
        savedState["editor_date"] = lesson.date.toEpochDay()
        savedState["editor_number"] = lesson.number
        editorLesson.value = lesson
    }
    fun closeEditor() {
        editorLesson.value = null; editorText.value = ""
        listOf("editor_class", "editor_date", "editor_number", "editor_text").forEach { savedState.remove<Any>(it) }
    }
    val saving = MutableStateFlow(false)
    val settingsError = MutableStateFlow<String?>(null)
    fun save(lesson: Lesson, text: String, onSaved: () -> Unit) {
        if (saving.value || !state.value.ready || state.value.error) return
        if (!LessonNote.canEdit(lesson, java.time.LocalDateTime.now())) {
            error.value = "Lekcja już się rozpoczęła. Nie można dodać ani edytować notatki."; return
        }
        saving.value = true; error.value = null
        viewModelScope.launch {
            try {
                runCatchingCancellable { repository.save(LessonNote(lesson.classId, lesson.date, lesson.number,
                    LessonNote.subject(lesson), text, 0)) }
                    .onSuccess { scheduler.requestReschedule(); widgetUpdater.requestUpdate(); onSaved() }
                    .onFailure { error.value = "Nie udało się zapisać notatki. Spróbuj ponownie." }
            } finally { saving.value = false }
        }
    }
    fun delete(note: LessonNote, onDeleted: () -> Unit) {
        if (saving.value) return
        saving.value = true; error.value = null
        viewModelScope.launch {
            try {
                runCatchingCancellable { repository.delete(note.key) }
                    .onSuccess { scheduler.requestReschedule(); widgetUpdater.requestUpdate(); onDeleted() }
                    .onFailure { error.value = "Nie udało się usunąć notatki." }
            } finally { saving.value = false }
        }
    }
    fun setReminders(enabled: Boolean) = viewModelScope.launch {
        runCatchingCancellable { repository.setReminders(enabled) }
            .onSuccess { scheduler.requestReschedule() }
            .onFailure { settingsError.value = "Nie udało się zmienić przypomnień o notatkach." }
    }
    fun setReminderTiming(value: NoteReminderSettings) = viewModelScope.launch {
        runCatchingCancellable { repository.setReminderTiming(value) }
            .onSuccess { scheduler.requestReschedule() }
            .onFailure { settingsError.value = "Nie udało się zapisać czasu przypomnień o notatkach." }
    }
    fun refreshReminders() { scheduler.requestReschedule() }
    fun clearError() { error.value = null }
}
