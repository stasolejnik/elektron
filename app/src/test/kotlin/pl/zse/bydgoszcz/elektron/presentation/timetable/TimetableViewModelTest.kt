package pl.zse.bydgoszcz.elektron.presentation.timetable

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.DayOfWeek
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroup
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.LessonTarget
import pl.zse.bydgoszcz.elektron.domain.model.SchoolClass
import pl.zse.bydgoszcz.elektron.domain.model.SchoolRoom
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.model.Teacher
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.testutil.TestCoordinator
import pl.zse.bydgoszcz.elektron.work.NotificationSink
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek as JavaDayOfWeek

/**
 * Dotknięcie zastępstwa: plan na ten dzień i (jednorazowo) szczegóły lekcji, gdy jest w planie.
 * Bufor tygodni planu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableViewModelTest {

    /** Plan w pamięci; hasLessons = true, więc ViewModel niczego nie pobiera. */
    private class FakeTimetable : TimetableRepository {
        val lessons = MutableStateFlow<List<Lesson>>(emptyList())
        var hasLessonsError = false
        var checks = 0
        override suspend fun syncSidebar(): Result<Unit> = Result.success(Unit)
        override suspend fun syncTimetable(classId: String, anchorDate: LocalDate): Result<Unit> = Result.success(Unit)
        override fun observeClasses(): Flow<List<SchoolClass>> = flowOf(emptyList())
        override fun observeTeachers(): Flow<List<Teacher>> = flowOf(emptyList())
        override fun observeRooms(): Flow<List<SchoolRoom>> = flowOf(emptyList())
        override fun observeLessons(classId: String, from: LocalDate, to: LocalDate): Flow<List<Lesson>> =
            lessons.map { all -> all.filter { it.date in from..to } }
        override fun observeAllLessons(from: LocalDate, to: LocalDate): Flow<List<Lesson>> = observeLessons("", from, to)
        override suspend fun getLessonsOnce(classId: String, from: LocalDate, to: LocalDate): List<Lesson> =
            lessons.value.filter { it.date in from..to }
        override suspend fun enrichWithTeacherNames(lessons: List<Lesson>): List<Lesson> = lessons
        override suspend fun hasLessons(classId: String, from: LocalDate, to: LocalDate): Boolean {
            checks++
            if (hasLessonsError) throw IOException("Baza niedostępna")
            return true
        }
    }

    private object Offline : TimetableSource, SubstitutionsSource, AnnouncementsSource, NotificationSink {
        override suspend fun fetchSidebar(): List<ClassListItemDto> = throw IOException()
        override suspend fun fetchTimetable(classId: String): TimetableDto = throw IOException()
        override suspend fun fetchSubstitutions(): List<SubstitutionDto> = throw IOException()
        override suspend fun fetchNewsFeed(): List<RssItemDto> = throw IOException()
        override suspend fun fetchLatestFeed(): List<RssItemDto> = throw IOException()
        override suspend fun fetchArticleHtml(url: String): String? = null
        override suspend fun fetchArchivePage(page: Int) = emptyList<ArchiveItemDto>()
        override suspend fun fetchArticleDate(url: String): Instant? = null
        override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) {}
        override suspend fun postAnnouncement(ann: Announcement) {}
        override suspend fun postGeneric(title: String, body: String, deepLink: String?) {}
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private val settings = FakeSettings(classId = "o3")
    private val repo = FakeTimetable()
    private val day = LocalDate.now().with(JavaDayOfWeek.MONDAY).plusWeeks(1).plusDays(2) // środa za tydzień
    private val target = LessonTarget(day, 3)

    private fun group(subject: String) = LessonGroup(subject, null, null, null, null, null, null, null)

    private fun lesson(number: Int, vararg subjects: String) = Lesson(
        id = "l$number", classId = "o3", className = "1D", date = day, dayOfWeek = DayOfWeek.SRODA,
        number = number, timeFrom = LocalTime.of(10, 0), timeTo = LocalTime.of(10, 45),
        groups = subjects.map(::group), note = null, substitution = null
    )

    private fun viewModel(): TimetableViewModel {
        val coordinator = TestCoordinator(context, db, settings, Offline, Offline, Offline, Offline).coordinator
        return TimetableViewModel(settings, repo, coordinator)
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = inMemoryDb()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun opensLoadedLessonOnceOnItsDay() = runTest(dispatcher) {
        repo.lessons.value = listOf(lesson(2, "mat"), lesson(3, "pol"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.openLesson(target)
        advanceUntilIdle()
        assertEquals(day, vm.currentAnchor)
        assertEquals(day, vm.openingJump.value?.day)
        assertEquals("l3", vm.lessonDetails.value?.id)
        // Jednorazowo: po zużyciu nic nie wraca (np. po obrocie ekranu ekran czyta stan od nowa).
        vm.consumeLessonDetails()
        advanceUntilIdle()
        assertNull(vm.lessonDetails.value)
    }

    @Test
    fun openingDayDoesNotMoveAwayFromLessonDay() = runTest(dispatcher) {
        repo.lessons.value = listOf(lesson(3, "pol"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.openLesson(target)
        // Wejście na zakładkę planu (NavHost) liczy dzień startowy - nie może przeskoczyć na dziś.
        vm.applyOpeningDay()
        advanceUntilIdle()
        assertEquals(day, vm.currentAnchor)
        assertEquals(day, vm.openingJump.value?.day)
    }

    @Test
    fun dashboardCardOpensTheDayOfTheShownLessonWithoutDetails() = runTest(dispatcher) {
        repo.lessons.value = listOf(lesson(3, "pol"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.openDay(day)
        vm.applyOpeningDay()
        advanceUntilIdle()
        assertEquals(day, vm.currentAnchor)
        assertEquals(day, vm.openingJump.value?.day)
        assertNull(vm.lessonDetails.value)
    }

    @Test
    fun waitsForPlanThatIsStillLoading() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.openLesson(target)
        advanceTimeBy(2_000)
        runCurrent()
        assertNull(vm.lessonDetails.value) // pusty plan - okna nie otwieramy
        repo.lessons.value = listOf(lesson(3, "pol"))
        runCurrent()
        assertEquals("l3", vm.lessonDetails.value?.id)
    }

    @Test
    fun givesUpAfterWaitLimit() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.openLesson(target)
        advanceTimeBy(TimetableViewModel.LESSON_WAIT_MS + 1)
        runCurrent()
        repo.lessons.value = listOf(lesson(3, "pol"))
        advanceUntilIdle()
        assertNull(vm.lessonDetails.value) // za późno - bez niespodziewanego okna
        assertEquals(day, vm.currentAnchor) // plan i tak na dniu zastępstwa
    }

    @Test
    fun missingLessonOpensOnlyThePlan() = runTest(dispatcher) {
        repo.lessons.value = listOf(lesson(1, "mat"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.openLesson(target)
        advanceUntilIdle()
        assertNull(vm.lessonDetails.value)
        assertEquals(day, vm.currentAnchor)
    }

    @Test
    fun otherGroupsLessonOpensOnlyThePlan() = runTest(dispatcher) {
        settings.groupSelectionsFlow.value = mapOf("rel" to LessonGroups.NONE)
        repo.lessons.value = listOf(lesson(1, "mat"), lesson(3, "rel-1/1"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.openLesson(target)
        advanceUntilIdle()
        assertNull(vm.lessonDetails.value)
        assertEquals(day, vm.currentAnchor)
    }

    @Test
    fun weekCacheKeepsOnlyRecentWeeks() = runTest(dispatcher) {
        val vm = viewModel()
        val monday = day.with(JavaDayOfWeek.MONDAY)
        val first = vm.week(monday)
        assertSame(first, vm.week(monday)) // ten sam tydzień - z bufora
        for (i in 1..TimetableViewModel.WEEK_CACHE_SIZE) vm.week(monday.plusWeeks(i.toLong()))
        assertNotSame(first, vm.week(monday)) // najdawniej oglądany wypadł z bufora
        assertSame(vm.week(monday.plusWeeks(12)), vm.week(monday.plusWeeks(12)))
    }
    @Test fun failedDatabaseCheckDoesNotKillObservationOfLaterWeeks() = runTest(dispatcher) {
        repo.hasLessonsError = true
        val vm = viewModel()
        advanceUntilIdle()
        assertNotNull(vm.refreshMessage.value)
        val failedChecks = repo.checks
        repo.hasLessonsError = false
        vm.consumeRefreshMessage()
        vm.onDaySettled(day.plusWeeks(1))
        advanceUntilIdle()
        assertTrue(repo.checks > failedChecks)
        assertNull(vm.refreshMessage.value)
        assertNull(vm.syncingWeek.value)
    }

    @Test fun failedSavingOfWeekModeReportsErrorWithoutCrashingTheScreen() = runTest(dispatcher) {
        val broken = object : pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository by settings {
            override suspend fun setTimetableLook(value: pl.zse.bydgoszcz.elektron.domain.model.TimetableLook) { throw IOException("Brak miejsca") }
        }
        val coordinator = TestCoordinator(context, db, settings, Offline, Offline, Offline, Offline).coordinator
        val vm = TimetableViewModel(broken, repo, coordinator)
        advanceUntilIdle()
        vm.setMode(TimetableViewModel.ViewMode.WEEK)
        advanceUntilIdle()
        assertEquals(TimetableViewModel.ViewMode.WEEK, vm.viewMode.value)
        assertTrue(vm.refreshMessage.value!!.contains("Nie udało się zapisać"))
    }

}
