package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitJourneyRepositoryTest {
    @Test fun requestUsesNearbyBoardingPlatformsAndDestinationAndReusesLocalStopCatalog() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-repository-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val catalogs = AtomicInteger()
        val plans = AtomicInteger()
        val walks = AtomicInteger()
        val target = TransitDestination("Rondo Jagiellonów", listOf("26"), 53.1236, 18.007)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("/stops")) {
                catalogs.incrementAndGet()
                """{"stops":[{"id":24,"name":"Jagiellońska - Łużycka","lat":53.12194,"lon":18.02764}]}"""
            } else if (request.url.host == "routing.openstreetmap.de") {
                walks.incrementAndGet()
                assertTrue(request.url.encodedPath.contains("routed-foot/table/v1/foot"))
                """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[192]],"distances":[[240.2]]}"""
            } else {
                plans.incrementAndGet()
                assertEquals("POST", request.method)
                assertEquals("/v2/cities/bydgoszcz/journeys", request.url.encodedPath)
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = JSONObject(buffer.readUtf8())
                assertEquals(24, body.getJSONObject("from").getInt("stopId"))
                assertFalse(body.getJSONObject("from").has("lat"))
                assertEquals(target.longitude, body.getJSONObject("to").getDouble("lon"), 0.0)
                assertEquals(plans.get() - 1, body.getInt("maxTransfers")); assertFalse(body.getBoolean("shape"))
                assertTrue(body.getLong("whenMs") in System.currentTimeMillis() - 60_000L..System.currentTimeMillis())
                val depart = System.currentTimeMillis() + 600_000L
                """{"journeys":[{"legs":[{"mode":"transit","line":"6","destination":"Bielawy",
                    "fromStop":{"id":24,"name":"Jagiellońska - Łużycka"},
                    "toStop":{"id":26,"name":"Rondo Jagiellonów"},
                    "departureMs":$depart,"arrivalMs":${depart + 240_000L}}]}]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val repo = TransitJourneyRepository(client, TransitStopCatalog(context, client))
            repeat(2) {
                val initial = repo.find(target, allowTransfers = it == 1)
                if (it == 0) {
                    assertEquals(0, walks.get())
                    assertTrue(initial.journeys.single().walkPending)
                    assertFalse(initial.journeys.single().walkAvailable)
                } else assertTrue(initial.journeys.single().walkAvailable)
                val result = repo.enrichWalking(initial)
                assertEquals(target.key, result.destinationKey)
                assertEquals("26", result.journeys.single().rides.last().toId)
                assertTrue(result.journeys.single().walkAvailable)
                assertEquals(4, result.journeys.single().walkMinutes)
                assertEquals(240.2, result.journeys.single().distanceMeters, 0.01)
            }
            assertEquals(1, catalogs.get())
            assertEquals(2, plans.get())
            assertEquals(1, walks.get())
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }
    @Test fun preferredOriginAndContinuationCursorAreSentAndReturned() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-paging-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val target = TransitDestination("Cel", listOf("26"), 53.1236, 18.007)
        val origin = TransitDestination("Start", listOf("24"), 53.12194, 18.02764)
        val cursor = System.currentTimeMillis() + 900_000L
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.endsWith("/stops") -> """{"stops":[{"id":24,"name":"Start","lat":53.12194,"lon":18.02764}]}"""
                request.url.host == "routing.openstreetmap.de" -> """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[192]],"distances":[[240.2]]}"""
                else -> {
                    val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                    val body = JSONObject(buffer.readUtf8())
                    assertEquals(24, body.getJSONObject("from").getInt("stopId"))
                    assertEquals(cursor, body.getLong("whenMs"))
                    """{"nextWhenMs":${cursor + 600_000L},"journeys":[{"legs":[{"mode":"transit","line":"6","destination":"Bielawy","fromStop":{"id":24,"name":"Start"},"toStop":{"id":26,"name":"Cel"},"departureMs":${cursor + 60_000L},"arrivalMs":${cursor + 300_000L}}]}]}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val result = TransitJourneyRepository(client, TransitStopCatalog(context, client)).find(target, origin, cursor)
            assertEquals(origin.key, result.originKey)
            // Następne szukanie minutę po odjeździe, a nie od nextWhenMs planera (cursor + 10 min).
            assertEquals(cursor + 120_000L, result.nextWhenMs)
            assertEquals(cursor + 60_000L, result.journeys.single().departureMs)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); directory.deleteRecursively() }
    }
    @Test fun selectedStopQueriesAllItsPlatformsAndRejectsNearbyAlternatives() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-origin-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val target = TransitDestination("Cel", listOf("27"), 53.12, 18.007)
        val origin = TransitDestination("Wybrany", listOf("24", "25"), 53.123, 18.027)
        val now = System.currentTimeMillis()
        val requested = java.util.Collections.synchronizedSet(mutableSetOf<Int>())
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("/stops")) {
                """{"stops":[{"id":24,"name":"Wybrany","lat":53.123,"lon":18.027},{"id":25,"name":"Wybrany","lat":53.1231,"lon":18.0271},{"id":26,"name":"Inny","lat":53.1232,"lon":18.0272}]}"""
            } else {
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = JSONObject(buffer.readUtf8())
                val from = body.getJSONObject("from").getInt("stopId")
                requested += from
                assertTrue(body.getLong("whenMs") <= System.currentTimeMillis())
                fun trip(id: Int, depart: Long) = """{"legs":[{"mode":"transit","line":"5","destination":"Cel","fromStop":{"id":$id,"name":"Przystanek"},"toStop":{"id":27,"name":"Cel"},"departureMs":$depart,"arrivalMs":${depart + 300_000L}}]}"""
                """{"journeys":[${trip(from, now + (if (from == 24) 60_000L else 180_000L))},${trip(26, now + 30_000L)}]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        try {
            val result = TransitJourneyRepository(client, TransitStopCatalog(context, client)).find(target, origin)
            assertEquals(setOf(24, 25), requested)
            assertEquals(listOf("24", "25"), result.journeys.map { it.rides.first().fromId })
            assertEquals(now + 60_000L, result.journeys.first().departureMs)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); directory.deleteRecursively() }
    }

    @Test fun pagingPreservesEachPlatformWindowAndSkipsExhaustedPlatforms() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-platform-cursors-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val target = TransitDestination("Cel", listOf("27"), 53.12, 18.007)
        val origin = TransitDestination("Start", listOf("24", "25"), 53.123, 18.027)
        val now = System.currentTimeMillis()
        val counts = java.util.concurrent.ConcurrentHashMap<Int, AtomicInteger>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("/stops")) {
                """{"stops":[{"id":24,"name":"Start","lat":53.123,"lon":18.027},{"id":25,"name":"Start","lat":53.1231,"lon":18.0271}]}"""
            } else {
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = JSONObject(buffer.readUtf8())
                val id = body.getJSONObject("from").getInt("stopId")
                val call = counts.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
                val whenMs = body.getLong("whenMs")
                // Dalej szuka się minutę po odjeździe z odpowiedzi (nie od nextWhenMs planera, który przeskakuje kursy).
                if (call == 2) assertEquals(now + if (id == 24) 660_000L else 960_000L, whenMs)
                if (call == 3) { assertEquals(25, id); assertEquals(now + 1_080_000L, whenMs) }
                val departure = if (call == 1) now + if (id == 24) 600_000L else 900_000L else whenMs + 60_000L
                // Bez nextWhenMs (0) planer nie ma dalszych połączeń z tego stanowiska.
                val next = if (call == 1 || (id == 25 && call == 2)) departure + 20 * 60_000L else 0L
                """{"nextWhenMs":$next,"journeys":[{"legs":[{"mode":"transit","line":"5","destination":"Cel","fromStop":{"id":$id,"name":"Start"},"toStop":{"id":27,"name":"Cel"},"departureMs":$departure,"arrivalMs":${departure + 240_000L}}]}]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        try {
            val repo = TransitJourneyRepository(client, TransitStopCatalog(context, client))
            val first = repo.find(target, origin)
            assertEquals(mapOf("24" to now + 660_000L, "25" to now + 960_000L), first.platformCursors)
            val second = repo.find(target, origin, first.nextWhenMs, platformCursors = first.platformCursors)
            assertEquals(mapOf("25" to now + 1_080_000L), second.platformCursors)
            val third = repo.find(target, origin, second.nextWhenMs, platformCursors = second.platformCursors)
            assertEquals(listOf("25"), third.journeys.map { it.rides.first().fromId })
            assertNull(third.nextWhenMs)
            assertEquals(emptyMap<String, Long>(), third.platformCursors)
            val exhausted = repo.find(target, origin, now + 1_500_000L, platformCursors = third.platformCursors)
            assertTrue(exhausted.journeys.isEmpty())
            assertEquals(2, counts[24]!!.get())
            assertEquals(3, counts[25]!!.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); directory.deleteRecursively() }
    }

    @Test fun oneFailedPlatformKeepsOtherDepartures() = platformFailure(allFail = false, malformed = false)
    @Test fun malformedPlatformDoesNotDiscardOtherDepartures() = platformFailure(allFail = false, malformed = true)
    @Test fun allFailedPlatformsRemainAnErrorRatherThanAnEmptyTimetable() = platformFailure(allFail = true, malformed = false)

    private fun platformFailure(allFail: Boolean, malformed: Boolean) = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-partial-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val target = TransitDestination("Cel", listOf("27"), 53.12, 18.007)
        val origin = TransitDestination("Start", listOf("24", "25"), 53.123, 18.027)
        val now = System.currentTimeMillis()
        val plans = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            var code = 200
            val json = if (request.url.encodedPath.endsWith("/stops")) {
                """{"stops":[{"id":24,"name":"Start","lat":53.123,"lon":18.027},{"id":25,"name":"Start","lat":53.1231,"lon":18.0271}]}"""
            } else {
                plans.incrementAndGet()
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val id = JSONObject(buffer.readUtf8()).getJSONObject("from").getInt("stopId")
                if (allFail || id == 25) {
                    if (!malformed) code = 503
                    "niepoprawna odpowiedź"
                } else {
                    """{"journeys":[{"legs":[{"mode":"transit","line":"5","destination":"Cel","fromStop":{"id":24,"name":"Start"},"toStop":{"id":27,"name":"Cel"},"departureMs":${now + 60_000L},"arrivalMs":${now + 300_000L}}]}]}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test").body(json.toResponseBody()).build()
        }.build()
        try {
            val outcome = runCatching { TransitJourneyRepository(client, TransitStopCatalog(context, client)).find(target, origin) }
            assertEquals(2, plans.get())
            if (allFail) assertTrue(outcome.exceptionOrNull() is java.io.IOException)
            else {
                val result = outcome.getOrThrow()
                assertTrue(result.partialFailure)
                assertEquals("24", result.journeys.single().rides.first().fromId)
                assertTrue(result.canReuse(target.key, System.currentTimeMillis(), origin.key))
            }
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); directory.deleteRecursively() }
    }

}
