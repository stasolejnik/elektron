package pl.zse.bydgoszcz.elektron.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import java.time.LocalDate
import pl.zse.bydgoszcz.elektron.domain.model.LessonNote

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LessonNotesRepositoryTest {
    @Test fun writeFailureIsReportedWithoutDiscardingStoredData() = runBlocking {
        val store = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = kotlinx.coroutines.flow.flowOf(androidx.datastore.preferences.core.emptyPreferences())
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
                throw java.io.IOException("Brak miejsca")
            }
        }
        val repo = LessonNotesRepository(store)
        try {
            repo.save(LessonNote("o3", LocalDate.now().plusDays(1), 1, "mat", "Ważna treść", 0))
            fail("Błąd zapisu musi zostać zgłoszony")
        } catch (_: java.io.IOException) { }
        assertTrue(repo.notes.first().isEmpty())
    }
    @Test fun preservesTextAcrossRestartAndClassesAndClaimsOnlyCurrentRevisionOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "note-test.preferences_pb").apply { delete() }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var repo = LessonNotesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        try {
            assertFalse(repo.remindersEnabled.first())
            val first = LessonNote("o3", LocalDate.of(2026, 10, 6), 2, "mat", "Linia 1\n\"Tekst\" i zażółć", 0)
            repo.save(first); repo.save(first.copy(classId = "o4", text = "Druga klasa")); repo.setReminders(true)
            val timing = pl.zse.bydgoszcz.elektron.domain.model.NoteReminderSettings(previousDay = false,
                time = java.time.LocalTime.of(21, 15), minutesBefore = 15)
            repo.setReminderTiming(timing)
            val saved = repo.notes.first().first { it.classId == "o3" }
            assertEquals(first.text, saved.text)
            assertTrue(repo.claimReminder(saved.key, saved.revision))
            assertFalse(repo.claimReminder(saved.key, saved.revision))
            repo.save(saved.copy(text = "Edycja"))
            val newer = repo.notes.first().first { it.classId == "o3" }
            assertTrue(newer.revision > saved.revision)
            assertFalse(newer.reminded)
            assertFalse(repo.claimReminder(saved.key, saved.revision))
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = LessonNotesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(2, repo.notes.first().size)
            assertTrue(repo.remindersEnabled.first())
            assertEquals(timing, repo.reminderTiming.first())
            repo.delete(newer.key)
            assertEquals("o4", repo.notes.first().single().classId)
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
    }
}
