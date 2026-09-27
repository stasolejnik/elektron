package pl.zse.bydgoszcz.elektron.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.zse.bydgoszcz.elektron.domain.model.Announcement

interface AnnouncementsRepository {
    suspend fun syncAll(): Result<Unit>
    fun observeAll(): Flow<List<Announcement>>
    suspend fun loadFullArticle(id: String): Result<Unit>
    suspend fun getAll(): List<Announcement>
    /** Zapis pojedynczego wpisu (np. z pusha FCM). */
    suspend fun upsertOne(ann: Announcement)
}
