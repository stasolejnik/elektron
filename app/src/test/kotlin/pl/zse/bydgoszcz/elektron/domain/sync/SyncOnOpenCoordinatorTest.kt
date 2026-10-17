package pl.zse.bydgoszcz.elektron.domain.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.testutil.TestCoordinator
import pl.zse.bydgoszcz.elektron.work.NotificationSink
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Otwarcie aplikacji pobiera zastępstwa, szybki powrót (w ciągu 2 min) już nie. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOnOpenCoordinatorTest {
    @Test fun openingTwiceFetchesOnce() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = inMemoryDb()
        var fetches = 0
        val raw = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val subs = object : SubstitutionsSource {
            override suspend fun fetchSubstitutions(): List<SubstitutionDto> {
                fetches++
                return listOf(SubstitutionDto(raw, "Y.Nauczyciel", 1, "1D", null, "105", "X.Zastępca", null))
            }
        }
        val school = object : TimetableSource {
            override suspend fun fetchSidebar() = emptyList<ClassListItemDto>()
            override suspend fun fetchTimetable(classId: String) = TimetableDto(classId, "1D", null, null, emptyList())
        }
        val anns = object : AnnouncementsSource {
            override suspend fun fetchNewsFeed() = emptyList<RssItemDto>()
            override suspend fun fetchLatestFeed() = emptyList<RssItemDto>()
            override suspend fun fetchArticleHtml(url: String): String? = null
            override suspend fun fetchArchivePage(page: Int) = emptyList<ArchiveItemDto>()
            override suspend fun fetchArticleDate(url: String): Instant? = null
        }
        val sink = object : NotificationSink {
            override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) {}
            override suspend fun postAnnouncement(ann: Announcement) {}
            override suspend fun postGeneric(title: String, body: String, deepLink: String?) {}
        }
        try {
            val t = TestCoordinator(context, db, FakeSettings(classId = "o3"), school, subs, anns, sink)
            assertNotNull(t.coordinator.syncOnOpen())
            assertEquals(1, fetches)
            assertNull("powrót w ciągu 2 min - bez pobierania", t.coordinator.syncOnOpen())
            assertEquals(1, fetches)
            assertNotNull(t.coordinator.syncOnOpen(Instant.now().plusSeconds(180)))
            assertEquals(2, fetches)
        } finally { db.close() }
    }
}
