package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
        var article: String? = "<p>Treść offline</p>"
        var articleCalls = 0
        var articleGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        val requestedPages = mutableListOf<Int>()
        override suspend fun fetchNewsFeed(): List<RssItemDto> { newsError?.let { throw it }; return news }
        override suspend fun fetchLatestFeed(): List<RssItemDto> { latestError?.let { throw it }; return latest }
        override suspend fun fetchArticleHtml(url: String): String? {
            articleCalls++
            articleGate?.await()
            return article
        }
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

    @Test fun overlappingFeedsDoNotRewriteIdenticalAnnouncement() = runTest {
        val item = rss("https://zse/a")
        source.news = listOf(item)
        source.latest = listOf(item.copy(source = RssItemDto.Source.RSS_LATEST))
        assertTrue(repo.syncAll().isSuccess)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE audit_writes (value INTEGER)")
        sql.execSQL("CREATE TRIGGER audit_ann AFTER INSERT ON announcements BEGIN INSERT INTO audit_writes VALUES (1); END")
        assertTrue(repo.syncAll().isSuccess)
        sql.query("SELECT COUNT(*) FROM audit_writes").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        assertEquals("RSS_LATEST", db.announcementDao().getById(item.guid)?.source)
    }
    @Test fun favoriteSurvivesRssPushAndRetention() = runTest {
        val id = "https://zse/favorite"
        source.news = listOf(rss(id))
        repo.syncAll().getOrThrow()
        repo.toggleFavorite(id)
        repo.loadFullArticle(id).getOrThrow()
        repo.syncAll().getOrThrow()
        repo.upsertOne(repo.observeById(id).first()!!.copy(title = "Zmieniony", isFavorite = false))
        db.announcementDao().deleteOlderThan(Long.MAX_VALUE)
        val saved = repo.observeById(id).first()!!
        assertTrue(saved.isFavorite)
        assertEquals("<p>Treść offline</p>", saved.fullHtml)
        org.junit.Assert.assertNull(repo.observeAll().first().single().fullHtml)
        org.junit.Assert.assertNull(repo.observeLatest(1).first().single().fullHtml)
        repo.loadFullArticle(id).getOrThrow()
        assertEquals(1, source.articleCalls)
        repo.toggleFavorite(id)
        db.announcementDao().deleteOlderThan(Long.MAX_VALUE)
        org.junit.Assert.assertNull(db.announcementDao().getById(id))
    }

    @Test fun favoriteChangeDuringDownloadIsPreserved() = runTest {
        val id = "https://zse/a"
        source.news = listOf(rss(id))
        repo.syncAll().getOrThrow()
        source.articleGate = kotlinx.coroutines.CompletableDeferred()
        val job = async { repo.loadFullArticle(id).getOrThrow() }
        withTimeout(10_000) { while (source.articleCalls == 0) delay(10) }
        repo.toggleFavorite(id)
        source.articleGate!!.complete(Unit)
        job.await()
        assertTrue(repo.observeById(id).first()!!.isFavorite)
    }

    @Test fun clearFavoritesKeepsArticlesAndDoesNotRestoreBookmarksOnSync() = runTest {
        source.news = (1..15).map { rss("https://zse/$it") }
        repo.syncAll().getOrThrow()
        source.news.take(14).forEach { repo.toggleFavorite(it.guid) }
        val firstId = source.news.first().guid
        repo.loadFullArticle(firstId).getOrThrow()
        assertEquals(14, repo.clearFavorites())
        assertEquals(0, repo.clearFavorites())
        assertEquals(15, repo.count())
        assertTrue(repo.observeAll().first().none { it.isFavorite })
        assertEquals("<p>Treść offline</p>", repo.observeById(firstId).first()!!.fullHtml)
        repo.syncAll().getOrThrow()
        repo.upsertOne(repo.observeById(firstId).first()!!.copy(title = "Push", isFavorite = true))
        assertTrue(repo.observeAll().first().none { it.isFavorite })
        assertEquals(1, source.articleCalls)
    }

    @Test fun clearFavoritesDuringDownloadDoesNotRestoreBookmark() = runTest {
        val id = "https://zse/a"
        source.news = listOf(rss(id))
        repo.syncAll().getOrThrow()
        repo.toggleFavorite(id)
        source.articleGate = kotlinx.coroutines.CompletableDeferred()
        val job = async { repo.loadFullArticle(id).getOrThrow() }
        withTimeout(10_000) { while (source.articleCalls == 0) delay(10) }
        assertEquals(1, repo.clearFavorites())
        source.articleGate!!.complete(Unit)
        job.await()
        val article = repo.observeById(id).first()!!
        org.junit.Assert.assertFalse(article.isFavorite)
        assertEquals("<p>Treść offline</p>", article.fullHtml)
    }

    @Test fun failedArticleDownloadDoesNotLoseFavorite() = runTest {
        source.news = listOf(rss("https://zse/a"))
        repo.syncAll().getOrThrow()
        repo.toggleFavorite("https://zse/a")
        source.article = null
        assertTrue(repo.loadFullArticle("https://zse/a").isFailure)
        assertTrue(repo.observeById("https://zse/a").first()!!.isFavorite)
    }

}
