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
    fun writeErrorIsSwallowedAndSettingStaysUnchanged() = runBlocking {
        val store = FullDiskStore()
        val repo = SettingsRepositoryImpl(store)
        // Dawniej: IOException leciał dalej - awaria przy przełączeniu dowolnej opcji.
        repo.setThemeMode(ThemeMode.DARK)
        repo.setSelectedClassId("o3")
        repo.setGroupSelection("o3", "ang", "1/2")
        assertEquals(3, store.attempts)
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }
}
