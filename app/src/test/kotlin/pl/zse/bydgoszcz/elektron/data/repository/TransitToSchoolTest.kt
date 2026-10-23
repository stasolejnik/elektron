package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.*
import pl.zse.bydgoszcz.elektron.domain.util.AppClock
import java.io.File

/** Przejazdy do szkoły: prawdziwa odpowiedź planera (z Szarych Szeregów pod szkołę, 26.10.2026 od 7:00). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitToSchoolTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource("transit/$name")!!.readText()
    private val searchedAt = 1792994400000L
    private val platforms by lazy { TransitStopParser.platforms(resource("stops_to_school.json")).associateBy { it.stopIds.single() } }
    private val home by lazy { TransitStopParser.group(platforms.values.toList()).single { it.name == "Szarych Szeregów" } }

    @Test fun parsesRidesFromTheHomeStopWithArrivalAtSchool() {
        val trips = TransitJourneyParser.parseToSchool(JSONObject(resource("planner_to_school.json")), home, platforms, searchedAt, allowTransfers = true)
        // Kurs z innego przystanku (Wojska Polskiego) odrzucony; pozostałe z Szarych Szeregów.
        assertEquals(4, trips.size)
        assertTrue(trips.all { it.rides.first().fromId in home.stopIds })
        trips.forEach { trip ->
            val atSchool = trip.schoolArrivalMs!!
            assertTrue(atSchool >= trip.arrivalMs)
            assertEquals(((atSchool - trip.arrivalMs + 59_999) / 60_000).toInt(), trip.walkMinutes)
            assertTrue(SchoolTransit.distance(platforms[trip.rides.last().toId]!!.latitude, platforms[trip.rides.last().toId]!!.longitude) <= 1200)
        }
        val direct = TransitJourneyParser.parseToSchool(JSONObject(resource("planner_to_school.json")), home, platforms, searchedAt, allowTransfers = false)
        assertTrue(direct.isNotEmpty() && direct.all { it.transfers == 0 })
        val ranked = TransitJourneys.rankToSchool(trips, searchedAt)
        assertEquals(ranked.sortedBy { it.departureMs }, ranked)
        assertEquals(ranked.map { it.key }.distinct(), ranked.map { it.key })
    }

    @Test fun findToSchoolAsksOncePerPageFromTheHomeStopToTheSchool() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "to-school-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        var plans = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("/stops")) resource("stops_to_school.json") else {
                plans++
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = JSONObject(buffer.readUtf8())
                assertEquals(home.latitude, body.getJSONObject("from").getDouble("lat"), 1e-9)
                assertEquals(SchoolTransit.LATITUDE, body.getJSONObject("to").getDouble("lat"), 1e-9)
                assertEquals(searchedAt, body.getLong("whenMs"))
                assertEquals(0, body.getInt("maxPrzesiadki"))
                resource("planner_to_school.json")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        AppClock.simulate(java.time.LocalDateTime.of(2026, 10, 26, 6, 50))
        try {
            val repository = TransitJourneyRepository(client, TransitStopCatalog(context, client))
            val result = repository.findToSchool(home, searchedAt)
            assertEquals(1, plans)
            assertEquals(TransitDirection.TO_SCHOOL, result.direction)
            assertEquals(home.key, result.destinationKey)
            assertNull(result.originKey)
            assertEquals(1792995181197L, result.nextWhenMs)
            assertTrue(result.journeys.isNotEmpty() && result.journeys.all { it.transfers == 0 && it.schoolArrivalMs != null && !it.walkPending })
            // Zapis i odczyt z kierunkiem.
            val decoded = TransitResultCodec.decode(TransitResultCodec.encode(result))!!
            assertEquals(result, decoded)
            assertTrue(result.canReuse(home.key, result.fetchedAt + 60_000L, null, false, TransitDirection.TO_SCHOOL, searchedAt))
            assertFalse(result.canReuse(home.key, result.fetchedAt + 60_000L, null, false, TransitDirection.FROM_SCHOOL, null))
            assertFalse(result.canReuse(home.key, result.fetchedAt + 60_000L, null, false, TransitDirection.TO_SCHOOL, searchedAt + 12 * 60 * 60_000L))
        } finally { AppClock.reset() }
    }

    @Test fun liveBoardParsesDelaysAndSkipsBrokenEntries() {
        val board = TransitLiveRepository.parse("""[
            {"linia":"10","cel":"Zajezdnia","atMs":1792789529124,"delayMin":-1,"source":"live"},
            {"linia":"10","cel":"Zajezdnia","atMs":1792791060000,"source":"municipal_schedule"},
            {"linia":"","atMs":1},
            {"linia":"4","atMs":1792789410659,"source":"live"}]""")
        assertEquals(listOf(LiveDeparture("10", 1792789529124, -1, true), LiveDeparture("10", 1792791060000, null, false),
            LiveDeparture("4", 1792789410659, null, true)), board)
    }
}
