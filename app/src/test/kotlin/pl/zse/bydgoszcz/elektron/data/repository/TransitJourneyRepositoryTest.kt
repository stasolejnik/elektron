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
    @Test fun requestUsesFixedSchoolAndDestinationAndReusesLocalStopCatalog() = runBlocking {
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
                """[{"id":24,"nazwa":"Jagiellońska - Łużycka","lat":53.12194,"lon":18.02764}]"""
            } else if (request.url.host == "routing.openstreetmap.de") {
                walks.incrementAndGet()
                assertTrue(request.url.encodedPath.contains("routed-foot/table/v1/foot"))
                """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[192]],"distances":[[240.2]]}"""
            } else {
                plans.incrementAndGet()
                assertEquals("POST", request.method)
                assertEquals("/bydgoszcz/api/polaczenia", request.url.encodedPath)
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = JSONObject(buffer.readUtf8())
                assertEquals(SchoolTransit.LATITUDE, body.getJSONObject("from").getDouble("lat"), 0.0)
                assertEquals(target.longitude, body.getJSONObject("to").getDouble("lon"), 0.0)
                assertEquals(plans.get() - 1, body.getInt("maxPrzesiadki")); assertFalse(body.getBoolean("ksztalt"))
                assertTrue(body.getLong("whenMs") > System.currentTimeMillis())
                val depart = System.currentTimeMillis() + 600_000L
                """{"ok":true,"polaczenia":[{"odcinki":[{"rodzaj":"przejazd","linia":"6","cel":"Bielawy",
                    "zPrzystanku":{"id":24,"nazwa":"Jagiellońska - Łużycka"},
                    "doPrzystanku":{"id":26,"nazwa":"Rondo Jagiellonów"},
                    "odjazdMs":$depart,"przyjazdMs":${depart + 240_000L}}]}]}"""
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
                request.url.encodedPath.endsWith("/stops") -> """[{"id":24,"nazwa":"Start","lat":53.12194,"lon":18.02764}]"""
                request.url.host == "routing.openstreetmap.de" -> """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[192]],"distances":[[240.2]]}"""
                else -> {
                    val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                    val body = JSONObject(buffer.readUtf8())
                    assertEquals(origin.latitude, body.getJSONObject("from").getDouble("lat"), 0.0)
                    assertEquals(cursor, body.getLong("whenMs"))
                    """{"ok":true,"nastepneWhenMs":${cursor + 600_000L},"polaczenia":[{"odcinki":[{"rodzaj":"przejazd","linia":"6","cel":"Bielawy","zPrzystanku":{"id":24,"nazwa":"Start"},"doPrzystanku":{"id":26,"nazwa":"Cel"},"odjazdMs":${cursor + 60_000L},"przyjazdMs":${cursor + 300_000L}}]}]}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val result = TransitJourneyRepository(client, TransitStopCatalog(context, client)).find(target, origin, cursor)
            assertEquals(origin.key, result.originKey)
            assertEquals(cursor + 600_000L, result.nextWhenMs)
            assertEquals(cursor + 60_000L, result.journeys.single().departureMs)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); directory.deleteRecursively() }
    }
}
