package pl.zse.bydgoszcz.elektron.domain.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonCellDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonGroupDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsPage
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.testutil.TestCoordinator
import pl.zse.bydgoszcz.elektron.work.NotificationSink
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Kolejność pobierania przy pierwszej synchronizacji: zastępstwa bez klasy potrzebują planu i listy nauczycieli. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOrderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val sink = object : NotificationSink {
        override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) {}
        override suspend fun postAnnouncement(ann: Announcement) {}
        override suspend fun postGeneric(title: String, body: String, deepLink: String?) {}
    }

    private val anns = object : AnnouncementsSource {
        override suspend fun fetchNewsFeed() = emptyList<RssItemDto>()
        override suspend fun fetchLatestFeed() = emptyList<RssItemDto>()
        override suspend fun fetchArticleHtml(url: String): String? = null
        override suspend fun fetchArchivePage(page: Int) = emptyList<ArchiveItemDto>()
        override suspend fun fetchArticleDate(url: String): Instant? = null
    }

    @Test fun classlessSubstitutionWaitsForTeacherListOnFirstSync() = runBlocking {
        val db = inMemoryDb()
        val date = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1)
        val raw = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val sidebarGate = CompletableDeferred<Unit>()
        val subsFetchedEarly = java.util.concurrent.atomic.AtomicBoolean(false)
        // Plan podaje tylko skrót nauczyciela ("JK"), pełne nazwisko jest na liście nauczycieli (sidebar).
        val school = object : TimetableSource {
            override suspend fun fetchSidebar(): List<ClassListItemDto> {
                sidebarGate.await()
                return listOf(ClassListItemDto("n1", ClassListItemDto.Kind.TEACHER, "J.Kowalski (JK)", "J.Kowalski",
                    "https://plan.zse.bydgoszcz.pl/plany/n1.html"))
            }
            override suspend fun fetchTimetable(classId: String) = TimetableDto(classId, "1D", null, null,
                listOf(LessonCellDto(3, "09:50", "10:35", 1, listOf(LessonGroupDto("wf-2/2", "JK", null, "Hala", null, null, null)), null)))
        }
        val subs = object : SubstitutionsSource {
            override suspend fun fetchSubstitutions() = emptyList<SubstitutionDto>()
            override suspend fun fetchPage(): SubstitutionsPage {
                if (!sidebarGate.isCompleted) subsFetchedEarly.set(true)
                return SubstitutionsPage(setOf(raw), emptyList(), classless = listOf(
                    SubstitutionDto(raw, "Jan Kowalski", 3, "", null, "Zajęcia Świetlicowe", null, null, "Wychowanie fizyczne")))
            }
        }
        try {
            val t = TestCoordinator(context, db, FakeSettings(classId = "o3"), school, subs, anns, sink)
            val flight = async { t.coordinator.sync(SyncRequest.full(date)) }
            delay(300)
            sidebarGate.complete(Unit)
            val outcome = withTimeout(10_000) { flight.await() }
            assertTrue(outcome.failures.isEmpty())
            assertFalse("zastępstwa pobrane przed listą nauczycieli", subsFetchedEarly.get())
            assertEquals(1, t.substitutionsRepo.getAllFrom(date).size)
        } finally {
            sidebarGate.complete(Unit)
            db.close()
        }
    }
}
