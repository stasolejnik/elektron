package pl.zse.bydgoszcz.elektron.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import java.time.LocalDate

interface SubstitutionsRepository {
    suspend fun syncAll(): Result<Unit>

    /** Usuwa zastępstwa symulowane w trybie dewelopera. Zwraca liczbę usuniętych. */
    suspend fun deleteSimulated(): Int = 0
    fun observeForDay(day: LocalDate): Flow<List<Substitution>>
    fun observeFrom(day: LocalDate): Flow<List<Substitution>>
    suspend fun getForClassAndDay(classShortName: String, day: LocalDate): List<Substitution>
    suspend fun getAllFrom(day: LocalDate): List<Substitution>
    /** Zapis pojedynczego wpisu (np. z pusha FCM). */
    suspend fun upsertOne(sub: Substitution)
}
