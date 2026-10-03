package pl.zse.bydgoszcz.elektron.data.repository

import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
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
        runCatchingCancellable {
            val page = source.fetchPage()
            val entities = page.items.mapNotNull(SubstitutionMapper::toEntity)
            // Dni pokazane na stronie (z nagłówków "Zastępstwa w dniu" - także dni bez wpisów).
            val pageDays = (page.dates.mapNotNull { runCatching { LocalDate.parse(it, PL_DATE) }.getOrNull() }
                .map { it.toEpochDay() } + entities.map { it.dateEpochDay }).toSet()
            if (pageDays.isEmpty()) {
                // Brak strony / nierozpoznana strona (awaria) - nic nie ruszamy.
                Log.w(TAG, "Pusto — nie nadpisuję")
                return@runCatchingCancellable
            }
            // Strona szkoły pokazuje PEŁNY komplet zastępstw dla swoich dni - te dni zastępujemy
            // (skasowane/odwołane zastępstwa znikają, także gdy szkoła odwoła wszystkie).
            // Dawniej kasowane było wszystko od dziś: gdy szkoła opublikowała zastępstwa na
            // jutro w trakcie dzisiejszych lekcji, dzisiejsze znikały z planu.
            // Dzień, z którego parser nie odczytał wszystkich wpisów (np. nietypowy zapis klasy):
            // NIE kasujemy go - dotychczasowe wpisy zostają, odczytane są tylko dopisywane.
            // Dawniej taki dzień był czyszczony i poprawne wcześniej zastępstwa znikały.
            val incompleteDays = page.incompleteDates
                .mapNotNull { runCatching { LocalDate.parse(it, PL_DATE) }.getOrNull()?.toEpochDay() }.toSet()
            if (incompleteDays.isNotEmpty()) {
                Log.w(TAG, "Niekompletnie odczytane dni ${page.incompleteDates} - zostawiam dotychczasowe wpisy")
            }
            db.withTransaction {
                (pageDays - incompleteDays).forEach { dao.deleteForDay(it) }
                dao.upsertAll(entities)
            }
            dao.deleteOlderThan(LocalDate.now().minusDays(60).toEpochDay())
        }
    }

    override suspend fun deleteSimulated(): Int = withContext(Dispatchers.IO) { dao.deleteDevEntries() }

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

    companion object {
        private const val TAG = "SubstitutionsRepositoryImpl"
        private val PL_DATE = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
