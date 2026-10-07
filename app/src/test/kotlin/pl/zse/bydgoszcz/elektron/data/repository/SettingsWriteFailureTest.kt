package pl.zse.bydgoszcz.elektron.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import java.io.IOException

/** Zapis ustawień przy błędzie pliku (np. brak miejsca) nie może wywracać aplikacji. */
class SettingsWriteFailureTest {

    private class FullDiskStore : DataStore<Preferences> {
        var attempts = 0
        override val data: Flow<Preferences> = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            attempts++
            throw IOException("ENOSPC (No space left on device)")
        }
    }

    @Test
    fun writeErrorIsReportedAndSettingStaysUnchanged() = runBlocking {
        val store = FullDiskStore()
        val repo = SettingsRepositoryImpl(store)
        for (write in listOf<suspend () -> Unit>(
            { repo.setThemeMode(ThemeMode.DARK) },
            { repo.setSelectedClassId("o3") },
            { repo.setGroupSelection("o3", "ang", "1/2") }
        )) {
            try { write(); org.junit.Assert.fail("Expected IOException") }
            catch (_: IOException) { }
        }
        assertEquals(3, store.attempts)
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }
    @Test fun groupChoicesFailAsOneWriteAndReportFailure() = runBlocking {
        val store = FullDiskStore()
        val repo = SettingsRepositoryImpl(store)
        try { repo.saveGroupChoices("o3", mapOf("ang" to "1/2", "wf" to "2/2")); org.junit.Assert.fail("Expected failure") }
        catch (_: IOException) { }
        assertEquals(1, store.attempts)
        assertEquals(emptyMap<String, String>(), repo.groupSelections("o3").first())
        org.junit.Assert.assertNull(repo.groupsConfiguredFor.first())
    }

}
