package pl.zse.bydgoszcz.elektron.presentation.common

import kotlinx.coroutines.flow.launchIn
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
    reminders: LessonReminderScheduler
) : ViewModel() {

    data class AppState(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
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
        // Przypomnienia przeliczane przy starcie i przy każdej zmianie, która zmienia ich
        // termin lub treść (klasa, grupy, nazwy przedmiotów, ustawienia przypomnień) -
        // nie trzeba czekać na najbliższą synchronizację.
        combine(
            settings.selectedClassId, settings.activeGroupSelections,
            settings.subjectStyles, settings.reminderSettings
        ) { a, b, c, d -> listOf(a, b, c, d) }
            .distinctUntilChanged()
            .onEach { reminders.requestReschedule() }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            startRoute.value = withTimeoutOrNull(1_500) { decideStartRoute() } ?: ElektronRoutes.DASHBOARD
        }
    }

    /**
     * Ekran startowy z Ustawień. "Automatycznie": w godzinach lekcji (od 10 min przed pierwszą
     * do końca ostatniej, po filtrze grup) Plan lekcji, poza nimi Strona główna.
     */
    private suspend fun decideStartRoute(): String = runCatching {
        val chosen = settings.startScreen.first()
        val todayLessons = if (chosen != StartScreen.SMART) emptyList() else {
            val cid = settings.selectedClassId.first()
            if (cid == null) emptyList() else {
                val today = LocalDate.now()
                LessonGroups.filter(timetableRepo.getLessonsOnce(cid, today, today), settings.groupSelections(cid).first())
            }
        }
        when (chosen.resolve(todayLessons, LocalTime.now())) {
            StartScreen.TIMETABLE -> ElektronRoutes.TIMETABLE
            StartScreen.SUBSTITUTIONS -> ElektronRoutes.SUBSTITUTIONS
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
        startRoute
    ) { app, route ->
        if (route == null) app.copy(isReady = false) else app.copy(startRoute = route)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState(isReady = false))

    /** Nazwy/kolory przedmiotów i wygląd planu - podawane całemu UI przez LocalPersonalization. */
    val personalization: StateFlow<Personalization> =
        combine(settings.subjectStyles, settings.timetableLook) { styles, look -> Personalization(styles, look) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Personalization())

    fun markChangelogSeen() {
        viewModelScope.launch { settings.setLastSeenVersionCode(currentVersionCode) }
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
                settings.setLastSeenVersionCode(target)
            }
        }
    }
}
