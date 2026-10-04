package pl.zse.bydgoszcz.elektron.data.repository

import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.zse.bydgoszcz.elektron.data.local.AnnouncementDao
import pl.zse.bydgoszcz.elektron.data.local.AnnouncementEntity
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.mapper.AnnouncementMapper
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.AnnouncementSource
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementsRepositoryImpl @Inject constructor(
    private val source: AnnouncementsSource,
    private val dao: AnnouncementDao,
    private val db: AppDatabase
) : AnnouncementsRepository {

    override suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val (newsResult, latestResult) = coroutineScope {
                val news = async { runCatchingCancellable { source.fetchNewsFeed() } }
                val latest = async { runCatchingCancellable { source.fetchLatestFeed() } }
                news.await() to latest.await()
            }
            // Oba kanały z błędem (np. brak internetu) - to porażka synchronizacji, nie "pusto".
            // Dawniej sync kończył się sukcesem bez danych i aplikacja uznawała ogłoszenia za
            // aktualne. Zapisane ogłoszenia zostają nietknięte.
            if (newsResult.isFailure && latestResult.isFailure) {
                throw newsResult.exceptionOrNull() ?: java.io.IOException("Ogłoszenia: brak połączenia")
            }
            val news = newsResult.getOrElse { Log.w(TAG, "RSS_NEWS padł", it); emptyList() }
            val latest = latestResult.getOrElse { Log.w(TAG, "RSS_LATEST padł", it); emptyList() }
            val all = news + latest
            if (all.isEmpty()) {
                Log.w(TAG, "Oba kanały puste — nie nadpisuję")
                return@runCatchingCancellable
            }
            val freshEntities = all.mapNotNull(AnnouncementMapper::toEntity).associateBy { it.id }.values.toList()
            // REPLACE nadpisywał pobraną treść artykułu (fullHtml) przy każdym syncu —
            // scalamy z tym, co już jest w Room, zanim wstawimy.
            db.withTransaction {
                val existingById = dao.getByIds(freshEntities.map { it.id }).associateBy { it.id }
                val entities = freshEntities.map { fresh ->
                    val existing = existingById[fresh.id]
                    if (existing == null) fresh
                    else fresh.copy(fullHtml = existing.fullHtml ?: fresh.fullHtml, isFavorite = existing.isFavorite)
                }
                dao.upsertAll(entities.filter { existingById[it.id] != it })
                dao.deleteDevEntries()
            }
            // 2 lata (dawniej 180 dni) — inaczej sync kasowałby starsze ogłoszenia
            // wczytane z archiwum przez "Pokaż więcej".
            dao.deleteOlderThan(Instant.now().minusSeconds(60L * 60 * 24 * 730).epochSecond)
        }
    }

    override suspend fun deleteSimulated(): Int = withContext(Dispatchers.IO) { dao.deleteDevEntries() }

    override fun observeAll(): Flow<List<Announcement>> =
        dao.observeAll().map { list -> list.map(AnnouncementMapper::toDomain) }.flowOn(Dispatchers.Default)

    override fun observeLatest(limit: Int): Flow<List<Announcement>> =
        dao.observeLatest(limit).map { list -> list.map(AnnouncementMapper::toDomain) }.flowOn(Dispatchers.Default)

    override suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }


    override fun observeById(id: String): Flow<Announcement?> =
        dao.observeById(id).map { it?.let(AnnouncementMapper::toDomain) }.flowOn(Dispatchers.Default)

    override suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) { dao.toggleFavorite(id) }

    override suspend fun clearFavorites(): Int = withContext(Dispatchers.IO) { dao.clearFavorites() }

    private val articleMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun loadFullArticle(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            articleMutex.withLock {
                val current = dao.getById(id) ?: throw java.io.IOException("Brak zapisanego ogłoszenia")
                if (!current.fullHtml.isNullOrBlank()) return@withLock
                val html = source.fetchArticleHtml(current.url)
                if (html.isNullOrBlank()) throw pl.zse.bydgoszcz.elektron.domain.model.ArticleContentException()
                dao.updateArticle(id, html)
            }
        }
    }

    override suspend fun getAll(): List<Announcement> = withContext(Dispatchers.IO) {
        dao.observeAll().first().map(AnnouncementMapper::toDomain)
    }

    // Push FCM może przyjść o ogłoszeniu, które już jest w bazie — scalamy z istniejącym
    // wpisem (np. pobraną treścią artykułu), tak jak w syncAll().
    override suspend fun upsertOne(ann: Announcement) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = dao.getById(ann.id)
            dao.upsertAll(listOf(AnnouncementEntity(
                id = ann.id,
                title = ann.title,
                url = ann.url,
                publishedAtEpochSeconds = ann.publishedAt.epochSecond,
                excerpt = ann.excerpt,
                coverImageUrl = ann.coverImageUrl,
                fullHtml = existing?.fullHtml ?: ann.fullHtml,
                isRead = false, // kolumna nieużywana (stan "przeczytane" usunięty w 0.3.1)
                source = ann.source.name,
                isFavorite = existing?.isFavorite ?: ann.isFavorite
            )))
        }
    }

    // Następna strona archiwum do pobrania (w pamięci procesu; po restarcie szacowana z bazy).
    private var nextArchivePage: Int? = null
    private val archiveMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun loadOlder(): Result<AnnouncementsRepository.OlderResult> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            archiveMutex.withLock {
                // Pierwsze strony archiwum pokrywają się z RSS. Startujemy od strony wynikającej
                // z liczby zapisanych ogłoszeń (5 wpisów na stronę), z zapasem 2 stron.
                var page = nextArchivePage ?: maxOf(1, dao.count() / ARCHIVE_PAGE_SIZE - 2)
                var added = 0
                var pagesChecked = 0
                while (added == 0 && pagesChecked < MAX_PAGES_PER_CALL) {
                    val items = source.fetchArchivePage(page)
                    pagesChecked++
                    if (items.isEmpty()) {
                        nextArchivePage = page
                        return@withLock AnnouncementsRepository.OlderResult(0, exhausted = true)
                    }
                    val known = dao.existingUrls(items.map { it.url }).toSet()
                    val fresh = items.filter { it.url !in known }
                    if (fresh.isNotEmpty()) {
                        // Daty tylko dla nowych wpisów, równolegle (najwyżej 5 zapytań).
                        val dates = coroutineScope {
                            fresh.map { item -> async { runCatchingCancellable { source.fetchArticleDate(item.url) }.getOrNull() } }
                                .awaitAll()
                        }
                        val entities = fresh.mapIndexed { i, item ->
                            AnnouncementEntity(
                                id = item.url,
                                title = item.title,
                                url = item.url,
                                // Bez daty: tuż przed najstarszym znanym, żeby zachować kolejność listy.
                                publishedAtEpochSeconds = (dates[i] ?: fallbackDate()).epochSecond,
                                excerpt = item.excerpt,
                                coverImageUrl = item.imageUrl,
                                fullHtml = null,
                                isRead = false,
                                source = AnnouncementSource.RSS_NEWS.name
                            )
                        }
                        dao.upsertAll(entities)
                        added = entities.size
                    }
                    page++
                }
                nextArchivePage = page
                AnnouncementsRepository.OlderResult(added, exhausted = false)
            }
        }
    }

    private suspend fun fallbackDate(): Instant =
        dao.oldestEpochSeconds()?.let { Instant.ofEpochSecond(it - 60) } ?: Instant.now()

    companion object {
        private const val TAG = "AnnouncementsRepositoryImpl"
        private const val ARCHIVE_PAGE_SIZE = 5
        private const val MAX_PAGES_PER_CALL = 6
    }
}
