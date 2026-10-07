package pl.zse.bydgoszcz.elektron.presentation.timetable

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.repository.LessonNotesRepository
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import pl.zse.bydgoszcz.elektron.work.*
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LessonNoteDraftTest {
    @Test fun restoredEditorKeepsDraftInsteadOfReplacingItWithStoredText() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(app.cacheDir, "draft-${System.nanoTime()}.preferences_pb")
        val notes = LessonNotesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val day = LocalDate.now().plusDays(2)
        val lesson = Lesson("l", "o3", "1D", day, pl.zse.bydgoszcz.elektron.domain.model.DayOfWeek.fromIso(day.dayOfWeek.value)!!, 1,
            LocalTime.of(9, 0), LocalTime.of(9, 45), emptyList(), null, null)
        val currentLessons = MutableStateFlow(listOf(lesson))
        val timetable = object : TimetableRepository {
            override suspend fun syncSidebar() = Result.success(Unit)
            override suspend fun syncTimetable(classId: String, anchorDate: LocalDate) = Result.success(Unit)
            override fun observeClasses() = flowOf(emptyList<SchoolClass>())
            override fun observeTeachers() = flowOf(emptyList<Teacher>())
            override fun observeRooms() = flowOf(emptyList<SchoolRoom>())
            override fun observeLessons(classId: String, from: LocalDate, to: LocalDate) = currentLessons
            override fun observeAllLessons(from: LocalDate, to: LocalDate) = currentLessons
            override suspend fun getLessonsOnce(classId: String, from: LocalDate, to: LocalDate) = currentLessons.value
            override suspend fun enrichWithTeacherNames(lessons: List<Lesson>) = lessons
            override suspend fun hasLessons(classId: String, from: LocalDate, to: LocalDate) = true
        }
        val settings = FakeSettings()
        val scheduler = NoteReminderScheduler(app, notes, settings, timetable, LocalNotificationSink(app, settings))
        val handle = SavedStateHandle()
        val owner = ViewModelStore()
        try {
            val first = LessonNotesViewModel(notes, scheduler, WidgetUpdater(app), handle, timetable, settings)
            owner.put("first", first)
            first.state.first { it.ready }
            first.openEditor(lesson); first.editText("Niezapisany szkic\nDruga linia")
            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
            val restored = LessonNotesViewModel(notes, scheduler, WidgetUpdater(app), restoredHandle, timetable, settings)
            owner.put("restored", restored)
            assertEquals(lesson, restored.editorLesson.first { it != null })
            assertEquals("Niezapisany szkic\nDruga linia", restored.editorText.value)
            restored.openEditor(lesson)
            assertEquals("Niezapisany szkic\nDruga linia", restored.editorText.value)
            currentLessons.value = emptyList()
            restored.error.first { it != null }
            assertFalse(restored.editorAvailable.value)
            assertEquals("Niezapisany szkic\nDruga linia", restored.editorText.value)
            val missing = LessonNotesViewModel(notes, scheduler, WidgetUpdater(app),
                SavedStateHandle(restoredHandle.keys().associateWith { restoredHandle.get<Any>(it) }), timetable, settings)
            owner.put("missing", missing)
            missing.orphanedDraft.first { it }
            assertEquals("Niezapisany szkic\nDruga linia", missing.editorText.value)
            restored.closeEditor()
            assertTrue(restoredHandle.keys().isEmpty())
        } finally { owner.clear(); scope.cancel(); scope.coroutineContext[Job]!!.join(); Dispatchers.resetMain(); file.delete() }
    }
}
