package pl.zse.bydgoszcz.elektron.work

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.*
import pl.zse.bydgoszcz.elektron.data.remote.sources.*
import pl.zse.bydgoszcz.elektron.data.repository.*
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushReminderTest {
    @Test fun cancelledLessonPushCancelsAlarmAndCoalescesWidgets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val db = inMemoryDb()
        try {
            val day = LocalDate.now().plusDays(1)
            db.schoolClassDao().upsertAll(listOf(SchoolClassEntity("o3", "1D", "1D", "https://plan.zse.bydgoszcz.pl/")))
            db.lessonDao().upsertAll(listOf(LessonEntity("lesson", "o3", "1D", day.toEpochDay(),
                day.dayOfWeek.value, 1, "08:00", "08:45", "matematyka")))
            val settings = FakeSettings(reminders = ReminderSettings(ReminderMode.EVERY, 10))
            settings.notifySubs.value = false
            val source = object : TimetableSource {
                override suspend fun fetchSidebar() = error("Unexpected network")
                override suspend fun fetchTimetable(classId: String) = error("Unexpected network")
            }
            val timetable = TimetableRepositoryImpl(source, db.schoolClassDao(), db.teacherDao(),
                db.roomDao(), db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)
            val subs = SubstitutionsRepositoryImpl(object : SubstitutionsSource {
                override suspend fun fetchSubstitutions() = error("Unexpected network")
            }, db.substitutionDao(), db)
            val notifications = NotificationsRepositoryImpl(db.notificationDao(), db.syncStateDao())
            val sink = object : NotificationSink {
                override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) {}
                override suspend fun postAnnouncement(ann: Announcement) {}
                override suspend fun postGeneric(title: String, body: String, deepLink: String?) {}
            }
            val scheduler = LessonReminderScheduler(context, settings, timetable)
            scheduler.reschedule()
            val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
            assertNotNull(alarms.peekNextScheduledAlarm())
            val service = ElektronFirebaseMessagingService().also {
                it.settings = settings
                it.substitutionsRepo = subs
                it.timetableRepo = timetable
                it.notificationsRepo = notifications
                it.reminders = scheduler
                it.widgetUpdater = WidgetUpdater(context)
                it.substitutionNotifier = SubstitutionNotifier(settings, timetable, notifications, sink)
            }
            val message = RemoteMessage.Builder("test").setData(mapOf(
                "type" to "substitution", "id" to "push-1", "date" to day.toString(),
                "lessonNumber" to "1", "classShortName" to "1D", "roomOrInfo" to "Uczniowie zwolnieni"
            )).build()
            service.onMessageReceived(message)
            assertNull(alarms.peekNextScheduledAlarm())
            val manager = WorkManager.getInstance(context)
            val first = manager.getWorkInfosForUniqueWork("elektron_widget_refresh").get().single()
            service.onMessageReceived(message) // ten sam push nie tworzy kolejnej pracy
            assertEquals(first.id, manager.getWorkInfosForUniqueWork("elektron_widget_refresh").get().single().id)
        } finally { db.close() }
    }
}
