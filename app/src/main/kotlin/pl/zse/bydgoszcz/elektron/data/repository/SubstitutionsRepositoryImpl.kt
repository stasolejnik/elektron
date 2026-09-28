package pl.zse.bydgoszcz.elektron.data.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.local.SubstitutionDao
import pl.zse.bydgoszcz.elektron.data.local.SubstitutionEntity
import pl.zse.bydgoszcz.elektron.data.mapper.SubstitutionMapper
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubstitutionsRepositoryImpl @Inject constructor(
    private val source: SubstitutionsSource,
    private val dao: SubstitutionDao,
    private val db: AppDatabase
) : SubstitutionsRepository {

    override suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dtos = source.fetchSubstitutions()
            val entities = dtos.mapNotNull(SubstitutionMapper::toEntity)
            if (entities.isEmpty()) {
                Log.w(TAG, "Pusto — nie nadpisuję")
                return@runCatching
            }
            // Bug audytu #6: strona szkoły zawsze zwraca PEŁNY aktualny zestaw zastępstw
            // od dziś w przód. Samo upsertAll nigdy nie usuwało zastępstw, które szkoła
            // skasowała/cofnęła — zostawały w Room jako "duchy" aż do wygaśnięcia po 60 dniach.
            // Czyścimy więc dziś..przyszłość przed wstawieniem świeżego kompletu, atomowo.
            val today = LocalDate.now().toEpochDay()
            db.withTransaction {
                dao.deleteFromDay(today)
                dao.upsertAll(entities)
            }
            dao.deleteOlderThan(LocalDate.now().minusDays(60).toEpochDay())
        }
    }

    override fun observeForDay(day: LocalDate): Flow<List<Substitution>> =
        dao.observeForDay(day.toEpochDay()).map { it.map(SubstitutionMapper::toDomain) }

    override fun observeFrom(day: LocalDate): Flow<List<Substitution>> =
        dao.observeFromDay(day.toEpochDay()).map { it.map(SubstitutionMapper::toDomain) }.flowOn(Dispatchers.Default)

    override suspend fun getForClassAndDay(classShortName: String, day: LocalDate): List<Substitution> =
        withContext(Dispatchers.IO) {
            dao.getForClassAndDay(classShortName, day.toEpochDay()).map(SubstitutionMapper::toDomain)
        }

    override suspend fun getAllFrom(day: LocalDate): List<Substitution> =
        withContext(Dispatchers.IO) {
            dao.getFromDay(day.toEpochDay()).map(SubstitutionMapper::toDomain)
        }

    override suspend fun upsertOne(sub: Substitution) = withContext(Dispatchers.IO) {
        dao.upsertAll(listOf(SubstitutionEntity(
            id = sub.id,
            dateEpochDay = sub.date.toEpochDay(),
            lessonNumber = sub.lessonNumber,
            classShortName = sub.classShortName,
            groupNumber = sub.groupNumber,
            roomOrInfo = sub.roomOrInfo,
            substituteTeacher = sub.substituteTeacher,
            notes = sub.notes,
            originalTeacher = sub.originalTeacher,
            originalSubject = sub.originalSubject
        )))
    }

    companion object { private const val TAG = "SubstitutionsRepositoryImpl" }
}
