package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationsRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: NotificationsRepositoryImpl

    @Before fun setUp() {
        db = inMemoryDb()
        repo = NotificationsRepositoryImpl(db.notificationDao(), db.syncStateDao())
    }

    @After fun tearDown() = db.close()

    @Test
    fun seenIdsWithCommasRoundTrip() = runTest {
        // Bug: ID ogłoszeń to URL-e z przecinkami; zapis z separatorem "," je rozcinał
        // i każdy sync powiadamiał o tych samych ogłoszeniach od nowa.
        val ids = setOf("https://zse.bydgoszcz.pl/a-w1,10,117294.html", "https://zse.bydgoszcz.pl/b-w1,400,1.html")
        repo.setSeenAnnouncementIds(ids)
        assertEquals(ids, repo.getSeenAnnouncementIds())
    }

    @Test
    fun parallelMarkLoadedKeepsAllResources() = runTest {
        // Bug audytu #5: równoległe markLoaded gubiły flagi (wyścig read-modify-write).
        repo.clearLoaded()
        listOf("timetable", "subs", "anns").map { r -> async { repo.markLoaded(r) } }.awaitAll()
        assertEquals(setOf("timetable", "subs", "anns"), repo.observeLoadedResources().first())
    }
}
