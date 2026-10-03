package pl.zse.bydgoszcz.elektron.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
import pl.zse.bydgoszcz.elektron.data.repository.NotificationsRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.TimetableRepositoryImpl
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import java.time.DayOfWeek
import java.time.LocalDate

/** Jedna logika powiadomień o zastępstwach dla synchronizacji i pushy FCM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubstitutionNotifierTest {

    private class RecordingSink : NotificationSink {
        val posted = mutableListOf<Pair<Substitution, String?>>()
        override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) { posted += sub to originalSubject }
        override suspend fun postAnnouncement(ann: Announcement) {}
        override suspend fun postGeneric(title: String, body: String, deepLink: String?) {}
    }

    private lateinit var db: AppDatabase
    private val settings = FakeSettings()
    private val sink = RecordingSink()
    private lateinit var notifier: SubstitutionNotifier
    private lateinit var notificationsRepo: NotificationsRepositoryImpl
    // Następny tydzień: lekcje na pewno jeszcze się nie odbyły.
    private val nextMonday = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1)

    @Before fun setUp() = runBlocking {
        db = inMemoryDb()
        val timetableRepo = TimetableRepositoryImpl(object : TimetableSource {
            override suspend fun fetchSidebar() = listOf(
                ClassListItemDto("o3", ClassListItemDto.Kind.CLASS, "1D 1D PBŚ", "1D", "https://plan.zse.bydgoszcz.pl/plany/o3.html"))
            override suspend fun fetchTimetable(classId: String) = TimetableDto(classId, "1D 1D PBŚ", null, null,
                listOf(LessonCellDto(3, "09:50", "10:35", 1,
                    listOf(LessonGroupDto("ang-1/2", "Ab", null, "105", null, null, null),
                        LessonGroupDto("niem-2/2", "Cd", null, "106", null, null, null)), null)))
        }, db.schoolClassDao(), db.teacherDao(), db.roomDao(), db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)
        timetableRepo.syncSidebar()
        timetableRepo.syncTimetable("o3", LocalDate.now())
        notificationsRepo = NotificationsRepositoryImpl(db.notificationDao(), db.syncStateDao())
        notifier = SubstitutionNotifier(settings, timetableRepo, notificationsRepo, sink)
    }

    @After fun tearDown() = db.close()

    private fun sub(id: String, cls: String = "1D", group: Int? = null, date: LocalDate = nextMonday) = Substitution(
        id = id, date = date, lessonNumber = 3, classShortName = cls, groupNumber = group,
        roomOrInfo = "316", substituteTeacher = "X.Zastępca", notes = null, originalTeacher = "Y.Nauczyciel"
    )

    @Test
    fun freshSubstitutionsOnlyForOwnClassWithSubjectOfSubstitutedGroup() = runBlocking {
        notifier.notifyFresh(listOf(sub("a"), sub("b", cls = "2A"), sub("c", group = 2)))
        assertEquals(listOf("a", "c"), sink.posted.map { it.first.id })
        assertEquals("niem-2/2", sink.posted.single { it.first.id == "c" }.second)
    }

    @Test
    fun otherGroupAndDisabledNotificationsAreSkipped() = runBlocking {
        settings.groupSelectionsFlow.value = mapOf("ang" to "1/2", "niem" to "-")
        notifier.notifyFresh(listOf(sub("g2", group = 2)))
        assertTrue(sink.posted.isEmpty())
        settings.groupSelectionsFlow.value = emptyMap()
        settings.notifySubs.value = false
        notifier.notifyFresh(listOf(sub("a")))
        notifier.notifyPushed(sub("b"))
        assertTrue(sink.posted.isEmpty())
    }

    @Test
    fun pastLessonIsNotNotified() = runBlocking {
        notifier.notifyFresh(listOf(sub("old", date = LocalDate.now().minusDays(1))))
        assertTrue(sink.posted.isEmpty())
    }

    @Test
    fun pushIsNotifiedOnceAndMarkedSeen() = runBlocking {
        notifier.notifyPushed(sub("p"))
        notifier.notifyPushed(sub("p"))                 // drugi push albo sync - bez duplikatu
        assertEquals(listOf("p"), sink.posted.map { it.first.id })
        assertTrue("p" in notificationsRepo.getSeenSubstitutionIds())
    }

    @Test
    fun pushAlreadySeenBySyncIsSkipped() = runBlocking {
        notificationsRepo.setSeenSubstitutionIds(setOf("s"))
        notifier.notifyPushed(sub("s"))
        assertTrue(sink.posted.isEmpty())
    }
}
