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
        var dateGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        val dateStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
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
        override suspend fun fetchArticleDate(url: String): Instant? {
            dateStarted.complete(Unit)
            dateGate?.await()
            return Instant.parse("2025-01-10T10:00:00Z")
        }
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

    @Test fun archiveDoesNotOverwriteArticleSavedWhileItsDateWasDownloading() = kotlinx.coroutines.runBlocking {
        val id = "https://zse/concurrent"
        source.archive = mapOf(1 to listOf(archiveItem(id)))
        source.dateGate = kotlinx.coroutines.CompletableDeferred()
        val loading = async { repo.loadOlder().getOrThrow() }
        withTimeout(10_000) { source.dateStarted.await() }
        source.news = listOf(rss(id))
        repo.syncAll().getOrThrow()
        repo.toggleFavorite(id)
        repo.loadFullArticle(id).getOrThrow()
        val saved = db.announcementDao().getById(id)!!
        source.dateGate!!.complete(Unit)
        assertEquals(0, loading.await().added)
        assertEquals(saved, db.announcementDao().getById(id))
        assertTrue(db.announcementDao().getById(id)!!.isFavorite)
    }

    @Test fun favoritesMigrateAndRestoreIntoAnEmptyDatabaseWithoutNetwork() = kotlinx.coroutines.runBlocking {
        val file = java.io.File.createTempFile("favorites-backup", ".json").apply { delete() }
        val store = FavoriteAnnouncementsStore(file)
        val id = "https://zse/backup"
        val existing = AnnouncementEntity(id, "Zapisane", id, 1_800_000_000, "Opis", null, "<b>Offline</b>", false, "RSS_NEWS", true)
        db.announcementDao().upsertAll(listOf(existing))
        try {
            repo = AnnouncementsRepositoryImpl(source, db.announcementDao(), db, store)
            repo.observeAll().first()
            assertEquals(existing, store.read()!!.single())
            assertTrue(file.setLastModified(1_000_000L))
            store.write(listOf(existing))
            assertEquals(1_000_000L, file.lastModified())
            db.announcementDao().clearFavorites()
            db.announcementDao().deleteOlderThan(Long.MAX_VALUE)
            repo = AnnouncementsRepositoryImpl(source, db.announcementDao(), db, FavoriteAnnouncementsStore(file))
            assertTrue(repo.observeAll().first().single().isFavorite)
            assertEquals("<b>Offline</b>", repo.observeById(id).first()!!.fullHtml)
            repo.clearFavorites()
            db.announcementDao().deleteOlderThan(Long.MAX_VALUE)
            repo = AnnouncementsRepositoryImpl(source, db.announcementDao(), db, FavoriteAnnouncementsStore(file))
            assertTrue(repo.observeAll().first().isEmpty())
        } finally { file.delete() }
    }
    @Test fun resetArchiveRestartsPaginationEvenAfterExhaustion() = kotlinx.coroutines.runBlocking {
        source.archive = mapOf(1 to listOf(archiveItem("https://zse/1")), 2 to listOf(archiveItem("https://zse/2")))
        assertEquals(1, repo.loadOlder().getOrThrow().added)
        assertEquals(1, repo.loadOlder().getOrThrow().added)
        assertTrue(repo.loadOlder().getOrThrow().exhausted)
        val generation = repo.archiveGeneration.first()
        repo.resetArchive { db.announcementDao().deleteOlderThan(Long.MAX_VALUE) }
        assertTrue(repo.archiveGeneration.first() > generation)
        assertEquals(1, repo.loadOlder().getOrThrow().added)
        assertEquals(1, source.requestedPages.last())
    }

    @Test fun failedFavoriteMigrationLeavesOriginalArticleUntouched() = kotlinx.coroutines.runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("favorite-blocked").toFile()
        val blocked = java.io.File(directory, "parent").apply { writeText("Not a directory") }
        val store = FavoriteAnnouncementsStore(java.io.File(blocked, "favorites.json"))
        val existing = AnnouncementEntity("https://zse/a", "A", "https://zse/a", 1_800_000_000, null, null, "<p>Offline</p>", false, "RSS_NEWS", true)
        db.announcementDao().upsertAll(listOf(existing))
        try {
            repo = AnnouncementsRepositoryImpl(source, db.announcementDao(), db, store)
            assertTrue(repo.observeAll().first().single().isFavorite)
            assertEquals(existing, db.announcementDao().getById(existing.id))
        } finally { directory.deleteRecursively() }
    }

    @Test fun failedBookmarkRemovalKeepsDatabaseAndBackupConsistent() = failedBookmarkWrite(clearAll = false, initiallyFavorite = true)
    @Test fun failedBookmarkAdditionKeepsDatabaseAndBackupConsistent() = failedBookmarkWrite(clearAll = false, initiallyFavorite = false)
    @Test fun failedClearFavoritesKeepsDatabaseAndBackupConsistent() = failedBookmarkWrite(clearAll = true, initiallyFavorite = true)

    private fun failedBookmarkWrite(clearAll: Boolean, initiallyFavorite: Boolean) = kotlinx.coroutines.runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("favorite-write-failure").toFile()
        val parent = java.io.File(directory, "store").apply { mkdirs() }
        val savedParent = java.io.File(directory, "saved-store")
        val file = java.io.File(parent, "favorites.json")
        val store = FavoriteAnnouncementsStore(file)
        val existing = AnnouncementEntity("https://zse/a", "A", "https://zse/a", 1_800_000_000, null, null, "<p>Offline</p>", false, "RSS_NEWS", initiallyFavorite)
        db.announcementDao().upsertAll(listOf(existing))
        try {
            repo = AnnouncementsRepositoryImpl(source, db.announcementDao(), db, store)
            repo.observeAll().first() // Finish migration before simulating an unavailable directory.
            val originalBackup = file.readText()
            assertTrue(parent.renameTo(savedParent))
            parent.writeText("Not a directory")
            val failure = runCatching { if (clearAll) repo.clearFavorites() else repo.toggleFavorite(existing.id) }
            assertTrue(failure.exceptionOrNull() is java.io.IOException)
            assertEquals(existing, db.announcementDao().getById(existing.id))
            assertEquals(initiallyFavorite, repo.observeById(existing.id).first()!!.isFavorite)
            assertTrue(parent.delete())
            assertTrue(savedParent.renameTo(parent))
            assertEquals(originalBackup, file.readText())
            // Retrying after storage recovers must change both copies exactly once.
            if (clearAll) assertEquals(1, repo.clearFavorites()) else repo.toggleFavorite(existing.id)
            val expected = !initiallyFavorite
            assertEquals(expected, db.announcementDao().getById(existing.id)!!.isFavorite)
            assertEquals(if (expected) listOf(existing.id) else emptyList<String>(), store.read()!!.map { it.id })
            repo = AnnouncementsRepositoryImpl(source, db.announcementDao(), db, FavoriteAnnouncementsStore(file))
            assertEquals(expected, repo.observeById(existing.id).first()!!.isFavorite)
        } finally { directory.deleteRecursively() }
    }

    @Test fun explicitRefreshReplacesIncompleteCacheAndFailurePreservesFullArticle() = kotlinx.coroutines.runBlocking {
        source.news = listOf(rss("https://zse/refresh"))
        repo.syncAll().getOrThrow()
        source.article = "<p>Stary fragment.</p>"
        repo.loadFullArticle("https://zse/refresh").getOrThrow()
        source.article = "<p>Pełny pierwszy akapit.</p><p>Pełny drugi akapit.</p>"
        repo.loadFullArticle("https://zse/refresh").getOrThrow()
        assertEquals(1, source.articleCalls)
        repo.refreshFullArticle("https://zse/refresh").getOrThrow()
        assertEquals(2, source.articleCalls)
        assertTrue(repo.observeById("https://zse/refresh").first()!!.fullHtml!!.contains("Pełny drugi"))
        source.article = null
        assertTrue(repo.refreshFullArticle("https://zse/refresh").isFailure)
        assertTrue(repo.observeById("https://zse/refresh").first()!!.fullHtml!!.contains("Pełny drugi"))
    }

    @Test fun scatteredFavoritesNeverCauseArchivePagesToBeSkipped() = runTest {
        db.announcementDao().upsertAll((1..30).map { n -> AnnouncementEntity(
            id = "favorite-$n", title = "Ulubione", url = "https://zse/favorite-$n", publishedAtEpochSeconds = 1_800_000_000,
            excerpt = null, coverImageUrl = null, fullHtml = null, isRead = false, source = "RSS_NEWS", isFavorite = true) })
        source.archive = mapOf(1 to listOf(archiveItem("https://zse/missing-first-page")))
        assertTrue(repo.loadOlder().isSuccess)
        assertEquals(listOf(1), source.requestedPages)
        assertTrue(repo.getAll().any { it.url == "https://zse/missing-first-page" })
    }

}
