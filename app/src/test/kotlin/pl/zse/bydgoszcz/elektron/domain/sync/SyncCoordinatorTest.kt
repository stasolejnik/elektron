package pl.zse.bydgoszcz.elektron.domain.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.AnnouncementEntity
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase
import pl.zse.bydgoszcz.elektron.data.remote.dto.ArchiveItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonCellDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonGroupDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.RssItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import pl.zse.bydgoszcz.elektron.testutil.TestCoordinator
import pl.zse.bydgoszcz.elektron.work.NotificationSink
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncCoordinatorTest {

    /** Plan: [blockedClass] czeka na [gate] (raz, jeśli [blockOnce]); zapisuje kolejność zdarzeń. */
    private inner class Timetable : TimetableSource {
        val calls = AtomicInteger()
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val started = CompletableDeferred<Unit>()
        @Volatile var gate: CompletableDeferred<Unit>? = null
        @Volatile var blockedClass: String? = null
        @Volatile var blockOnce = false
        @Volatile var error: Exception? = null

        override suspend fun fetchSidebar() = listOf(
            ClassListItemDto("o3", ClassListItemDto.Kind.CLASS, "1D 1D PBŚ", "1D", "https://plan.zse.bydgoszcz.pl/plany/o3.html"),
            ClassListItemDto("o4", ClassListItemDto.Kind.CLASS, "1E 1E PBŚ", "1E", "https://plan.zse.bydgoszcz.pl/plany/o4.html")
        )

        override suspend fun fetchTimetable(classId: String): TimetableDto {
            calls.incrementAndGet()
            events += "start:$classId"
            val g = gate
            if (g != null && (blockedClass == null || blockedClass == classId)) {
                if (blockOnce) gate = null
                started.complete(Unit)
                try {
                    g.await()
                } catch (e: CancellationException) {
                    // Czy baza była jeszcze nietknięta, gdy przerwano tę pracę (reset czeka na koniec)?
                    events += "cancelled:$classId:znacznik=${withContext(NonCancellable) { db.announcementDao().count() }}"
                    throw e
                }
            }
            error?.let { throw it }
            val name = if (classId == "o4") "1E 1E PBŚ" else "1D 1D PBŚ"
            return TimetableDto(classId, name, null, null, (1..5).map { day ->
                LessonCellDto(2, "08:55", "09:40", day, listOf(LessonGroupDto("mat", "Ch", null, "105", null, null, null)), null)
            })
        }
    }

    private class Subs : SubstitutionsSource {
        @Volatile var items: List<SubstitutionDto> = emptyList()
        @Volatile var error: Exception? = null
        override suspend fun fetchSubstitutions(): List<SubstitutionDto> { error?.let { throw it }; return items }
    }

    private class Anns : AnnouncementsSource {
        @Volatile var error: Exception? = null
        override suspend fun fetchNewsFeed(): List<RssItemDto> { error?.let { throw it }; return emptyList() }
        override suspend fun fetchLatestFeed(): List<RssItemDto> { error?.let { throw it }; return emptyList() }
        override suspend fun fetchArticleHtml(url: String): String? = null
        override suspend fun fetchArchivePage(page: Int) = emptyList<ArchiveItemDto>()
        override suspend fun fetchArticleDate(url: String): Instant? = null
    }

    private class RecordingSink : NotificationSink {
        val substitutions: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) { substitutions += sub.id }
        override suspend fun postAnnouncement(ann: Announcement) {}
        override suspend fun postGeneric(title: String, body: String, deepLink: String?) {}
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private val settings = FakeSettings(classId = "o3")
    private val timetable = Timetable()
    private val subs = Subs()
    private val anns = Anns()
    private val sink = RecordingSink()
    private lateinit var t: TestCoordinator
    private val coordinator get() = t.coordinator
    private val nextMonday = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1)
    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun sub(cls: String, teacher: String) =
        SubstitutionDto(nextMonday.format(fmt), teacher, 2, cls, null, "316", "X.Zastępca", null)

    private fun newCoordinator() = TestCoordinator(context, db, settings, timetable, subs, anns, sink)

    @Before fun setUp() {
        db = inMemoryDb()
        t = newCoordinator()
    }

    @After fun tearDown() = db.close()

    private suspend fun <T> eventually(read: suspend () -> T, ok: (T) -> Boolean): T = withTimeout(10_000) {
        var v = read()
        while (!ok(v)) { delay(20); v = read() }
        v
    }

    // --- Wspólna praca ---

    @Test
    fun twoParallelCallsWithSameRequestDoOneFetch() = runBlocking<Unit> {
        timetable.gate = CompletableDeferred()
        val a = async(Dispatchers.Default) { coordinator.sync(SyncRequest.full()) }
        withTimeout(10_000) { timetable.started.await() }
        val b = async(Dispatchers.Default) { coordinator.sync(SyncRequest.full()) }
        delay(200)
        timetable.gate!!.complete(Unit)
        val ra = withTimeout(10_000) { a.await() }
        val rb = withTimeout(10_000) { b.await() }
        assertEquals(1, timetable.calls.get())
        assertSame(ra, rb)
        assertTrue(SyncOutcome.TIMETABLE in ra.succeeded)
    }

    @Test
    fun differentPlanDateIsSeparateWork() = runBlocking<Unit> {
        val thisWeek = SyncRequest.of(SyncOutcome.TIMETABLE)
        val nextWeek = SyncRequest.of(SyncOutcome.TIMETABLE, anchorDate = LocalDate.now().plusWeeks(1))
        timetable.gate = CompletableDeferred()
        val a = async(Dispatchers.Default) { coordinator.sync(thisWeek) }
        withTimeout(10_000) { timetable.started.await() }
        val b = async(Dispatchers.Default) { coordinator.sync(nextWeek) }
        delay(200)
        timetable.gate!!.complete(Unit)
        withTimeout(10_000) { a.await(); b.await() }
        assertEquals(2, timetable.calls.get())
    }

    @Test
    fun cancelledCallerDoesNotCancelWorkOthersWaitFor() = runBlocking<Unit> {
        timetable.gate = CompletableDeferred()
        val a = async(Dispatchers.Default) { coordinator.sync(SyncRequest.full()) }
        withTimeout(10_000) { timetable.started.await() }
        val b = async(Dispatchers.Default) { coordinator.sync(SyncRequest.full()) }
        delay(200)
        a.cancelAndJoin()                                    // np. zamknięty ekran
        timetable.gate!!.complete(Unit)
        val rb = withTimeout(10_000) { b.await() }
        assertTrue(SyncOutcome.TIMETABLE in rb.succeeded)
        assertEquals(1, timetable.calls.get())
        assertTrue(timetable.events.none { it.startsWith("cancelled") })
    }

    @Test
    fun lastWaiterLeavingCancelsWorkAndClearsSyncingFlag() = runBlocking<Unit> {
        timetable.gate = CompletableDeferred()
        val a = async(Dispatchers.Default) { coordinator.sync(SyncRequest.full()) }
        withTimeout(10_000) { timetable.started.await() }
        assertTrue(coordinator.isSyncing.first())
        a.cancelAndJoin()                                    // np. zatrzymany worker
        eventually({ timetable.events.toList() }) { ev -> ev.any { it.startsWith("cancelled:o3") } }
        eventually({ coordinator.isSyncing.first() }) { !it }
        assertTrue(a.isCancelled)
    }

    @Test
    fun classChangeInterruptsWaitersWithoutCancellation() = runBlocking<Unit> {
        timetable.gate = CompletableDeferred()
        timetable.blockedClass = "o3"
        val a = async(Dispatchers.Default) { coordinator.sync(SyncRequest.full()) }
        withTimeout(10_000) { timetable.started.await() }
        coordinator.selectClass("o4")
        // Wołający (np. SyncWorker) dostaje "przerwano", a nie CancellationException.
        val ra = withTimeout(10_000) { a.await() }
        assertTrue(ra.interrupted)
        eventually({ t.notificationsRepo.observeLoadedResources().first() }) { SyncOutcome.TIMETABLE in it }
        assertEquals("o4", settings.classIdFlow.value)
        val ev = timetable.events.toList()
        val cancelled = ev.indexOfFirst { it.startsWith("cancelled:o3") }
        assertTrue(ev.toString(), cancelled >= 0 && cancelled < ev.indexOf("start:o4"))
    }

    @Test
    fun resetWaitsForRunningWorkBeforeClearingDatabase() = runBlocking<Unit> {
        db.announcementDao().upsertAll(listOf(AnnouncementEntity(
            id = "znacznik", title = "x", url = "https://zse/x", publishedAtEpochSeconds = Instant.now().epochSecond,
            excerpt = null, coverImageUrl = null, fullHtml = null, isRead = false, source = "RSS_NEWS"
        )))
        timetable.gate = CompletableDeferred()
        timetable.blockOnce = true
        val a = async(Dispatchers.Default) { coordinator.sync(SyncRequest.full()) }
        withTimeout(10_000) { timetable.started.await() }
        coordinator.resetCacheAndResync("o3")
        assertTrue(withTimeout(10_000) { a.await() }.interrupted)
        eventually({ timetable.events.toList() }) { ev -> ev.count { it == "start:o3" } == 2 }
        // Przerwana praca zakończyła się, gdy baza była jeszcze cała - czyszczenie poszło później.
        assertTrue(timetable.events.toString(), "cancelled:o3:znacznik=1" in timetable.events)
        eventually({ t.notificationsRepo.observeLoadedResources().first() }) { SyncOutcome.TIMETABLE in it }
        assertEquals(0, db.announcementDao().count())
    }

    // --- Wspólne kroki po synchronizacji ---

    @Test
    fun failedSourceDoesNotBlockOthers() = runBlocking<Unit> {
        subs.error = IOException("Zastępstwa: HTTP 503 dla x")
        val r = coordinator.sync(SyncRequest.full())
        assertTrue(SyncOutcome.SUBSTITUTIONS in r.failures)
        assertTrue(r.succeeded.containsAll(listOf(SyncOutcome.TIMETABLE, SyncOutcome.SIDEBAR, SyncOutcome.ANNOUNCEMENTS)))
        assertNotNull(t.notificationsRepo.getLastSyncAt())                     // plan się pobrał
        assertNotNull(t.notificationsRepo.observeLastSyncError().first())      // komunikat o zastępstwach
    }

    @Test
    fun onlyTimetableOrSubstitutionsSetLastSync() = runBlocking<Unit> {
        timetable.error = IOException("Plan lekcji: HTTP 503")
        subs.error = IOException("Zastępstwa: HTTP 503")
        val r = coordinator.sync(SyncRequest.full())
        assertTrue(r.succeeded.containsAll(listOf(SyncOutcome.SIDEBAR, SyncOutcome.ANNOUNCEMENTS)))
        assertNull(t.notificationsRepo.getLastSyncAt())
        timetable.error = null
        coordinator.sync(SyncRequest.of(SyncOutcome.TIMETABLE))
        assertNotNull(t.notificationsRepo.getLastSyncAt())
    }

    @Test
    fun announcementsOnlyRefreshKeepsPlanError() = runBlocking<Unit> {
        timetable.error = IOException("Plan lekcji: HTTP 503")
        coordinator.sync(SyncRequest.full())
        val error = t.notificationsRepo.observeLastSyncError().first()
        assertNotNull(error)
        coordinator.sync(SyncRequest.of(SyncOutcome.ANNOUNCEMENTS))
        assertEquals(error, t.notificationsRepo.observeLastSyncError().first())
    }

    // --- Powiadomienia: pierwsza synchronizacja (instalacja, zmiana klasy, reset) bez powiadomień ---

    @Test
    fun firstSyncAfterInstallClassChangeAndResetDoesNotNotify() = runBlocking<Unit> {
        subs.items = listOf(sub("1D", "A"))
        coordinator.sync(SyncRequest.full())
        assertTrue("pierwsza synchronizacja po instalacji", sink.substitutions.isEmpty())

        subs.items = listOf(sub("1D", "A"), sub("1D", "B"))
        coordinator.sync(SyncRequest.full())
        assertEquals(1, sink.substitutions.size)                         // zwykła: nowe B

        subs.items = subs.items + sub("1E", "C")                        // nowe dla klasy 1E
        coordinator.selectClass("o4")
        eventually({ t.notificationsRepo.observeLoadedResources().first() }) { SyncOutcome.TIMETABLE in it }
        eventually({ coordinator.isSyncing.first() }) { !it }
        assertEquals("pierwsza po zmianie klasy", 1, sink.substitutions.size)

        subs.items = subs.items + sub("1E", "D")
        coordinator.sync(SyncRequest.full())
        assertEquals(2, sink.substitutions.size)                         // zwykła: nowe D

        subs.items = subs.items + sub("1E", "E")
        coordinator.resetCacheAndResync("o4")
        eventually({ t.notificationsRepo.observeLoadedResources().first() }) { SyncOutcome.TIMETABLE in it }
        eventually({ coordinator.isSyncing.first() }) { !it }
        assertEquals("pierwsza po resecie", 2, sink.substitutions.size)
    }

    // --- Flaga "pierwsza synchronizacja po zmianie klasy" ---

    @Test
    fun initialSyncPendingSurvivesProcessRestartAfterReset() = runBlocking<Unit> {
        coordinator.sync(SyncRequest.full())
        assertFalse(coordinator.initialSyncPending.first())
        timetable.gate = CompletableDeferred()
        timetable.blockOnce = true
        coordinator.resetCacheAndResync("o3")                            // baza czyszczona, plan czeka
        withTimeout(10_000) { timetable.started.await() }
        // "Restart procesu": nowy koordynator (pusta pamięć) na tej samej bazie i ustawieniach.
        val afterRestart = newCoordinator().coordinator
        assertTrue(afterRestart.initialSyncPending.first())
        timetable.gate!!.complete(Unit)
        eventually({ afterRestart.initialSyncPending.first() }) { !it }
    }

    @Test
    fun failedFirstSyncEndsPendingSoGroupsCanOfferRetry() = runBlocking<Unit> {
        timetable.error = IOException("Plan lekcji: HTTP 503")
        coordinator.selectClass("o4")
        eventually({ t.notificationsRepo.observeLastSyncError().first() }) { it != null }
        eventually({ coordinator.initialSyncPending.first() }) { !it }
    }
}
