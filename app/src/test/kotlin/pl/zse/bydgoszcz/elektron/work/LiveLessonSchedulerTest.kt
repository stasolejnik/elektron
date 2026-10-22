package pl.zse.bydgoszcz.elektron.work

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.local.LessonEntity
import pl.zse.bydgoszcz.elektron.data.local.LessonGroupEntity
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.repository.LessonNotesRepository
import pl.zse.bydgoszcz.elektron.data.repository.TimetableRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Powiadomienie na żywo w systemie: kanał, stałe ID, chronometr, ukrywanie i wyłączanie. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveLessonSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val day = LocalDate.of(2026, 10, 16)
    private lateinit var db: AppDatabase
    private val settings = FakeSettings()
    private lateinit var scheduler: LiveLessonScheduler
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    @Before fun setUp() = runBlocking {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        LocalNotificationSink(context, settings)   // kanały
        db = inMemoryDb()
        db.lessonDao().upsertAll(listOf(
            LessonEntity("o3|${day.toEpochDay()}|1", "o3", "1D", day.toEpochDay(), 5, 1, "08:00", "08:45", null),
            LessonEntity("o3|${day.toEpochDay()}|2", "o3", "1D", day.toEpochDay(), 5, 2, "08:55", "09:40", null)))
        db.lessonGroupDao().upsertAll(listOf(
            LessonGroupEntity(lessonId = "o3|${day.toEpochDay()}|1", sortOrder = 0, subject = "mat", teacherCode = "Ko", teacherUrl = null,
                teacherFullName = null, room = "105", roomUrl = null, groupLabel = null, classRef = null),
            LessonGroupEntity(lessonId = "o3|${day.toEpochDay()}|2", sortOrder = 0, subject = "fiz", teacherCode = "No", teacherUrl = null,
                teacherFullName = null, room = "12", roomUrl = null, groupLabel = null, classRef = null)))
        val source = object : TimetableSource {
            override suspend fun fetchSidebar() = error("offline")
            override suspend fun fetchTimetable(classId: String) = error("offline")
        }
        val repo = TimetableRepositoryImpl(source, db.schoolClassDao(), db.teacherDao(), db.roomDao(),
            db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)
        val file = File(context.cacheDir, "live-${System.nanoTime()}.preferences_pb")
        val notes = LessonNotesRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile = { file }))
        scheduler = LiveLessonScheduler(context, settings, repo, notes)
    }

    @After fun tearDown() = db.close()

    private fun live(): Notification? = manager.activeNotifications.firstOrNull { it.id == LiveLessonScheduler.NOTIFICATION_ID }?.notification

    @Test fun offByDefault() = runBlocking {
        scheduler.reschedule(day.atTime(8, 10))
        assertNull(live())
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test fun duringLessonIsSilentOngoingCountdownOnLiveChannel() = runBlocking {
        settings.liveFlow.value = true
        scheduler.reschedule(day.atTime(8, 10))
        val n = requireNotNull(live())
        assertEquals(LocalNotificationSink.CHANNEL_LIVE, n.channelId)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Do przerwy", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Teraz: 1. mat · s. 105", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(day.atTime(8, 45).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), n.`when`)
        assertNotNull(n.publicVersion)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(LocalNotificationSink.CHANNEL_LIVE).importance)
        assertNull(manager.getNotificationChannel(LocalNotificationSink.CHANNEL_LIVE).sound)
        assertNotNull("alarm na najbliższy dzwonek", alarms.peekNextScheduledAlarm())
        // "Wyczyść wszystko" go nie usuwa; mały napis o końcu dnia (plan do 09:40).
        assertTrue(n.flags and Notification.FLAG_NO_CLEAR != 0)
        assertEquals("Koniec lekcji o 09:40 · jeszcze 1 h 30 min", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        // Pasek i napis odświeżane co minutę alarmem bez budzenia telefonu.
        val progress = alarms.scheduledAlarms.single { it.type == AlarmManager.RTC }
        assertEquals(day.atTime(8, 11).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L, progress.triggerAtTime)
    }

    @Test fun hideTodayRemovesUntilTomorrowAndDisablingCancels() = runBlocking {
        settings.liveFlow.value = true
        scheduler.reschedule(day.atTime(8, 10))
        scheduler.hideToday(day.atTime(8, 11))
        assertNull(live())
        assertEquals(day.toEpochDay(), settings.liveHiddenFlow.value)
        scheduler.reschedule(day.atTime(8, 50))
        assertNull("ukryte do końca dnia", live())
        settings.liveHiddenFlow.value = null
        scheduler.reschedule(day.atTime(8, 50))
        assertNotNull(live())
        settings.liveFlow.value = false
        scheduler.reschedule(day.atTime(8, 51))
        assertNull(live())
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test fun afterLessonsNothingIsShown() = runBlocking {
        settings.liveFlow.value = true
        scheduler.reschedule(day.atTime(9, 40))
        assertNull(live())
    }

    @Test fun simulatedTimeDrivesContentWhileAlarmsUseRealTime() = runBlocking {
        settings.liveFlow.value = true
        val zone = java.time.ZoneId.systemDefault()
        val simulated = day.atTime(8, 10).atZone(zone).toInstant().toEpochMilli()
        val offset = simulated - System.currentTimeMillis()
        pl.zse.bydgoszcz.elektron.domain.util.AppClock.restore(offset)
        try {
            scheduler.reschedule()                 // czas aplikacji (symulowany)
            val n = requireNotNull(live())
            assertEquals("Do przerwy", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Teraz: 1. mat · s. 105", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            // Chronometr i alarm w czasie telefonu: koniec lekcji za ok. 35 min od teraz.
            val left = n.`when` - System.currentTimeMillis()
            assertTrue("odliczanie ok. 35 min, było ${left / 60000} min", left in 34 * 60_000L..36 * 60_000L)
            val alarm = requireNotNull(alarms.peekNextScheduledAlarm())
            assertTrue(alarm.triggerAtTime - System.currentTimeMillis() in 0..36 * 60_000L)
        } finally { pl.zse.bydgoszcz.elektron.domain.util.AppClock.restore(0L) }
    }
}
