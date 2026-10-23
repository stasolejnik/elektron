package pl.zse.bydgoszcz.elektron.presentation.transit

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.data.local.LessonEntity
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.repository.*
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.domain.util.AppClock
import pl.zse.bydgoszcz.elektron.testutil.FakeSettings
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Przejazdy: rano dojazd na pierwszą lekcję, ręczna zmiana kierunku, opóźnienia z tablic przystanków. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class TransitDirectionTest {
    @Test fun morningSearchesToSchoolAndManualSwitchGoesBack() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-direction-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = ioScope, produceFile = { File(directory, "s.preferences_pb") }))
        val day = LocalDate.of(2026, 10, 26)
        val db = inMemoryDb()
        db.lessonDao().upsertAll(listOf(
            LessonEntity("o3|${day.toEpochDay()}|1", "o3", "1D", day.toEpochDay(), 1, 1, "08:00", "08:45", null),
            LessonEntity("o3|${day.toEpochDay()}|7", "o3", "1D", day.toEpochDay(), 1, 7, "13:45", "14:30", null)))
        val timetable = TimetableRepositoryImpl(object : TimetableSource {
            override suspend fun fetchSidebar() = error("offline")
            override suspend fun fetchTimetable(classId: String) = error("offline")
        }, db.schoolClassDao(), db.teacherDao(), db.roomDao(), db.lessonDao(), db.lessonGroupDao(), db.substitutionDao(), db)
        val settings = FakeSettings().apply { classIdFlow.value = "o3" }
        val home = TransitDestination("Dom", listOf("41"), 53.108, 18.045)
        val bodies = java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
        val boards = java.util.concurrent.atomic.AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.endsWith("/stops") && request.url.encodedPath.contains("/departures") -> {
                    boards.incrementAndGet()
                    assertEquals("8", request.url.queryParameter("line"))
                    val dep = bodies.first().getLong("whenMs") + 5 * 60_000L
                    """[{"linia":"8","atMs":${dep + 3 * 60_000L},"delayMin":3,"source":"live"}]"""
                }
                request.url.encodedPath.endsWith("/stops") ->
                    """[{"id":41,"nazwa":"Dom","lat":53.108,"lon":18.045},{"id":11,"nazwa":"Szkoła","lat":53.1225,"lon":18.0264},{"id":24,"nazwa":"Przy szkole","lat":53.12194,"lon":18.02764}]"""
                request.url.host == "routing.openstreetmap.de" -> """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[180]],"distances":[[250]]}"""
                else -> {
                    val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                    val body = JSONObject(buffer.readUtf8()); bodies += body
                    val w = body.getLong("whenMs")
                    if (body.getJSONObject("from").has("lat"))
                        """{"ok":true,"polaczenia":[{"przyjazdMs":${w + 20 * 60_000L},"odcinki":[{"rodzaj":"przejazd","linia":"8","cel":"Rycerska","zPrzystanku":{"id":41,"nazwa":"Dom"},"doPrzystanku":{"id":11,"nazwa":"Szkoła"},"odjazdMs":${w + 5 * 60_000L},"przyjazdMs":${w + 17 * 60_000L}},{"rodzaj":"pieszo","zPrzystanku":{"id":11,"nazwa":"Szkoła"},"doPrzystanku":{"id":null,"nazwa":"Miejsce docelowe"},"odjazdMs":${w + 17 * 60_000L},"przyjazdMs":${w + 20 * 60_000L}}]}]}"""
                    else """{"ok":true,"polaczenia":[{"odcinki":[{"rodzaj":"przejazd","linia":"6","cel":"Dom","zPrzystanku":{"id":24,"nazwa":"Przy szkole"},"doPrzystanku":{"id":41,"nazwa":"Dom"},"odjazdMs":${w + 60_000L},"przyjazdMs":${w + 600_000L}}]}]}"""
                }
            }
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val catalog = TransitStopCatalog(context, client)
        // 7:00 - przed połową dnia lekcji (8:00-14:30), lekcja o 8:00.
        AppClock.simulate(day.atTime(7, 0))
        val vm = TransitViewModel(prefs, catalog, TransitJourneyRepository(client, catalog), TransitSchoolDay(settings, timetable), TransitLiveRepository(client))
        val owner = ViewModelStore().apply { put("transit", vm) }
        try {
            prefs.select(home); prefs.setEnabled(true)
            vm.settings.first { it.ready && it.preferences.destination?.key == home.key }
            val commute = vm.commute.first { it.target != null }
            assertEquals(TransitDirection.TO_SCHOOL, commute.direction)
            assertEquals(1, commute.target!!.lesson.number)
            val consumer = Any()
            vm.startJourneySession(consumer, 3)
            val result = vm.journeys.first { it != null && it.direction == TransitDirection.TO_SCHOOL }!!
            val sent = bodies.single()
            assertEquals(home.latitude, sent.getJSONObject("from").getDouble("lat"), 1e-9)
            assertEquals(SchoolTransit.LATITUDE, sent.getJSONObject("to").getDouble("lat"), 1e-9)
            val trip = result.journeys.single()
            assertEquals(sent.getLong("whenMs") + 20 * 60_000L, trip.schoolArrivalMs)
            // Symulowany czas nie pyta o pojazdy na ulicy; po powrocie do czasu telefonu - pyta.
            assertEquals(0, boards.get())
            assertTrue(vm.live.value.isEmpty())

            // Ręcznie "Ze szkoły": zapytanie ze stanowiska przy szkole do Twojego przystanku.
            vm.setDirection(TransitDirection.FROM_SCHOOL)
            assertEquals(TransitDirection.FROM_SCHOOL, vm.commute.value.direction)
            val before = bodies.size
            vm.refreshJourneys(3)
            vm.journeys.first { it != null && it.direction == TransitDirection.FROM_SCHOOL && it.complete }
            // Ze szkoły - każde stanowisko do 1 km od szkoły osobno (zapytania równoległe, dowolna kolejność).
            assertEquals(setOf(11, 24), bodies.drop(before).map { it.getJSONObject("from").getInt("stopId") }.toSet())
            vm.setDirection(TransitDirection.TO_SCHOOL)
            assertNull("powrót do automatycznego", vm.commute.value.let { if (it.direction == it.auto) null else it })
            vm.stopJourneySession(consumer)
        } finally {
            AppClock.reset()
            owner.clear(); ioScope.cancel(); ioScope.coroutineContext[Job]!!.join()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            db.close(); Dispatchers.resetMain(); directory.deleteRecursively()
        }
    }

    @Test fun liveDelaysComeFromTheStopBoardForSoonDepartures() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-live-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = TransitPreferencesRepository(PreferenceDataStoreFactory.create(scope = ioScope, produceFile = { File(directory, "s.preferences_pb") }))
        val goal = TransitDestination("Dom", listOf("41"), 53.108, 18.045)
        val departure = (System.currentTimeMillis() / 60_000L + 6) * 60_000L
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.contains("/departures") -> {
                    assertEquals("/v1/cities/bydgoszcz/stops/24/departures", request.url.encodedPath)
                    """[{"linia":"6","atMs":${departure + 2 * 60_000L},"delayMin":2,"source":"live"}]"""
                }
                request.url.encodedPath.endsWith("/stops") -> """[{"id":24,"nazwa":"Przy szkole","lat":53.12194,"lon":18.02764}]"""
                request.url.host == "routing.openstreetmap.de" -> """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[180]],"distances":[[250]]}"""
                else -> """{"ok":true,"polaczenia":[{"odcinki":[{"rodzaj":"przejazd","linia":"6","cel":"Dom","zPrzystanku":{"id":24,"nazwa":"Przy szkole"},"doPrzystanku":{"id":41,"nazwa":"Dom"},"odjazdMs":$departure,"przyjazdMs":${departure + 600_000L}}]}]}"""
            }
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val catalog = TransitStopCatalog(context, client)
        val vm = TransitViewModel(prefs, catalog, TransitJourneyRepository(client, catalog), null, TransitLiveRepository(client))
        val owner = ViewModelStore().apply { put("transit", vm) }
        try {
            prefs.select(goal); prefs.setEnabled(true)
            vm.settings.first { it.ready && it.preferences.destination?.key == goal.key }
            val consumer = Any()
            vm.startJourneySession(consumer, 1)
            val trip = vm.journeys.first { it?.journeys?.isNotEmpty() == true }!!.journeys.single()
            val live = withContext(Dispatchers.IO) { withTimeout(10_000) { vm.live.first { it.isNotEmpty() } } }
            assertEquals(LiveStatus(2, departure + 2 * 60_000L), live[trip.key])
            vm.stopJourneySession(consumer)
        } finally {
            owner.clear(); ioScope.cancel(); ioScope.coroutineContext[Job]!!.join()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            Dispatchers.resetMain(); directory.deleteRecursively()
        }
    }
}
