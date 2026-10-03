package pl.zse.bydgoszcz.elektron.data.repository

import pl.zse.bydgoszcz.elektron.domain.model.SchoolPageChangedException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonCellDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonGroupDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableRepositoryImplTest {

    private class FakeSource : TimetableSource {
        override suspend fun fetchSidebar() = emptyList<ClassListItemDto>()
        override suspend fun fetchTimetable(classId: String) = TimetableDto(
            classId = classId, className = "1D 1D PBŚ", generatedAt = null, validFrom = null,
            lessons = listOf(LessonCellDto(1, "08:00", "08:45", 1,
                listOf(LessonGroupDto("mat", "Ch", null, "105", null, null, null)), null))
        )
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: TimetableRepositoryImpl
    private val monday = LocalDate.now().with(DayOfWeek.MONDAY)

    @Before fun setUp() {
        db = inMemoryDb()
        repo = TimetableRepositoryImpl(FakeSource(), db.schoolClassDao(), db.teacherDao(), db.roomDao(),
            db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)
    }

    @After fun tearDown() = db.close()

    @Test
    fun templateIsStoredForFourWeeks() = runTest {
        // Plan (szablon tygodniowy) zapisywany na 4 tygodnie — w weekend Start nie może
        // pokazywać "Brak nadchodzących lekcji".
        repo.syncTimetable("o3", monday)
        assertTrue(repo.hasLessons("o3", monday.plusWeeks(3), monday.plusWeeks(3).plusDays(4)))
        assertFalse(repo.hasLessons("o3", monday.plusWeeks(4), monday.plusWeeks(4).plusDays(4)))
    }

    @Test
    fun oldWeeksArePruned() = runTest {
        // Dawniej stare tygodnie zostawały w bazie na zawsze.
        val farBack = monday.minusWeeks(10)
        db.lessonDao().upsertAll(listOf(pl.zse.bydgoszcz.elektron.data.local.LessonEntity(
            id = "o3|${farBack.toEpochDay()}|1", classId = "o3", className = "1D", dateEpochDay = farBack.toEpochDay(),
            dayOfWeekIso = 1, number = 1, timeFrom = "08:00", timeTo = "08:45", note = null
        )))
        repo.syncTimetable("o3", monday)
        assertFalse(repo.hasLessons("o3", farBack, farBack.plusDays(4)))
        assertTrue(repo.hasLessons("o3", monday, monday.plusDays(4)))
    }

    @Test
    fun pastAnchorNeverWritesCurrentPlanIntoPastWeeks() = runTest {
        // Strona szkoły publikuje tylko aktualny plan. Dawniej sync tygodnia przewiniętego
        // wstecz (albo odświeżenie na nim) zapisywał bieżący szablon jako plan sprzed tygodni.
        val pastWeek = monday.minusWeeks(3)
        repo.syncTimetable("o3", pastWeek)
        assertFalse(repo.hasLessons("o3", pastWeek, pastWeek.plusDays(4)))
        assertTrue(repo.hasLessons("o3", monday, monday.plusDays(4)))     // bieżący plan odświeżony
    }

    @Test
    fun refreshOnPastWeekKeepsItsSavedPlan() = runTest {
        // Zapisany wcześniej plan minionego tygodnia (z tamtego czasu) zostaje nietknięty.
        repoWithPlan("stary", null).syncTimetable("o3", monday)
        val lastWeek = monday.minusWeeks(1)
        db.lessonDao().upsertAll(listOf(pl.zse.bydgoszcz.elektron.data.local.LessonEntity(
            id = "o3|${lastWeek.toEpochDay()}|1", classId = "o3", className = "1D", dateEpochDay = lastWeek.toEpochDay(),
            dayOfWeekIso = 1, number = 1, timeFrom = "08:00", timeTo = "08:45", note = null
        )))
        repoWithPlan("nowy", null).syncTimetable("o3", lastWeek)
        assertTrue(repo.getLessonsOnce("o3", lastWeek, lastWeek).single().groups.isEmpty())   // bez nadpisania
        assertEquals("nowy", subjectOn(monday))
    }

    @Test
    fun classNameHasNoDuplicates() = runTest {
        repo.syncTimetable("o3", monday)
        assertEquals("1D PBŚ", repo.getLessonsOnce("o3", monday, monday).single().className)
    }

    @Test
    fun changedPageLayoutKeepsSavedPlanAndFails() = runTest {
        repo.syncTimetable("o3", monday)
        val broken = TimetableRepositoryImpl(object : TimetableSource {
            override suspend fun fetchSidebar() = emptyList<ClassListItemDto>()
            override suspend fun fetchTimetable(classId: String) =
                TimetableDto(classId, classId, null, null, emptyList(), layoutOk = false)
        }, db.schoolClassDao(), db.teacherDao(), db.roomDao(),
            db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)

        val result = broken.syncTimetable("o3", monday)
        assertTrue(result.exceptionOrNull() is SchoolPageChangedException)
        assertTrue(repo.hasLessons("o3", monday, monday.plusDays(4)))   // stary plan nietknięty
    }

    private fun repoWithPlan(subject: String, validFrom: LocalDate?) = TimetableRepositoryImpl(object : TimetableSource {
        override suspend fun fetchSidebar() = emptyList<ClassListItemDto>()
        // Lekcja w poniedziałek i w piątek.
        override suspend fun fetchTimetable(classId: String) = TimetableDto(
            classId = classId, className = "1D 1D PBŚ", generatedAt = null,
            validFrom = validFrom?.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            lessons = listOf(1, 5).map { day ->
                LessonCellDto(1, "08:00", "08:45", day, listOf(LessonGroupDto(subject, "Ch", null, "105", null, null, null)), null)
            })
    }, db.schoolClassDao(), db.teacherDao(), db.roomDao(), db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)

    private suspend fun subjectOn(day: LocalDate) =
        repo.getLessonsOnce("o3", day, day).single().groups.single().subject

    @Test
    fun futurePlanDoesNotReplaceCurrentDays() = runTest {
        // Nowy plan opublikowany z wyprzedzeniem, obowiązuje od piątku tego tygodnia:
        // poniedziałek zostaje ze starego planu, od piątku - nowy.
        repoWithPlan("stary", null).syncTimetable("o3", monday)
        repoWithPlan("nowy", monday.plusDays(4)).syncTimetable("o3", monday)
        assertEquals("stary", subjectOn(monday))
        assertEquals("nowy", subjectOn(monday.plusDays(4)))
        assertEquals("nowy", subjectOn(monday.plusWeeks(1)))
    }

    @Test
    fun futurePlanFillsEmptyDatabase() = runTest {
        // Pierwsze uruchomienie: brak starego planu - lepszy nowy plan niż pusty ekran.
        repoWithPlan("nowy", monday.plusDays(4)).syncTimetable("o3", monday)
        assertEquals("nowy", subjectOn(monday))
    }
}
