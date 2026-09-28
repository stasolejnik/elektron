package pl.zse.bydgoszcz.elektron.domain.repository

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface SettingsRepository {
    val selectedClassId: Flow<String?>
    suspend fun setSelectedClassId(id: String)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val dynamicColor: Flow<Boolean>
    suspend fun setDynamicColor(enabled: Boolean)

    val notificationsSubstitutions: Flow<Boolean>
    suspend fun setNotificationsSubstitutions(enabled: Boolean)

    val notificationsAnnouncements: Flow<Boolean>
    suspend fun setNotificationsAnnouncements(enabled: Boolean)

    val showNextLesson: Flow<Boolean>
    suspend fun setShowNextLesson(enabled: Boolean)

    val showSubstitutions: Flow<Boolean>
    suspend fun setShowSubstitutions(enabled: Boolean)

    val showAnnouncements: Flow<Boolean>
    suspend fun setShowAnnouncements(enabled: Boolean)

    /** Inteligentny start: w godzinach lekcji aplikacja otwiera się na planie. */
    val smartStart: Flow<Boolean>
    suspend fun setSmartStart(enabled: Boolean)

    val lastSeenVersionCode: Flow<Int>
    suspend fun setLastSeenVersionCode(code: Int)

    // --- Grupy zajęciowe (per klasa): przedmiot bazowy -> etykieta grupy lub LessonGroups.NONE ---
    fun groupSelections(classId: String): Flow<Map<String, String>>
    /** Wybory dla aktualnie wybranej klasy (pusta mapa, gdy brak klasy). */
    val activeGroupSelections: Flow<Map<String, String>>
    /** choice == null usuwa wybór (= pokazuj wszystkie grupy tego przedmiotu). */
    suspend fun setGroupSelection(classId: String, subject: String, choice: String?)

    /** Klasa, dla której użytkownik przeszedł krok wyboru grup (null = jeszcze nigdy). */
    val groupsConfiguredFor: Flow<String?>
    suspend fun setGroupsConfiguredFor(classId: String)
}
