package pl.zse.bydgoszcz.elektron.presentation.common

import pl.zse.bydgoszcz.elektron.domain.util.AppClock

import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import pl.zse.bydgoszcz.elektron.domain.model.AccentSetting
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.filterNotNull
import pl.zse.bydgoszcz.elektron.domain.sync.SyncCoordinator
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.distinctUntilChanged
import pl.zse.bydgoszcz.elektron.work.LessonReminderScheduler
import pl.zse.bydgoszcz.elektron.domain.model.StartScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.presentation.navigation.ElektronRoutes
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class ElektronAppViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val timetableRepo: TimetableRepository,
    coordinator: SyncCoordinator,
    reminders: LessonReminderScheduler,
    noteReminders: pl.zse.bydgoszcz.elektron.work.NoteReminderScheduler,
    notes: pl.zse.bydgoszcz.elektron.data.repository.LessonNotesRepository,
    private val transitPreferences: pl.zse.bydgoszcz.elektron.data.repository.TransitPreferencesRepository
) : ViewModel() {

    data class AppState(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
        val accent: AccentSetting = AccentSetting(),
        val selectedClassId: String? = null,
        /** Klasa, dla której przeszedł krok wyboru grup — różna od selectedClassId => pokaż krok. */
        val groupsConfiguredFor: String? = null,
        val isReady: Boolean = false,
        val changelogToShow: List<Changelog.Entry> = emptyList(),
        /** Sekcja otwierana przy starcie (inteligentny start). */
        val startRoute: String = ElektronRoutes.DASHBOARD
    )

    private val currentVersionCode = BuildConfig.VERSION_CODE

    // Sekcja startowa ustalana raz, przy utworzeniu (uruchomieniu aplikacji). Ekran ładowania
    // czeka na nią, żeby nie było mignięcia strony głównej i przeskoku na plan.
    private val startRoute = MutableStateFlow<String?>(null)

    init {
        combine(settings.selectedClassId, settings.activeGroupSelections, notes.remindersEnabled, notes.reminderTiming) { a, b, c, d -> listOf(a, b, c, d) }
            .distinctUntilChanged().onEach { noteReminders.requestReschedule() }
            .catch { android.util.Log.w("LessonNotes", "Nie udało się odczytać notatek", it) }.launchIn(viewModelScope)
        // Przypomnienia przeliczane przy starcie i przy każdej zmianie, która zmienia ich
        // termin lub treść (klasa, grupy, nazwy przedmiotów, ustawienia przypomnień) -
        // nie trzeba czekać na najbliższą synchronizację.
        // W trakcie pierwszego syncu po wyborze klasy (initialSyncPending) - nic: planu nowej
        // klasy jeszcze nie ma, więc przeliczenie skasowałoby alarm. Przelicza SyncCoordinator
        // po pobraniu planu (i ten przepływ, gdy flaga zgaśnie).
        combine(
            settings.selectedClassId, settings.activeGroupSelections,
            settings.subjectStyles, settings.reminderSettings,
            coordinator.initialSyncPending
        ) { a, b, c, d, pending -> if (pending) null else listOf(a, b, c, d) }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { values ->
                // Zabezpieczenie na wypadek, gdyby zmiana klasy dotarła przed flagą syncu:
                // bez zapisanego planu klasy nie przeliczamy (nie kasujemy alarmu).
                val cid = values[0] as String?
                val today = AppClock.today()
                runCatchingCancellable {
                    if (cid == null || timetableRepo.hasLessons(cid, today, today.plusDays(7))) reminders.requestReschedule()
                }.onFailure { android.util.Log.w("LessonReminders", "Nie udało się sprawdzić planu; kolejna zmiana ponowi próbę", it) }
            }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            startRoute.value = withTimeoutOrNull(1_500) { decideStartRoute() } ?: ElektronRoutes.DASHBOARD
        }
    }

    /**
     * Ekran startowy z Ustawień. "Automatycznie": w godzinach lekcji (od 10 min przed pierwszą
     * do końca ostatniej, po filtrze grup) Plan lekcji, poza nimi Strona główna.
     */
    private suspend fun decideStartRoute(): String = runCatchingCancellable {
        val chosen = settings.startScreen.first()
        val todayLessons = if (chosen != StartScreen.SMART) emptyList() else {
            val cid = settings.selectedClassId.first()
            if (cid == null) emptyList() else {
                val today = AppClock.today()
                LessonGroups.filter(timetableRepo.getLessonsOnce(cid, today, today), settings.groupSelections(cid).first())
            }
        }
        when (chosen.resolve(todayLessons, AppClock.time())) {
            StartScreen.TIMETABLE -> ElektronRoutes.TIMETABLE
            StartScreen.SUBSTITUTIONS -> ElektronRoutes.SUBSTITUTIONS
            // Tylko z włączoną zakładką (wyłączona - strona główna, jak dla nieznanego ekranu).
            StartScreen.TRANSIT -> if (transitPreferences.preferences.first().visible) ElektronRoutes.TRANSIT else ElektronRoutes.DASHBOARD
            else -> ElektronRoutes.DASHBOARD
        }
    }.getOrDefault(ElektronRoutes.DASHBOARD)

    val state: StateFlow<AppState> = combine(
        combine(
        settings.themeMode,
        settings.dynamicColor,
        settings.selectedClassId,
        settings.lastSeenVersionCode,
        settings.groupsConfiguredFor
    ) { theme, dynamic, classId, lastSeen, groupsFor ->
        val entriesToShow = if (lastSeen in 1 until currentVersionCode) {
            Changelog.newerThan(lastSeen)
        } else emptyList()
        AppState(themeMode = theme, dynamicColor = dynamic, selectedClassId = classId,
            groupsConfiguredFor = groupsFor, isReady = true, changelogToShow = entriesToShow)
    },
        startRoute,
        settings.accent
    ) { app, route, accent ->
        val withAccent = app.copy(accent = accent)
        if (route == null) withAccent.copy(isReady = false) else withAccent.copy(startRoute = route)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState(isReady = false))

    /** Nazwy/kolory przedmiotów i wygląd planu - podawane całemu UI przez LocalPersonalization. */
    // null = jeszcze nie wczytano: aplikacja czeka na to (ElektronActivity), żeby nazwy/kolory
    // przedmiotów i tryb planu nie przeskakiwały z domyślnych na własne po pierwszej klatce.
    val personalization: StateFlow<Personalization?> =
        combine(settings.subjectStyles, settings.timetableLook) { styles, look -> Personalization(styles, look) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markChangelogSeen() {
        viewModelScope.launch { runCatchingCancellable { settings.setLastSeenVersionCode(currentVersionCode) } }
    }

    /**
     * Wołane raz przy każdym starcie aplikacji (nie tylko na ekranie Setup — bug audytu #4,
     * dawniej to gwarantowało, że dialog "Co nowego" nigdy się nie pokaże istniejącym
     * użytkownikom, bo ci nigdy nie trafiają na ekran Setup).
     *
     * Świeża instalacja (current == 0, brak wybranej klasy) -> lastSeen = currentVersionCode,
     * nie ma czego pokazywać.
     * Istniejący użytkownik uaktualniający apkę (current == 0, klasa już wybrana wcześniej,
     * czyli DataStore po prostu nie znał jeszcze tego klucza) -> lastSeen = currentVersionCode - 1,
     * żeby zobaczył wpis changelogu dla tej właśnie wersji.
     */
    fun ensureLastSeenInitialized() {
        viewModelScope.launch {
            val current = settings.lastSeenVersionCode.first()
            if (current == 0) {
                val isExistingUser = settings.selectedClassId.first() != null
                val target = if (isExistingUser) (currentVersionCode - 1).coerceAtLeast(0) else currentVersionCode
                runCatchingCancellable { settings.setLastSeenVersionCode(target) }
            }
        }
    }
}
