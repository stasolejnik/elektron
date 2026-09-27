package pl.zse.bydgoszcz.elektron.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.local.LessonDao
import pl.zse.bydgoszcz.elektron.data.local.LessonGroupDao
import pl.zse.bydgoszcz.elektron.data.local.RoomDao
import pl.zse.bydgoszcz.elektron.data.local.SchoolClassDao
import pl.zse.bydgoszcz.elektron.data.local.SubstitutionDao
import pl.zse.bydgoszcz.elektron.data.local.TeacherDao
import pl.zse.bydgoszcz.elektron.data.mapper.SidebarMapper
import pl.zse.bydgoszcz.elektron.data.mapper.SubstitutionMapper
import pl.zse.bydgoszcz.elektron.data.mapper.TimetableMapper
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroup
import pl.zse.bydgoszcz.elektron.domain.model.SchoolClass
import pl.zse.bydgoszcz.elektron.domain.model.SchoolRoom
import pl.zse.bydgoszcz.elektron.domain.model.Teacher
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import java.time.DayOfWeek as JavaDayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimetableRepositoryImpl @Inject constructor(
    private val source: TimetableSource,
    private val classDao: SchoolClassDao,
    private val teacherDao: TeacherDao,
    private val roomDao: RoomDao,
    private val lessonDao: LessonDao,
    private val lessonGroupDao: LessonGroupDao,
    private val substitutionDao: SubstitutionDao,
    private val db: AppDatabase
) : TimetableRepository {

    override suspend fun syncSidebar(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val items = source.fetchSidebar()
            if (items.isEmpty()) return@runCatching
            classDao.upsertAll(items.filter { it.kind == ClassListItemDto.Kind.CLASS }.map(SidebarMapper::toClassEntity))
            teacherDao.upsertAll(items.filter { it.kind == ClassListItemDto.Kind.TEACHER }.map(SidebarMapper::toTeacherEntity))
            roomDao.upsertAll(items.filter { it.kind == ClassListItemDto.Kind.ROOM }.map(SidebarMapper::toRoomEntity))
        }
    }

    override suspend fun syncTimetable(classId: String, anchorDate: LocalDate): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val monday = anchorDate.with(JavaDayOfWeek.MONDAY)
                val dto = source.fetchTimetable(classId)
                if (dto.lessons.isEmpty()) return@runCatching
                // Plan Optivum to szablon tygodniowy. Dawniej zapisywaliśmy go tylko dla
                // tygodnia anchorDate — w weekend cały zapisany tydzień był w przeszłości
                // i Start pokazywał "Brak nadchodzących lekcji". Teraz jeden fetch,
                // zapis na WEEKS_AHEAD tygodni (pokrywa 21-dniowy zakres Startu).
                val lessons = mutableListOf<pl.zse.bydgoszcz.elektron.data.local.LessonEntity>()
                val groups = mutableListOf<pl.zse.bydgoszcz.elektron.data.local.LessonGroupEntity>()
                for (w in 0 until WEEKS_AHEAD) {
                    val (l, g) = TimetableMapper.toEntities(dto, monday.plusWeeks(w.toLong()))
                    lessons += l
                    groups += g
                }
                val fromDay = monday.toEpochDay()
                val toDay = monday.plusWeeks(WEEKS_AHEAD.toLong()).minusDays(1).toEpochDay()
                // Transakcja: lekcje + grupy zapisywane atomowo, UI nigdy nie zobaczy
                // lekcji bez grup. Grupy kasują się kaskadowo (FK CASCADE).
                db.withTransaction {
                    lessonDao.deleteForRange(classId, fromDay, toDay)
                    lessonDao.upsertAll(lessons)
                    lessonGroupDao.upsertAll(groups)
                }
            }
        }

    override fun observeClasses(): Flow<List<SchoolClass>> =
        classDao.observeAll().map { list -> list.map(SidebarMapper::classToDomain).sortedWith(CLASS_COMPARATOR) }

    override fun observeTeachers(): Flow<List<Teacher>> =
        teacherDao.observeAll().map { list -> list.map(SidebarMapper::teacherToDomain) }

    override fun observeRooms(): Flow<List<SchoolRoom>> =
        roomDao.observeAll().map { list -> list.map(SidebarMapper::roomToDomain) }

    // Bug: dawniej Flow obserwował TYLKO tabelę lessons, a zastępstwa były doczytywane
    // jednorazowo w środku. Nowe zastępstwo (z syncu, z pusha FCM, z panelu dewelopera)
    // nie pojawiało się na planie ani w "Następnej lekcji", dopóki nie zmienił się sam plan.
    // Teraz combine() obserwuje obie tabele. Zakres zastępstw = widoczny zakres dat.
    override fun observeLessons(classId: String, from: LocalDate, to: LocalDate): Flow<List<Lesson>> {
        val fromDay = from.toEpochDay()
        val toDay = to.toEpochDay()
        return combine(
            lessonDao.observeForRange(classId, fromDay, toDay),
            substitutionDao.observeForRange(fromDay, toDay)
        ) { lessonEntities, subEntities ->
            assembleLessons(lessonEntities, subEntities)
        }
    }

    override fun observeAllLessons(from: LocalDate, to: LocalDate): Flow<List<Lesson>> {
        val fromDay = from.toEpochDay()
        val toDay = to.toEpochDay()
        return combine(
            lessonDao.observeAllForRange(fromDay, toDay),
            substitutionDao.observeForRange(fromDay, toDay)
        ) { lessonEntities, subEntities ->
            assembleLessons(lessonEntities, subEntities)
        }
    }

    private suspend fun assembleLessons(
        lessonEntities: List<pl.zse.bydgoszcz.elektron.data.local.LessonEntity>,
        subEntities: List<pl.zse.bydgoszcz.elektron.data.local.SubstitutionEntity>
    ): List<Lesson> {
        if (lessonEntities.isEmpty()) return emptyList()
        val ids = lessonEntities.map { it.id }
        val groupEntities = lessonGroupDao.getForLessons(ids)
        val groupsByLesson = groupEntities.groupBy { it.lessonId }
        val subs = subEntities.map(SubstitutionMapper::toDomain)
        val raw = lessonEntities.map { le ->
            TimetableMapper.toDomain(le, groupsByLesson[le.id].orEmpty(), subs)
        }
        return enrichWithTeacherNames(raw)
    }

    override suspend fun getLessonsOnce(classId: String, from: LocalDate, to: LocalDate): List<Lesson> =
        withContext(Dispatchers.IO) {
            val fromDay = from.toEpochDay()
            val toDay = to.toEpochDay()
            assembleLessons(
                lessonDao.getForRange(classId, fromDay, toDay),
                substitutionDao.getForRange(fromDay, toDay)
            )
        }

    override suspend fun hasLessons(classId: String, from: LocalDate, to: LocalDate): Boolean =
        withContext(Dispatchers.IO) {
            lessonDao.countForRange(classId, from.toEpochDay(), to.toEpochDay()) > 0
        }

    override suspend fun enrichWithTeacherNames(lessons: List<Lesson>): List<Lesson> =
        withContext(Dispatchers.IO) {
            val codes = lessons.flatMap { it.groups }.mapNotNull { it.teacherCode }.distinct()
            if (codes.isEmpty()) return@withContext lessons
            val byCode = teacherDao.getByCodes(codes).associateBy { it.code }
            lessons.map { l ->
                l.copy(groups = l.groups.map { g ->
                    val full = g.teacherCode?.let { byCode[it]?.fullName }
                    if (full == null) g else LessonGroup(
                        g.subject, g.teacherCode, g.teacherUrl, full,
                        g.room, g.roomUrl, g.groupLabel, g.classRef
                    )
                })
            }
        }

    companion object {
        private const val TAG = "TimetableRepositoryImpl"
        /** Ile tygodni do przodu zapisujemy szablon planu (4 tyg. > 21-dniowy zakres Startu). */
        private const val WEEKS_AHEAD = 4
        private val CLASS_COMPARATOR = Comparator<SchoolClass> { a, b ->
            val (na, la, ra) = classKey(a.fullName)
            val (nb, lb, rb) = classKey(b.fullName)
            when {
                na != nb -> na.compareTo(nb)
                la != lb -> la.compareTo(lb)
                else -> ra.compareTo(rb, ignoreCase = true).takeIf { it != 0 }
                    ?: a.fullName.compareTo(b.fullName, ignoreCase = true)
            }
        }
        private fun classKey(name: String): Triple<Int, Char, String> {
            val m = Regex("^(\\d+)\\s*([A-Za-z])?(?:\\s+(.+))?$").find(name) ?: return Triple(999, 'Z', name)
            val n = m.groupValues[1].toIntOrNull() ?: 999
            val l = m.groupValues[2].uppercase().firstOrNull() ?: 'Z'
            val r = m.groupValues[3]
            return Triple(n, l, r)
        }
    }
}
