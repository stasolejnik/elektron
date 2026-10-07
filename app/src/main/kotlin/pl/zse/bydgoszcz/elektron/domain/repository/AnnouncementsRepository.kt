package pl.zse.bydgoszcz.elektron.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.zse.bydgoszcz.elektron.domain.model.Announcement

interface AnnouncementsRepository {
    /** Pełny zapis artykułu; listy ogłoszeń pomijają treść HTML. */
    fun observeById(id: String): Flow<Announcement?>
    suspend fun toggleFavorite(id: String)
    /** Usuwa wszystkie zakładki, zachowując ogłoszenia i pobraną treść. */
    suspend fun clearFavorites(): Int
    suspend fun syncAll(): Result<Unit>
    fun observeAll(): Flow<List<Announcement>>
    /** Najnowsze [limit] ogłoszeń (strona główna) — bez czytania całej tabeli. */
    fun observeLatest(limit: Int): Flow<List<Announcement>>
    suspend fun count(): Int
    suspend fun loadFullArticle(id: String): Result<Unit>
    suspend fun refreshFullArticle(id: String): Result<Unit> = loadFullArticle(id)
    suspend fun getAll(): List<Announcement>
    /** Zapis pojedynczego wpisu (np. z pusha FCM). */
    suspend fun upsertOne(ann: Announcement)

    /** Usuwa ogłoszenia symulowane w trybie dewelopera (id "dev_ann_..."). */
    suspend fun deleteSimulated(): Int = 0

    /**
     * Wczytuje starsze ogłoszenia z archiwum strony szkoły (RSS zawiera tylko najnowsze).
     * Wynik: liczba dodanych wpisów; [OlderResult.exhausted] = archiwum się skończyło.
     */
    val archiveGeneration: Flow<Long> get() = kotlinx.coroutines.flow.flowOf(0L)
    /** Serialize cache removal with archive loading and invalidate pagination. */
    suspend fun resetArchive(clear: suspend () -> Unit = {}) { clear() }
    suspend fun loadOlder(): Result<OlderResult>

    data class OlderResult(val added: Int, val exhausted: Boolean)
}
