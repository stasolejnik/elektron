package pl.zse.bydgoszcz.elektron.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.LessonEntity
import pl.zse.bydgoszcz.elektron.data.repository.*
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.presentation.settings.reminderNotificationsEnabled
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderStatusTest {
    @Test fun statusReflectsScheduledAlarmAndDisablingCancelsIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = inMemoryDb()
        try {
            val now = LocalDateTime.of(2026, 10, 5, 7, 0)
            db.lessonDao().upsertAll(listOf(LessonEntity("l", "o3", "1D", now.toLocalDate().toEpochDay(),
                1, 1, "08:00", "08:45", null)))
            val source = object : TimetableSource {
                override suspend fun fetchSidebar() = error("No network")
                override suspend fun fetchTimetable(classId: String) = error("No network")
            }
            val repo = TimetableRepositoryImpl(source, db.schoolClassDao(), db.teacherDao(), db.roomDao(),
                db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)
            val settings = FakeSettings(reminders = ReminderSettings(ReminderMode.EVERY, 10))
            val scheduler = LessonReminderScheduler(context, settings, repo)
            scheduler.reschedule(now)
            assertTrue(scheduler.status.value.ready)
            assertFalse(scheduler.status.value.error)
            assertEquals(now.withHour(7).withMinute(50), scheduler.status.value.at)
            assertNotNull(shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm())
            settings.setReminderSettings(ReminderSettings(ReminderMode.OFF))
            scheduler.reschedule(now)
            assertNull(scheduler.status.value.at)
            assertNull(shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm())
        } finally { db.close() }
    }

    @Test fun blockedReminderChannelIsDetected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(LocalNotificationSink.CHANNEL_REMINDERS, "Przypomnienia", NotificationManager.IMPORTANCE_NONE))
        assertFalse(reminderNotificationsEnabled(context))
    }
}
