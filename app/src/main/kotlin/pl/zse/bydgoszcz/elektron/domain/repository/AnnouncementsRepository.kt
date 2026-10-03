package pl.zse.bydgoszcz.elektron.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.zse.bydgoszcz.elektron.domain.model.Announcement

interface AnnouncementsRepository {
    suspend fun syncAll(): Result<Unit>
    fun observeAll(): Flow<List<Announcement>>
    /** Najnowsze [limit] ogłoszeń (strona główna) — bez czytania całej tabeli. */
    fun observeLatest(limit: Int): Flow<List<Announcement>>
    suspend fun count(): Int
    suspend fun loadFullArticle(id: String): Result<Unit>
    suspend fun getAll(): List<Announcement>
    /** Zapis pojedynczego wpisu (np. z pusha FCM). */
    suspend fun upsertOne(ann: Announcement)

    /** Usuwa ogłoszenia symulowane w trybie dewelopera (id "dev_ann_..."). */
    suspend fun deleteSimulated(): Int = 0

    /**
     * Wczytuje starsze ogłoszenia z archiwum strony szkoły (RSS zawiera tylko najnowsze).
     * Wynik: liczba dodanych wpisów; [OlderResult.exhausted] = archiwum się skończyło.
     */
    suspend fun loadOlder(): Result<OlderResult>

    data class OlderResult(val added: Int, val exhausted: Boolean)
}
