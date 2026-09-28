package pl.zse.bydgoszcz.elektron.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
import pl.zse.bydgoszcz.elektron.data.repository.AnnouncementsRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.NotificationsRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.SubstitutionsRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.TimetableRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import pl.zse.bydgoszcz.elektron.domain.usecase.SyncAllUseCase
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * SyncWorker od początku do końca: prawdziwa baza, repozytoria i SyncAllUseCase;
 * podstawione tylko źródła sieciowe, ustawienia i odbiornik powiadomień.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {

    // --- Podstawione zależności ---

    private class Timetable : TimetableSource {
        override suspend fun fetchSidebar() = listOf(
            ClassListItemDto("o3", ClassListItemDto.Kind.CLASS, "1D 1D PBŚ", "1D", "https://plan.zse.bydgoszcz.pl/plany/o3.html")
        )
        override suspend fun fetchTimetable(classId: String) = TimetableDto(classId, "1D 1D PBŚ", null, null,
            listOf(LessonCellDto(2, "08:55", "09:40", 1, listOf(LessonGroupDto("mat", "Ch", null, "105", null, null, null)), null)))
    }

    private class Subs(var items: List<SubstitutionDto> = emptyList()) : SubstitutionsSource {
        override suspend fun fetchSubstitutions() = items
    }

    private class Anns : AnnouncementsSource {
        override suspend fun fetchNewsFeed() = emptyList<RssItemDto>()
        override suspend fun fetchLatestFeed() = emptyList<RssItemDto>()
        override suspend fun fetchArticleHtml(url: String): String? = null
        override suspend fun fetchArchivePage(page: Int) = emptyList<ArchiveItemDto>()
        override suspend fun fetchArticleDate(url: String): Instant? = null
    }

    private class RecordingSink : NotificationSink {
        val substitutions = mutableListOf<Substitution>()
        override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) { substitutions += sub }
        override suspend fun postAnnouncement(ann: Announcement) {}
        override suspend fun postGeneric(title: String, body: String, deepLink: String?) {}
    }

    private class FakeSettings : SettingsRepository {
        val notifySubs = MutableStateFlow(true)
        override val selectedClassId: Flow<String?> = MutableStateFlow("o3")
        override suspend fun setSelectedClassId(id: String) {}
        override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override val dynamicColor: Flow<Boolean> = flowOf(false)
        override suspend fun setDynamicColor(enabled: Boolean) {}
        override val notificationsSubstitutions: Flow<Boolean> = notifySubs
        override suspend fun setNotificationsSubstitutions(enabled: Boolean) { notifySubs.value = enabled }
        override val notificationsAnnouncements: Flow<Boolean> = flowOf(false)
        override suspend fun setNotificationsAnnouncements(enabled: Boolean) {}
        override val showNextLesson: Flow<Boolean> = flowOf(true)
        override suspend fun setShowNextLesson(enabled: Boolean) {}
        override val showSubstitutions: Flow<Boolean> = flowOf(true)
        override suspend fun setShowSubstitutions(enabled: Boolean) {}
        override val showAnnouncements: Flow<Boolean> = flowOf(true)
        override suspend fun setShowAnnouncements(enabled: Boolean) {}
        override val smartStart: Flow<Boolean> = flowOf(true)
        override suspend fun setSmartStart(enabled: Boolean) {}
        override val lastSeenVersionCode: Flow<Int> = flowOf(0)
        override suspend fun setLastSeenVersionCode(code: Int) {}
        override fun groupSelections(classId: String): Flow<Map<String, String>> = flowOf(emptyMap())
        override val activeGroupSelections: Flow<Map<String, String>> = flowOf(emptyMap())
        override suspend fun setGroupSelection(classId: String, subject: String, choice: String?) {}
        override val groupsConfiguredFor: Flow<String?> = flowOf("o3")
        override suspend fun setGroupsConfiguredFor(classId: String) {}
    }

    // --- Środowisko ---

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val tomorrow = LocalDate.now().plusDays(1)
    private lateinit var db: AppDatabase
    private val subsSource = Subs()
    private val sink = RecordingSink()
    private val settings = FakeSettings()

    private fun sub(lesson: Int, cls: String = "1D") =
        SubstitutionDto(tomorrow.format(fmt), "Y.Nauczyciel", lesson, cls, null, "316", "X.Zastępca", null)

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() = db.close()

    private fun runWorker(): ListenableWorker.Result {
        val timetableRepo = TimetableRepositoryImpl(Timetable(), db.schoolClassDao(), db.teacherDao(), db.roomDao(),
            db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)
        val substitutionsRepo = SubstitutionsRepositoryImpl(subsSource, db.substitutionDao(), db)
        val announcementsRepo = AnnouncementsRepositoryImpl(Anns(), db.announcementDao(), db)
        val notificationsRepo = NotificationsRepositoryImpl(db.notificationDao(), db.syncStateDao())
        val syncAll = SyncAllUseCase(timetableRepo, substitutionsRepo, announcementsRepo, notificationsRepo)
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                SyncWorker(appContext, workerParameters, syncAll, settings, substitutionsRepo, announcementsRepo,
                    notificationsRepo, sink, timetableRepo, WidgetUpdater(appContext))
        }
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build()
        return runBlocking { worker.doWork() }
    }

    @Test
    fun firstSyncOnlyRecordsStateWithoutNotifications() {
        subsSource.items = listOf(sub(1))
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertTrue("Pierwszy sync nie może zalać powiadomieniami", sink.substitutions.isEmpty())
    }

    @Test
    fun newSubstitutionForOwnClassNotifiesOnce() {
        subsSource.items = listOf(sub(1))
        runWorker()                                            // stan startowy
        subsSource.items = listOf(sub(1), sub(2), sub(3, cls = "2A"))
        runWorker()
        assertEquals(listOf(2), sink.substitutions.map { it.lessonNumber })   // 2A pominięte
        runWorker()                                            // to samo jeszcze raz
        assertEquals("Bez duplikatów przy kolejnym syncu", 1, sink.substitutions.size)
    }

    @Test
    fun disabledNotificationsPostNothing() {
        subsSource.items = listOf(sub(1))
        runWorker()
        settings.notifySubs.value = false
        subsSource.items = listOf(sub(1), sub(2))
        runWorker()
        assertTrue(sink.substitutions.isEmpty())
    }
}
