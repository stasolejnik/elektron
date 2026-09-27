package pl.zse.bydgoszcz.elektron.data.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        runCatching {
            val news = runCatching { source.fetchNewsFeed() }.getOrElse { Log.w(TAG, "RSS_NEWS padł", it); emptyList() }
            val latest = runCatching { source.fetchLatestFeed() }.getOrElse { Log.w(TAG, "RSS_LATEST padł", it); emptyList() }
            val all = news + latest
            if (all.isEmpty()) {
                Log.w(TAG, "Oba kanały puste — nie nadpisuję")
                return@runCatching
            }
            val freshEntities = all.mapNotNull(AnnouncementMapper::toEntity)
            // REPLACE nadpisywał pobraną treść artykułu (fullHtml) przy każdym syncu —
            // scalamy z tym, co już jest w Room, zanim wstawimy.
            val existingById = dao.getByIds(freshEntities.map { it.id }).associateBy { it.id }
            val entities = freshEntities.map { fresh ->
                val existing = existingById[fresh.id]
                if (existing == null) fresh
                else fresh.copy(fullHtml = existing.fullHtml ?: fresh.fullHtml)
            }
            db.withTransaction {
                dao.upsertAll(entities)
                dao.deleteDevEntries()
            }
            dao.deleteOlderThan(Instant.now().minusSeconds(60L * 60 * 24 * 180).epochSecond)
        }
    }

    override fun observeAll(): Flow<List<Announcement>> =
        dao.observeAll().map { list -> list.map(AnnouncementMapper::toDomain) }


    override suspend fun loadFullArticle(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val current = dao.getById(id) ?: return@runCatching
            if (!current.fullHtml.isNullOrBlank()) return@runCatching
            val html = source.fetchArticleHtml(current.url)
            if (html.isNullOrBlank()) return@runCatching
            dao.upsertAll(listOf(current.copy(fullHtml = html)))
        }
    }

    override suspend fun getAll(): List<Announcement> = withContext(Dispatchers.IO) {
        dao.observeAll().first().map(AnnouncementMapper::toDomain)
    }

    // Push FCM może przyjść o ogłoszeniu, które już jest w bazie — scalamy z istniejącym
    // wpisem (np. pobraną treścią artykułu), tak jak w syncAll().
    override suspend fun upsertOne(ann: Announcement) = withContext(Dispatchers.IO) {
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
            source = ann.source.name
        )))
    }

    companion object { private const val TAG = "AnnouncementsRepositoryImpl" }
}
