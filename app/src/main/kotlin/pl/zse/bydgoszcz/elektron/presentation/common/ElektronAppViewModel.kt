package pl.zse.bydgoszcz.elektron.presentation.common

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
import javax.inject.Inject

@HiltViewModel
class ElektronAppViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    data class AppState(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = true,
        val selectedClassId: String? = null,
        /** Klasa, dla której przeszedł krok wyboru grup — różna od selectedClassId => pokaż krok. */
        val groupsConfiguredFor: String? = null,
        val isReady: Boolean = false,
        val changelogToShow: List<Changelog.Entry> = emptyList()
    )

    private val currentVersionCode = BuildConfig.VERSION_CODE

    val state: StateFlow<AppState> = combine(
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState(isReady = false))

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
