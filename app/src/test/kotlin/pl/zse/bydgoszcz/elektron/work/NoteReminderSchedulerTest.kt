package pl.zse.bydgoszcz.elektron.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.repository.LessonNotesRepository
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteReminderSchedulerTest {
    private class Plan(var lessons: List<Lesson>) : TimetableRepository {
        override suspend fun syncSidebar() = error("No network")
        override suspend fun syncTimetable(classId: String, anchorDate: LocalDate) = error("No network")
        override fun observeClasses() = flowOf(emptyList<SchoolClass>())
        override fun observeTeachers() = flowOf(emptyList<Teacher>())
        override fun observeRooms() = flowOf(emptyList<SchoolRoom>())
        override fun observeLessons(classId: String, from: LocalDate, to: LocalDate) = flowOf(lessons)
        override fun observeAllLessons(from: LocalDate, to: LocalDate) = flowOf(lessons)
        override suspend fun getLessonsOnce(classId: String, from: LocalDate, to: LocalDate) = lessons.filter { it.classId == classId && it.date in from..to }
        override suspend fun enrichWithTeacherNames(lessons: List<Lesson>) = lessons
        override suspend fun hasLessons(classId: String, from: LocalDate, to: LocalDate) = true
    }
    @Test fun eveningDeliversAllDueNotesOnceAndTapOpensCorrectLesson() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { WorkManager.getInstance(app) }.getOrElse {
            WorkManager.initialize(app, Configuration.Builder().build())
            WorkManager.getInstance(app)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(app.cacheDir, "reminder-notes.preferences_pb").apply { delete() }
        val repo = LessonNotesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val day = LocalDate.now().plusDays(2)
        val lessons = (1..2).map { Lesson("l$it", "o3", "1D", day, DayOfWeek.fromIso(day.dayOfWeek.value)!!,
            it, LocalTime.of(8 + it, 0), LocalTime.of(8 + it, 45), emptyList(), null, null) }
        val settings = FakeSettings()
        val sink = LocalNotificationSink(app, settings)
        val scheduler = NoteReminderScheduler(app, repo, settings, Plan(lessons), sink)
        try {
            lessons.forEach { repo.save(LessonNote("o3", day, it.number, "mat", "Notatka ${it.number}", 0)) }
            repo.setReminders(true)
            scheduler.reschedule(LocalDateTime.now())
            val wm = WorkManager.getInstance(app)
            val pending = wm.getWorkInfosForUniqueWork(NoteReminderScheduler.WORK_NAME).get().single { !it.state.isFinished }
            scheduler.reschedule(LocalDateTime.now())
            assertEquals(pending.id, wm.getWorkInfosForUniqueWork(NoteReminderScheduler.WORK_NAME).get().single { !it.state.isFinished }.id)
            repo.setReminders(false)
            scheduler.reschedule(LocalDateTime.now())
            assertTrue(wm.getWorkInfosForUniqueWork(NoteReminderScheduler.WORK_NAME).get().all { it.state.isFinished })
            repo.setReminders(true)
            scheduler.reschedule(LocalDateTime.now())
            val saved = repo.notes.first().first()
            val now = day.minusDays(1).atTime(18, 0)
            scheduler.deliver(saved.key, saved.revision, now)
            assertTrue(repo.notes.first().all { it.reminded })
            val mgr = app.getSystemService(NotificationManager::class.java)
            assertEquals(2, mgr.activeNotifications.size)
            val n = mgr.activeNotifications.first().notification
            val intent = shadowOf(n.contentIntent).savedIntent
            assertNotNull(LessonLinks.parseDeepLink(intent.getStringExtra(LessonLinks.EXTRA_LESSON), LocalDate.now()))
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE, n.visibility)
            scheduler.deliver(saved.key, saved.revision, now)
            assertEquals(2, mgr.activeNotifications.size)
            // Zmiana klasy tuż przed alarmem nie wysyła notatki poprzedniej klasy.
            repo.save(saved.copy(text = "Nowa edycja"))
            val edited = repo.notes.first().first { it.key == saved.key }
            settings.setSelectedClassId("o4")
            scheduler.deliver(edited.key, edited.revision, now.plusHours(1))
            assertFalse(repo.notes.first().first { it.key == edited.key }.reminded)
            assertEquals(2, mgr.activeNotifications.size)
            mgr.cancelAll()
            permissionRevokedAfterClaimDoesNotConsumeReminder()
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
    }
    private suspend fun permissionRevokedAfterClaimDoesNotConsumeReminder() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(app.cacheDir, "reminder-permission-${System.nanoTime()}.preferences_pb")
        val repo = LessonNotesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val day = LocalDate.now().plusDays(2)
        val lesson = Lesson("l1", "o3", "1D", day, DayOfWeek.fromIso(day.dayOfWeek.value)!!,
            1, LocalTime.of(9, 0), LocalTime.of(9, 45), emptyList(), null, null)
        var revokeDuringDelivery = true
        val settings = object : pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository by FakeSettings() {
            override val subjectStyles: Flow<Map<String, SubjectStyle>> = flow {
                if (revokeDuringDelivery) shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
                emit(emptyMap())
            }
        }
        val sink = LocalNotificationSink(app, settings)
        val scheduler = NoteReminderScheduler(app, repo, settings, Plan(listOf(lesson)), sink)
        try {
            repo.save(LessonNote("o3", day, 1, "mat", "Ważna notatka", 0))
            repo.setReminders(true)
            val saved = repo.notes.first().single()
            val now = day.minusDays(1).atTime(18, 0)
            try { scheduler.deliver(saved.key, saved.revision, now); fail("Delivery failure must be reported") }
            catch (_: java.io.IOException) { }
            assertFalse(repo.notes.first().single().reminded)
            assertEquals(0, app.getSystemService(NotificationManager::class.java).activeNotifications.size)
            revokeDuringDelivery = false
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            scheduler.deliver(saved.key, saved.revision, now)
            assertTrue(repo.notes.first().single().reminded)
            assertEquals(1, app.getSystemService(NotificationManager::class.java).activeNotifications.size)
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete() }
    }

}
