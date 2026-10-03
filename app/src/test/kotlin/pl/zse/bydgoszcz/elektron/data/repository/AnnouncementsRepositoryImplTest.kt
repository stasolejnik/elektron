package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AnnouncementEntity
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnouncementsRepositoryImplTest {

    private class FakeSource : AnnouncementsSource {
        var news: List<RssItemDto> = emptyList()
        var latest: List<RssItemDto> = emptyList()
        var newsError: Exception? = null
        var latestError: Exception? = null
        var archive: Map<Int, List<ArchiveItemDto>> = emptyMap()
        val requestedPages = mutableListOf<Int>()
        override suspend fun fetchNewsFeed(): List<RssItemDto> { newsError?.let { throw it }; return news }
        override suspend fun fetchLatestFeed(): List<RssItemDto> { latestError?.let { throw it }; return latest }
        override suspend fun fetchArticleHtml(url: String): String? = null
        override suspend fun fetchArchivePage(page: Int): List<ArchiveItemDto> {
            requestedPages += page
            return archive[page].orEmpty()
        }
        override suspend fun fetchArticleDate(url: String): Instant? = Instant.parse("2025-01-10T10:00:00Z")
    }

    private lateinit var db: AppDatabase
    private val source = FakeSource()
    private lateinit var repo: AnnouncementsRepositoryImpl

    private fun rss(guid: String) = RssItemDto(guid, "Tytuł $guid", guid, "Mon, 28 Sep 2026 10:00:00 +0200", "Opis", RssItemDto.Source.RSS_NEWS)
    private fun archiveItem(url: String) = ArchiveItemDto(url, "Archiwalne $url", "Opis", null)

    @Before fun setUp() {
        db = inMemoryDb()
        repo = AnnouncementsRepositoryImpl(source, db.announcementDao(), db)
    }

    @After fun tearDown() = db.close()

    @Test
    fun syncKeepsDownloadedArticleContent() = runTest {
        // REPLACE przy syncu kasował pobraną treść artykułu.
        db.announcementDao().upsertAll(listOf(AnnouncementEntity(
            id = "https://zse/a", title = "A", url = "https://zse/a", publishedAtEpochSeconds = 1_800_000_000,
            excerpt = null, coverImageUrl = null, fullHtml = "<p>treść</p>", isRead = false, source = "RSS_NEWS"
        )))
        source.news = listOf(rss("https://zse/a"))
        repo.syncAll()
        assertEquals("<p>treść</p>", db.announcementDao().getById("https://zse/a")?.fullHtml)
    }

    @Test
    fun oneFailedChannelIsNotAFailureOfAll() = runTest {
        // Kanał RSS z błędem HTTP przy działającym drugim: ogłoszenia z drugiego się zapisują.
        source.newsError = java.io.IOException("Ogłoszenia: HTTP 503 dla rss.xml")
        source.latest = listOf(rss("https://zse/z-latest"))
        assertTrue(repo.syncAll().isSuccess)
        assertEquals(1, db.announcementDao().count())
    }

    @Test
    fun bothChannelsFailedIsAFailure() = runTest {
        source.newsError = java.io.IOException("Ogłoszenia: HTTP 503 dla rss.xml")
        source.latestError = java.io.IOException("Ogłoszenia: HTTP 503 dla rsslatest.xml")
        assertTrue(repo.syncAll().isFailure)
    }

    @Test
    fun cancellationIsNotReportedAsFailedSync() = runTest {
        // runCatching łapał anulowanie: anulowany sync wracał jako "porażka" zamiast się zatrzymać.
        source.newsError = kotlinx.coroutines.CancellationException("anulowano")
        source.latestError = kotlinx.coroutines.CancellationException("anulowano")
        try {
            repo.syncAll()
            org.junit.Assert.fail("Anulowanie musi lecieć dalej")
        } catch (_: kotlinx.coroutines.CancellationException) {
        }
    }

    @Test
    fun loadOlderSkipsKnownUrlsAndDetectsEnd() = runTest {
        source.news = listOf(rss("https://zse/nowe"))
        repo.syncAll()
        source.archive = mapOf(1 to listOf(archiveItem("https://zse/nowe"), archiveItem("https://zse/stare")))
        val first = repo.loadOlder().getOrThrow()
        assertEquals(1, first.added)                       // "nowe" było już z RSS
        val second = repo.loadOlder().getOrThrow()          // strona 2 pusta = koniec archiwum
        assertTrue(second.exhausted)
        assertEquals(2, db.announcementDao().count())
    }
}
