package pl.zse.bydgoszcz.elektron.presentation.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.zse.bydgoszcz.elektron.work.DeveloperTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import pl.zse.bydgoszcz.elektron.crash.Diagnostics
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.model.AccentSetting
import pl.zse.bydgoszcz.elektron.work.LessonReminderScheduler
import pl.zse.bydgoszcz.elektron.domain.model.WidgetLook
import pl.zse.bydgoszcz.elektron.domain.model.QuietHours
import pl.zse.bydgoszcz.elektron.domain.model.ReminderSettings
import pl.zse.bydgoszcz.elektron.domain.model.StartScreen
import pl.zse.bydgoszcz.elektron.domain.model.TimetableLook
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
    private val widgetUpdater: WidgetUpdater,
    private val reminders: LessonReminderScheduler,
    private val notificationsRepo: NotificationsRepository,
    private val developerTools: DeveloperTools,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** Tryb dewelopera - na czas działania aplikacji, nie zapisywany. Panel z symulacjami w Ustawieniach. */
    private val _devMode = MutableStateFlow(false)
    val devMode: StateFlow<Boolean> = _devMode

    /** Komunikat po akcji trybu dewelopera (pokazywany raz). */
    private val _devMessage = MutableStateFlow<String?>(null)
    val devMessage: StateFlow<String?> = _devMessage

    fun setDevMode(enabled: Boolean) {
        _devMode.value = enabled
    }

    fun consumeDevMessage() {
        _devMessage.value = null
    }

    fun devSimulateSubstitution(freed: Boolean) = devAction { developerTools.simulateSubstitution(freed) }
    fun devSimulateAnnouncement() = devAction { developerTools.simulateAnnouncement() }
    fun devClearSimulations() = devAction { developerTools.clearSimulations() }
    fun devCrash() = developerTools.crash()

    private fun devAction(block: suspend () -> String) {
        viewModelScope.launch {
            _devMessage.value = runCatching { block() }.getOrElse { "Błąd: ${it.message}" }
        }
    }

    /** Raport do "Zgłoś problem" (null - okno zamknięte). */
    private val _feedbackReport = MutableStateFlow<String?>(null)
    val feedbackReport: StateFlow<String?> = _feedbackReport

    /**
     * "Zgłoś problem": raport z logami aplikacji i stanem synchronizacji (najważniejsze przy
     * problemach z pobieraniem danych ze strony szkoły).
     */
    fun openFeedback() {
        viewModelScope.launch {
            val s = state.value
            val className = s.classes.firstOrNull { it.id == s.selectedClassId }?.fullName ?: s.selectedClassId ?: "nie wybrano"
            val lastSync = runCatching { notificationsRepo.getLastSyncAt() }.getOrNull()
            val lastError = runCatching { notificationsRepo.observeLastSyncError().first() }.getOrNull()
            val details = listOf(
                "Klasa" to className,
                "Ostatnia udana synchronizacja" to (lastSync?.atZone(java.time.ZoneId.systemDefault())
                    ?.toLocalDateTime()?.withNano(0)?.toString()?.replace('T', ' ') ?: "brak"),
                "Ostatni błąd synchronizacji" to (lastError ?: "brak")
            )
            _feedbackReport.value = withContext(Dispatchers.IO) { Diagnostics.feedbackReport(appContext, details) }
        }
    }

    fun closeFeedback() {
        _feedbackReport.value = null
    }

    data class State(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
        val selectedClassId: String? = null,
        val notifSubs: Boolean = true,
        val notifAnn: Boolean = false,
        val showNextLesson: Boolean = true,
        val showSubstitutions: Boolean = true,
        val showAnnouncements: Boolean = true,
        val startScreen: StartScreen = StartScreen.SMART,
        val classes: List<SchoolClass> = emptyList(),
        val look: TimetableLook = TimetableLook(),
        val reminder: ReminderSettings = ReminderSettings(),
        val quiet: QuietHours = QuietHours(),
        val widgetLook: WidgetLook = WidgetLook(),
        val accent: AccentSetting = AccentSetting(),
        /** Pierwsze dane już są (do tego czasu ekran jest niewidoczny, bez mignięć). */
        val ready: Boolean = false
    )

    val state: StateFlow<State> = combine(
        settings.themeMode, settings.dynamicColor, settings.selectedClassId,
        settings.notificationsSubstitutions, settings.notificationsAnnouncements,
        settings.showNextLesson, settings.showSubstitutions, settings.showAnnouncements,
        settings.startScreen,
        timetableRepo.observeClasses(),
        settings.timetableLook,
        settings.reminderSettings,
        settings.quietHours,
        settings.widgetLook,
        settings.accent
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        State(
            ready = true,
            themeMode = values[0] as ThemeMode,
            dynamicColor = values[1] as Boolean,
            selectedClassId = values[2] as String?,
            notifSubs = values[3] as Boolean,
            notifAnn = values[4] as Boolean,
            showNextLesson = values[5] as Boolean,
            showSubstitutions = values[6] as Boolean,
            showAnnouncements = values[7] as Boolean,
            startScreen = values[8] as StartScreen,
            classes = values[9] as List<SchoolClass>,
            look = values[10] as TimetableLook,
            reminder = values[11] as ReminderSettings,
            quiet = values[12] as QuietHours,
            widgetLook = values[13] as WidgetLook,
            accent = values[14] as AccentSetting
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

    fun setStartScreen(screen: StartScreen) = viewModelScope.launch { settings.setStartScreen(screen) }

    fun setReminder(value: ReminderSettings) = viewModelScope.launch {
        settings.setReminderSettings(value)
        reminders.reschedule()
    }

    fun setQuietHours(value: QuietHours) = viewModelScope.launch { settings.setQuietHours(value) }

    fun setAccent(value: AccentSetting) = viewModelScope.launch {
        settings.setAccent(value)
        widgetUpdater.requestUpdate()   // widżety w nowym kolorze
    }

    fun setWidgetLook(value: WidgetLook) = viewModelScope.launch {
        settings.setWidgetLook(value)
        widgetUpdater.requestUpdate()
    }

    fun setTimetableLook(look: TimetableLook) = viewModelScope.launch { settings.setTimetableLook(look) }

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
