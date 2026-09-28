package pl.zse.bydgoszcz.elektron.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import pl.zse.bydgoszcz.elektron.domain.repository.AppUpdate
import pl.zse.bydgoszcz.elektron.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.domain.model.SchoolClass
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.usecase.ClassSelection
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val timetableRepo: TimetableRepository,
    private val classSelection: ClassSelection,
    private val updateRepo: UpdateRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    data class State(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
        val selectedClassId: String? = null,
        val notifSubs: Boolean = true,
        val notifAnn: Boolean = false,
        val showNextLesson: Boolean = true,
        val showSubstitutions: Boolean = true,
        val showAnnouncements: Boolean = true,
        val smartStart: Boolean = true,
        val classes: List<SchoolClass> = emptyList()
    )

    val state: StateFlow<State> = combine(
        settings.themeMode, settings.dynamicColor, settings.selectedClassId,
        settings.notificationsSubstitutions, settings.notificationsAnnouncements,
        settings.showNextLesson, settings.showSubstitutions, settings.showAnnouncements,
        settings.smartStart,
        timetableRepo.observeClasses()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        State(
            themeMode = values[0] as ThemeMode,
            dynamicColor = values[1] as Boolean,
            selectedClassId = values[2] as String?,
            notifSubs = values[3] as Boolean,
            notifAnn = values[4] as Boolean,
            showNextLesson = values[5] as Boolean,
            showSubstitutions = values[6] as Boolean,
            showAnnouncements = values[7] as Boolean,
            smartStart = values[8] as Boolean,
            classes = values[9] as List<SchoolClass>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setTheme(m: ThemeMode) = viewModelScope.launch {
        settings.setThemeMode(m)
        widgetUpdater.requestUpdate()   // widżety w motywie aplikacji
    }

    // Ponowne tapnięcie tej samej klasy nic nie robi (dawniej: pełny resync + miganie
    // "Ładowanie"). Sam sync działa w ClassSelection (zasięg aplikacji) — przeżyje
    // przełączenie ekranu na krok wyboru grup.
    fun setClass(id: String) {
        if (id == state.value.selectedClassId) return
        classSelection.select(id)
    }

    /** Stan ręcznego sprawdzania aktualizacji ("O aplikacji"). */
    sealed interface UpdateStatus {
        data object Idle : UpdateStatus
        data object Checking : UpdateStatus
        data object UpToDate : UpdateStatus
        data class Available(val update: AppUpdate) : UpdateStatus
        data object Failed : UpdateStatus
    }

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus

    fun checkForUpdates() {
        if (_updateStatus.value == UpdateStatus.Checking) return
        _updateStatus.value = UpdateStatus.Checking
        viewModelScope.launch {
            _updateStatus.value = updateRepo.check(force = true).fold(
                onSuccess = { u -> if (u != null) UpdateStatus.Available(u) else UpdateStatus.UpToDate },
                onFailure = { UpdateStatus.Failed }
            )
        }
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settings.setDynamicColor(enabled)
        widgetUpdater.requestUpdate()   // widżety w kolorach z tapety
    }

    fun setSmartStart(enabled: Boolean) = viewModelScope.launch { settings.setSmartStart(enabled) }

    fun resetCache() {
        state.value.selectedClassId?.let { classSelection.resetCacheAndResync(it) }
    }

    fun setNotifSubs(b: Boolean) = viewModelScope.launch { settings.setNotificationsSubstitutions(b) }
    fun setNotifAnn(b: Boolean) = viewModelScope.launch { settings.setNotificationsAnnouncements(b) }
    fun setShowNextLesson(b: Boolean) = viewModelScope.launch { settings.setShowNextLesson(b) }
    fun setShowSubstitutions(b: Boolean) = viewModelScope.launch { settings.setShowSubstitutions(b) }
    fun setShowAnnouncements(b: Boolean) = viewModelScope.launch { settings.setShowAnnouncements(b) }

    companion object { private const val TAG = "SettingsViewModel" }
}
