package pl.zse.bydgoszcz.elektron.domain.usecase

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonCellDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonGroupDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.repository.NotificationsRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.ReminderMode
import pl.zse.bydgoszcz.elektron.domain.model.ReminderSettings
import pl.zse.bydgoszcz.elektron.domain.model.SyncErrors
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.testutil.TestCoordinator
import java.net.UnknownHostException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClassSelectionTest {

    private class Timetable(var error: Exception? = null) : TimetableSource {
        override suspend fun fetchSidebar() = emptyList<ClassListItemDto>()
        override suspend fun fetchTimetable(classId: String): TimetableDto {
            error?.let { throw it }
            // Lekcje od poniedziałku do piątku - najbliższa zawsze w ciągu tygodnia.
            return TimetableDto(classId, "1D 1D PBŚ", null, null, (1..5).map { day ->
                LessonCellDto(2, "08:55", "09:40", day, listOf(LessonGroupDto("mat", "Ch", null, "105", null, null, null)), null)
            })
        }
    }

    private class Subs : SubstitutionsSource {
        override suspend fun fetchSubstitutions() = emptyList<SubstitutionDto>()
    }

    private class Anns : AnnouncementsSource {
        override suspend fun fetchNewsFeed() = emptyList<RssItemDto>()
        override suspend fun fetchLatestFeed() = emptyList<RssItemDto>()
        override suspend fun fetchArticleHtml(url: String): String? = null
        override suspend fun fetchArchivePage(page: Int) = emptyList<ArchiveItemDto>()
        override suspend fun fetchArticleDate(url: String): Instant? = null
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var notificationsRepo: NotificationsRepositoryImpl
    private val settings = FakeSettings(classId = null, reminders = ReminderSettings(ReminderMode.EVERY, 10))
    private val timetableSource = Timetable()

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() = db.close()

    private fun classSelection(): ClassSelection {
        val t = TestCoordinator(context, db, settings, timetableSource, Subs(), Anns(), object : pl.zse.bydgoszcz.elektron.work.NotificationSink {
            override suspend fun postSubstitution(sub: pl.zse.bydgoszcz.elektron.domain.model.Substitution, originalSubject: String?) {}
            override suspend fun postAnnouncement(ann: pl.zse.bydgoszcz.elektron.domain.model.Announcement) {}
            override suspend fun postGeneric(title: String, body: String, deepLink: String?) {}
        })
        notificationsRepo = t.notificationsRepo
        return ClassSelection(t.coordinator)
    }

    /** ClassSelection działa we własnym zasięgu (Dispatchers.IO) - czekamy na warunek. */
    private suspend fun <T : Any> awaitValue(read: suspend () -> T?): T? =
        runCatching { withTimeout(10_000) { var v = read(); while (v == null) { delay(20); v = read() }; v } }.getOrNull()

    @Test
    fun reminderIsScheduledAfterNewClassPlanIsDownloaded() = runBlocking {
        // Dawniej przypomnienia przeliczały się tylko przy samej zmianie klasy - zanim był
        // jej plan - więc do najbliższego syncu w tle nie było żadnego przypomnienia.
        val am = shadowOf(context.getSystemService(AlarmManager::class.java))
        classSelection().select("o3")
        assertNotNull(awaitValue { am.nextScheduledAlarm })
    }

    @Test
    fun failedPlanShowsPolishMessageInsteadOfRawException() = runBlocking {
        timetableSource.error = UnknownHostException("Unable to resolve host \"plan.zse.bydgoszcz.pl\"")
        classSelection().select("o3")
        val error = awaitValue { notificationsRepo.observeLastSyncError().first() }.orEmpty()
        assertTrue(error, error.contains(SyncErrors.OFFLINE))
        assertFalse(error, error.contains("Unable to resolve host"))
    }
}
