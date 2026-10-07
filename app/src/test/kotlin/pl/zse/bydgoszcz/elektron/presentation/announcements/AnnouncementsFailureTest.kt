package pl.zse.bydgoszcz.elektron.presentation.announcements

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.remote.dto.*
import pl.zse.bydgoszcz.elektron.data.remote.sources.*
import pl.zse.bydgoszcz.elektron.data.repository.inMemoryDb
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.testutil.*
import pl.zse.bydgoszcz.elektron.work.NotificationSink
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementsFailureTest {
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
    @Test fun databaseFailureDuringPaginationIsDisplayedAndCanBeRetried() = failureCase(0)
    @Test fun archiveResetFailureStopsRefreshingAndDisplaysMessage() = failureCase(1)
    @Test fun thrownArticleFailureIsHandledAndClosingClearsLoadingAndError() = failureCase(2)
    @Test fun closingWhileArticleLoadsClearsLoadingImmediately() = failureCase(3)

    @Test fun unsuccessfulNetworkRefreshDisplaysErrorInsteadOfSilentlyKeepingOldList() = failureCase(4)

    private fun failureCase(kind: Int) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val db = inMemoryDb()
        val owner = ViewModelStore()
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val fixture = TestCoordinator(context, db, FakeSettings(classId = null), Offline, Offline, Offline, Offline)
            var failed = true
            val gate = CompletableDeferred<Result<Unit>>()
            val repo = object : AnnouncementsRepository by fixture.announcementsRepo {
                override fun observeAll(): Flow<List<Announcement>> = flowOf(emptyList())
                override val archiveGeneration = flowOf(0L)
                override suspend fun count(): Int { if (failed) throw IOException("Baza niedostępna"); return 0 }
                override suspend fun loadOlder() = Result.success(AnnouncementsRepository.OlderResult(0, true))
                override suspend fun resetArchive(clear: suspend () -> Unit) { if (kind != 4) throw IOException("Błąd zapisu"); clear() }
                override suspend fun loadFullArticle(id: String): Result<Unit> {
                    if (kind == 3) return gate.await()
                    throw IOException("Błąd odczytu")
                }
            }
            val vm = AnnouncementsViewModel(repo, fixture.coordinator)
            owner.put("announcements", vm)
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            vm.state.first { it.ready }
            when (kind) {
                0 -> {
                    vm.loadMore()
                    val state = vm.state.first { it.loadMoreError && !it.isLoadingMore }
                    assertFalse(state.isLoadingMore)
                    failed = false
                    vm.loadMore()
                    vm.state.first { !it.loadMoreError && !it.hasMore }
                }
                1, 4 -> {
                    vm.refresh()
                    assertTrue(vm.message.first { it != null }!!.contains("Nie udało się odświeżyć"))
                    vm.state.first { !it.isRefreshing }
                }
                2 -> {
                    vm.openArticle("id")
                    assertNotNull(vm.articleError.first { it != null })
                    assertFalse(vm.articleLoading.value)
                    vm.closeArticle()
                    assertNull(vm.articleError.value)
                    assertFalse(vm.articleLoading.value)
                }
                3 -> {
                    vm.openArticle("id")
                    assertTrue(vm.articleLoading.value)
                    vm.closeArticle()
                    assertFalse(vm.articleLoading.value)
                    assertNull(vm.articleError.value)
                    gate.complete(Result.success(Unit))
                    assertFalse(vm.articleLoading.value)
                }
            }
            collector.cancel()
        } finally { owner.clear(); db.close(); Dispatchers.resetMain() }
    }
}
