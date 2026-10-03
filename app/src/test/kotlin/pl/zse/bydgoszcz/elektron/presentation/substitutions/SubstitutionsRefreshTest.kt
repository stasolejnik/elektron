package pl.zse.bydgoszcz.elektron.presentation.substitutions

import android.app.AlarmManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonCellDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonGroupDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.repository.NotificationsRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.SubstitutionsRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.TimetableRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.ReminderMode
import pl.zse.bydgoszcz.elektron.domain.model.ReminderSettings
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import pl.zse.bydgoszcz.elektron.work.LessonReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Ręczne odświeżenie (tu: zakładka Zastępstwa; Start, Plan i Ogłoszenia działają tak samo)
 * przelicza przypomnienie - dawniej czekało to na najbliższy sync w tle, więc np. zwolnienie
 * z lekcji było widać w aplikacji, a przypomnienie o tej lekcji i tak przychodziło.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubstitutionsRefreshTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private val settings = FakeSettings(reminders = ReminderSettings(ReminderMode.EVERY, 10))

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() = db.close()

    @Test
    fun manualRefreshReschedulesReminder() {
        val timetableRepo = TimetableRepositoryImpl(object : TimetableSource {
            override suspend fun fetchSidebar() = emptyList<ClassListItemDto>()
            override suspend fun fetchTimetable(classId: String) = TimetableDto(classId, "1D 1D PBŚ", null, null, (1..5).map { day ->
                LessonCellDto(2, "08:55", "09:40", day, listOf(LessonGroupDto("mat", "Ch", null, "105", null, null, null)), null)
            })
        }, db.schoolClassDao(), db.teacherDao(), db.roomDao(), db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)
        runBlocking { timetableRepo.syncTimetable("o3", LocalDate.now().with(DayOfWeek.MONDAY)) }

        val repo = SubstitutionsRepositoryImpl(object : SubstitutionsSource {
            override suspend fun fetchSubstitutions() = emptyList<SubstitutionDto>()
        }, db.substitutionDao(), db)
        val vm = SubstitutionsViewModel(repo, settings, timetableRepo,
            NotificationsRepositoryImpl(db.notificationDao(), db.syncStateDao()),
            WidgetUpdater(context), LessonReminderScheduler(context, settings, timetableRepo))
        val am = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertNull(am.nextScheduledAlarm)

        vm.refresh()
        val deadline = System.currentTimeMillis() + 10_000
        while (am.nextScheduledAlarm == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertNotNull(am.nextScheduledAlarm)
    }
}
