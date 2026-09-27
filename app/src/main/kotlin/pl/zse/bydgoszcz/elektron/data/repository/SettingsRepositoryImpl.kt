package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.elektronSettings: DataStore<Preferences> by preferencesDataStore(name = "elektron_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val SELECTED_CLASS_ID = stringPreferencesKey("selected_class_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val NOTIF_SUBSTITUTIONS = booleanPreferencesKey("notif_substitutions")
        val NOTIF_ANNOUNCEMENTS = booleanPreferencesKey("notif_announcements")
        val SHOW_NEXT_LESSON = booleanPreferencesKey("show_next_lesson")
        val SHOW_SUBSTITUTIONS = booleanPreferencesKey("show_substitutions")
        val SHOW_ANNOUNCEMENTS = booleanPreferencesKey("show_announcements")
        val LAST_SEEN_VERSION = intPreferencesKey("last_seen_version_code")
        val GROUPS_CONFIGURED_FOR = stringPreferencesKey("groups_configured_for")
        fun groups(classId: String) = stringPreferencesKey("groups_$classId")
    }

    private val prefs: Flow<Preferences> = context.elektronSettings.data

    override val selectedClassId: Flow<String?> = prefs.map { it[Keys.SELECTED_CLASS_ID] }
    override suspend fun setSelectedClassId(id: String) {
        context.elektronSettings.edit { it[Keys.SELECTED_CLASS_ID] = id }
    }

    override val themeMode: Flow<ThemeMode> = prefs.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }
    override suspend fun setThemeMode(mode: ThemeMode) {
        context.elektronSettings.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override val dynamicColor: Flow<Boolean> = prefs.map { it[Keys.DYNAMIC_COLOR] ?: true }
    override suspend fun setDynamicColor(enabled: Boolean) {
        context.elektronSettings.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override val notificationsSubstitutions: Flow<Boolean> =
        prefs.map { it[Keys.NOTIF_SUBSTITUTIONS] ?: true }
    override suspend fun setNotificationsSubstitutions(enabled: Boolean) {
        context.elektronSettings.edit { it[Keys.NOTIF_SUBSTITUTIONS] = enabled }
    }

    override val notificationsAnnouncements: Flow<Boolean> =
        prefs.map { it[Keys.NOTIF_ANNOUNCEMENTS] ?: false }
    override suspend fun setNotificationsAnnouncements(enabled: Boolean) {
        context.elektronSettings.edit { it[Keys.NOTIF_ANNOUNCEMENTS] = enabled }
    }

    override val showNextLesson: Flow<Boolean> = prefs.map { it[Keys.SHOW_NEXT_LESSON] ?: true }
    override suspend fun setShowNextLesson(enabled: Boolean) {
        context.elektronSettings.edit { it[Keys.SHOW_NEXT_LESSON] = enabled }
    }

    override val showSubstitutions: Flow<Boolean> = prefs.map { it[Keys.SHOW_SUBSTITUTIONS] ?: true }
    override suspend fun setShowSubstitutions(enabled: Boolean) {
        context.elektronSettings.edit { it[Keys.SHOW_SUBSTITUTIONS] = enabled }
    }

    override val showAnnouncements: Flow<Boolean> = prefs.map { it[Keys.SHOW_ANNOUNCEMENTS] ?: true }
    override suspend fun setShowAnnouncements(enabled: Boolean) {
        context.elektronSettings.edit { it[Keys.SHOW_ANNOUNCEMENTS] = enabled }
    }

    override val lastSeenVersionCode: Flow<Int> = prefs.map { it[Keys.LAST_SEEN_VERSION] ?: 0 }
    override suspend fun setLastSeenVersionCode(code: Int) {
        context.elektronSettings.edit { it[Keys.LAST_SEEN_VERSION] = code }
    }

    // Format: "przedmiot\twybór" w liniach — nazwy przedmiotów z Optivum nie zawierają
    // tabulatora ani nowej linii.
    private fun decodeGroups(raw: String?): Map<String, String> =
        raw?.lineSequence()?.mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }?.toMap() ?: emptyMap()

    private fun encodeGroups(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    override fun groupSelections(classId: String): Flow<Map<String, String>> =
        prefs.map { decodeGroups(it[Keys.groups(classId)]) }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val activeGroupSelections: Flow<Map<String, String>> =
        selectedClassId.flatMapLatest { cid -> if (cid == null) flowOf(emptyMap()) else groupSelections(cid) }

    override suspend fun setGroupSelection(classId: String, subject: String, choice: String?) {
        context.elektronSettings.edit {
            val current = decodeGroups(it[Keys.groups(classId)]).toMutableMap()
            if (choice == null) current.remove(subject) else current[subject] = choice
            it[Keys.groups(classId)] = encodeGroups(current)
        }
    }

    override val groupsConfiguredFor: Flow<String?> = prefs.map { it[Keys.GROUPS_CONFIGURED_FOR] }
    override suspend fun setGroupsConfiguredFor(classId: String) {
        context.elektronSettings.edit { it[Keys.GROUPS_CONFIGURED_FOR] = classId }
    }
}
