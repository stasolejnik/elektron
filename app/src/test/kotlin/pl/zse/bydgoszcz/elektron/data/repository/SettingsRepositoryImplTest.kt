package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import java.io.File
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Jeden test (DataStore to singleton na proces - drugi test w tej samej piaskownicy
     * dostałby plik z pierwszego).
     */
    @Test
    fun corruptedFileFallsBackToDefaultsAndClassIdEmitsOnlyOnChange() = runBlocking {
        // 1. Uszkodzony plik ustawień: dawniej CorruptionException w każdym kolektorze.
        File(context.filesDir, "datastore").mkdirs()
        File(context.filesDir, "datastore/elektron_settings.preferences_pb").writeBytes(byteArrayOf(0x7F, 0x01, 0x02, 0x03))
        val repo = SettingsRepositoryImpl(context)
        assertNull(withTimeout(5_000) { repo.selectedClassId.first() })
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())

        // 2. Zapis innego ustawienia nie może "zmieniać" klasy (restart flatMapLatest).
        repo.setSelectedClassId("o3")
        val seen = Collections.synchronizedList(mutableListOf<String?>())
        val job = launch(Dispatchers.Default) { repo.selectedClassId.collect { seen += it } }
        withTimeout(5_000) { while (seen.isEmpty()) delay(10) }
        repo.setThemeMode(ThemeMode.DARK)
        repo.setShowNextLesson(false)
        withTimeout(5_000) { while (repo.themeMode.first() != ThemeMode.DARK) delay(10) }
        delay(300)
        job.cancel()
        assertEquals(listOf<String?>("o3"), seen.toList())
    }
}
