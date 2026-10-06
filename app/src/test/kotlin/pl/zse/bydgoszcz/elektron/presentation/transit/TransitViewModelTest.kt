package pl.zse.bydgoszcz.elektron.presentation.transit

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.repository.*
import pl.zse.bydgoszcz.elektron.domain.model.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class TransitViewModelTest {
    @Test fun settingsReadFailureDoesNotCrashTheObserverOrStartNetworkWork() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = kotlinx.coroutines.flow.flow<androidx.datastore.preferences.core.Preferences> { throw java.io.IOException("Dysk") }
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences = throw java.io.IOException("Dysk")
        }
        val client = OkHttpClient.Builder().addInterceptor { throw AssertionError("No request without readable settings") }.build()
        val catalog = TransitStopCatalog(context, client)
        val vm = TransitViewModel(TransitPreferencesRepository(store), catalog, TransitJourneyRepository(client, catalog))
        val owner = ViewModelStore().apply { put("transit", vm) }
        try {
            val state = vm.settings.first { it.ready }
            assertTrue(state.failed)
            assertTrue(state.preferences.visible)
            vm.loadJourneys()
            assertFalse(vm.journeyLoading.value)
        } finally {
            owner.clear(); client.dispatcher.executorService.shutdown(); Dispatchers.resetMain()
        }
    }
    @Test fun changingGoalClearsOldResultsAndErrorsAndMapSearchIsNotTruncated() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-vm-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = ioScope, produceFile = { File(directory, "settings.preferences_pb") }))
        val client = OkHttpClient.Builder().addInterceptor { throw AssertionError("Fresh cached results must not call the network") }.build()
        val catalog = TransitStopCatalog(context, client)
        val vm = TransitViewModel(prefs, catalog, TransitJourneyRepository(client, catalog))
        val owner = ViewModelStore().apply { put("transit", vm) }
        try {
            val goal = TransitDestination("Cel", listOf("26"), 53.1236, 18.007)
            prefs.select(goal); prefs.setEnabled(true)
            vm.settings.first { it.ready && it.preferences.visible }
            val now = System.currentTimeMillis()
            vm.journeys.value = TransitJourneyRepository.Result(goal.key, listOf(TransitJourney(listOf(
                TransitRide("6", "Bielawy", "24", "Start", "26", "Cel", now + 600_000L, now + 900_000L)), 3, 150.0)), now)
            vm.journeyError.value = "Poprzedni błąd"
            vm.loadJourneys()
            assertNull(vm.journeyError.value)
            assertFalse(vm.journeyLoading.value)
            val origin = goal.copy(name = "Start", stopIds = listOf("24"))
            prefs.selectOrigin(origin)
            vm.settings.first { it.preferences.preferredOrigin?.key == origin.key }
            assertNull(vm.journeys.value)
            assertNull(vm.journeyError.value)
            val next = goal.copy(name = "Nowy cel", stopIds = listOf("27"))
            prefs.select(next)
            vm.settings.first { it.preferences.destination?.key == next.key }
            assertNull(vm.journeys.value)
            assertNull(vm.journeyError.value)
            vm.stops.value = List(150) { goal.copy(name = "Przystanek Łąkowa $it", stopIds = listOf("$it")) }
            vm.query.value = "lakowa"
            assertEquals(150, vm.matchingStops.first { it.size == 150 }.size)
            assertEquals(80, vm.results.first { it.size == 80 }.size)
            val school = pl.zse.bydgoszcz.elektron.domain.model.SchoolTransit
            val near = goal.copy(name = "Near", stopIds = listOf("1"), latitude = school.LATITUDE + 0.0001, longitude = school.LONGITUDE)
            val middle = near.copy(name = "Middle", stopIds = listOf("2"), latitude = school.LATITUDE + 0.001)
            val far = near.copy(name = "Far", stopIds = listOf("3"), latitude = school.LATITUDE + 0.002)
            vm.query.value = ""
            vm.stops.value = listOf(far, near, middle)
            assertEquals(listOf("1", "2", "3"), vm.originStops.first { it.size == 3 }.map { it.key })
            vm.query.value = "far"
            assertEquals("3", vm.originStops.first { it.size == 1 }.single().key)
            prefs.setAllowTransfers(true)
            vm.settings.first { it.preferences.allowTransfers }
            assertNull(vm.journeys.value)
            prefs.setEnabled(false)
            vm.settings.first { !it.preferences.visible }
            assertFalse(vm.journeyLoading.value)
        } finally {
            owner.clear(); ioScope.cancel(); ioScope.coroutineContext[Job]!!.join()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            Dispatchers.resetMain(); directory.deleteRecursively()
        }
    }
    @Test fun loadingMoreKeepsEarlierResultsAndDropsDuplicateVariants() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-more-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = ioScope, produceFile = { File(directory, "settings.preferences_pb") }))
        val now = System.currentTimeMillis()
        val cursor = now + 900_000L
        val goal = TransitDestination("Cel", listOf("26"), 53.1236, 18.007)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.endsWith("/stops") -> """[{"id":24,"nazwa":"Start","lat":53.12194,"lon":18.02764}]"""
                request.url.host == "routing.openstreetmap.de" -> """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[192]],"distances":[[240.2]]}"""
                else -> {
                    val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                    assertEquals(cursor, org.json.JSONObject(buffer.readUtf8()).getLong("whenMs"))
                    fun leg(depart: Long) = """{"odcinki":[{"rodzaj":"przejazd","linia":"6","cel":"Bielawy","zPrzystanku":{"id":24,"nazwa":"Start"},"doPrzystanku":{"id":26,"nazwa":"Cel"},"odjazdMs":$depart,"przyjazdMs":${depart + 300_000L}}]}"""
                    """{"ok":true,"polaczenia":[${leg(now + 600_000L)},${leg(cursor + 60_000L)},${leg(cursor + 60_000L)}]}"""
                }
            }
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        val catalog = TransitStopCatalog(context, client)
        val vm = TransitViewModel(prefs, catalog, TransitJourneyRepository(client, catalog))
        val owner = ViewModelStore().apply { put("transit", vm) }
        try {
            prefs.select(goal); prefs.setEnabled(true)
            vm.settings.first { it.preferences.visible }
            val first = TransitJourney(listOf(TransitRide("6", "Bielawy", "24", "Start", "26", "Cel", now + 600_000L, now + 900_000L)), 4, 240.2)
            vm.journeys.value = TransitJourneyRepository.Result(goal.key, listOf(first), now, nextWhenMs = cursor)
            vm.loadJourneys(more = true)
            val merged = vm.journeys.first { it?.journeys?.size == 2 }!!
            assertTrue(merged.journeys.any { it.key == first.key })
            assertEquals(2, merged.journeys.map { it.key }.distinct().size)
            vm.moreLoading.first { !it }
            assertNull(vm.journeyError.value)
        } finally {
            owner.clear(); ioScope.cancel(); ioScope.coroutineContext[Job]!!.join()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            Dispatchers.resetMain(); directory.deleteRecursively()
        }
    }
    @Test fun failedPaginationDoesNotLeaveWalkingTimePending() = paginationDuringWalking(failPage = true)
    @Test fun successfulPaginationPreservesWalkingEnrichment() = paginationDuringWalking(failPage = false)

    @Test fun paginationResponseBeforeWalkingDoesNotRestartTheRequest() = paginationDuringWalking(false, true)
    private fun paginationDuringWalking(failPage: Boolean, pageFirst: Boolean = false) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-overlap-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = ioScope, produceFile = { File(directory, "settings.preferences_pb") }))
        val now = System.currentTimeMillis()
        val goal = TransitDestination("Cel", listOf("26"), 53.1236, 18.007)
        val stop = TransitDestination("Start", listOf("24"), 53.12194, 18.02764)
        val walkingStarted = java.util.concurrent.CountDownLatch(1)
        val releaseWalking = java.util.concurrent.CountDownLatch(1)
        val pageStarted = java.util.concurrent.CountDownLatch(1)
        val releasePage = java.util.concurrent.CountDownLatch(1)
        val walkingCalls = java.util.concurrent.atomic.AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.endsWith("/stops") -> """[{"id":24,"nazwa":"Start","lat":53.12194,"lon":18.02764}]"""
                request.url.host == "routing.openstreetmap.de" -> {
                    walkingCalls.incrementAndGet(); walkingStarted.countDown()
                    check(releaseWalking.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[192]],"distances":[[240.2]]}"""
                }
                else -> {
                    pageStarted.countDown()
                    check(releasePage.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    if (failPage) throw java.io.IOException("Offline")
                    """{"ok":true,"polaczenia":[]}"""
                }
            }
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val catalog = TransitStopCatalog(context, client)
        val vm = TransitViewModel(prefs, catalog, TransitJourneyRepository(client, catalog))
        val owner = ViewModelStore().apply { put("transit", vm) }
        try {
            prefs.select(goal); prefs.setEnabled(true)
            vm.settings.first { it.preferences.visible }
            val trip = TransitJourney(listOf(TransitRide("6", "Bielawy", "24", "Start", "26", "Cel", now + 600_000L, now + 900_000L)), 1, 100.0, walkAvailable = false, walkPending = true)
            vm.journeys.value = TransitJourneyRepository.Result(goal.key, listOf(trip), now, nextWhenMs = now + 900_000L, boardingStops = listOf(stop))
            val oldConsumer = Any()
            val newConsumer = Any()
            vm.startJourneySession(oldConsumer)
            vm.startJourneySession(newConsumer)
            vm.stopJourneySession(oldConsumer)
            withContext(Dispatchers.IO) { assertTrue(walkingStarted.await(10, java.util.concurrent.TimeUnit.SECONDS)) }
            vm.loadJourneys(more = true)
            withContext(Dispatchers.IO) { assertTrue(pageStarted.await(10, java.util.concurrent.TimeUnit.SECONDS)) }
            if (pageFirst) {
                releasePage.countDown()
                vm.moreLoading.first { !it }
            }
            releaseWalking.countDown()
            vm.journeys.first { it?.journeys?.singleOrNull()?.walkMinutes == 4 }
            releasePage.countDown()
            vm.moreLoading.first { !it }
            val shown = vm.journeys.value!!.journeys.single()
            assertFalse(shown.walkPending)
            assertTrue(shown.walkAvailable)
            assertEquals(4, shown.walkMinutes)
            assertEquals(1, walkingCalls.get())
            if (failPage) assertNotNull(vm.journeyError.value) else assertNull(vm.journeyError.value)
        } finally {
            releaseWalking.countDown(); releasePage.countDown()
            owner.clear(); ioScope.cancel(); ioScope.coroutineContext[Job]!!.join()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            Dispatchers.resetMain(); directory.deleteRecursively()
        }
    }

    @Test fun settingsObserverDoesNotCancelWorkAlreadyStartedForTheNewSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-settings-race-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val data = kotlinx.coroutines.flow.MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
        val store = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = data
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
                val updated = transform(data.value)
                data.value = updated
                return updated
            }
        }
        val prefs = TransitPreferencesRepository(store)
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().url.encodedPath.endsWith("/stops")) """[{"id":24,"nazwa":"Start","lat":53.12194,"lon":18.02764}]""" else {
                requests.incrementAndGet(); started.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                """{"ok":true,"polaczenia":[]}"""
            }
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val catalog = TransitStopCatalog(context, client)
        val vm = TransitViewModel(prefs, catalog, TransitJourneyRepository(client, catalog))
        val owner = ViewModelStore().apply { put("transit", vm) }
        // Simulate the screen observing settings before the queued ViewModel observer.
        val screen = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.settings.collect { if (it.preferences.visible) vm.loadJourneys() }
        }
        try {
            runCurrent()
            val goal = TransitDestination("Cel", listOf("26"), 53.1236, 18.007)
            prefs.select(goal); prefs.setEnabled(true)
            runCurrent()
            withContext(Dispatchers.IO) { assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            assertTrue(vm.journeyLoading.value)
            release.countDown()
            assertEquals(goal.key, vm.journeys.first { it != null }!!.destinationKey)
            assertEquals(1, requests.get())
        } finally {
            release.countDown(); screen.cancel(); owner.clear()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            Dispatchers.resetMain(); directory.deleteRecursively()
        }
    }

}
