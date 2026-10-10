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
import java.io.File
import java.util.Collections

/** Odjazdy: kolejność i liczba zapytań, wyniki cząstkowe, zapis ostatniego wyniku. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitSmartQueriesTest {
    private val target = TransitDestination("Cel", listOf("99"), 53.1236, 18.007)

    private val depart = System.currentTimeMillis() + 900_000L

    private fun fixture(directory: File, requests: MutableList<Int>): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val json = when {
            request.url.encodedPath.endsWith("/stops") ->
                """[{"id":24,"nazwa":"A","lat":53.12340,"lon":18.02664},{"id":25,"nazwa":"B","lat":53.12360,"lon":18.02664},{"id":26,"nazwa":"C","lat":53.12380,"lon":18.02664}]"""
            request.url.host == "routing.openstreetmap.de" -> """{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[120]],"distances":[[100]]}"""
            else -> {
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = JSONObject(buffer.readUtf8())
                val stop = body.getJSONObject("from").getInt("stopId")
                val whenMs = body.getLong("whenMs")
                requests += stop
                // Tylko stanowisko 25 ma kurs do celu; pozostałe zwracają puste okno z kursorem.
                if (stop == 25) {
                    """{"ok":true,"nastepneWhenMs":${whenMs + 600_000L},"polaczenia":[{"odcinki":[{"rodzaj":"przejazd","linia":"6","cel":"Kierunek",
                        "zPrzystanku":{"id":25,"nazwa":"B"},"doPrzystanku":{"id":99,"nazwa":"Cel"},"odjazdMs":$depart,"przyjazdMs":${depart + 300_000L}}]}]}"""
                } else """{"ok":true,"nastepneWhenMs":${whenMs + 600_000L},"polaczenia":[]}"""
            }
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(json.toResponseBody("application/json".toMediaType())).build()
    }.build()

    @Test fun platformsWithoutRidesGetOneRequestAfterAFullCheckAndPartialResultsArePublished() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-smart-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val requests = Collections.synchronizedList(mutableListOf<Int>())
        val client = fixture(directory, requests)
        try {
            val repo = TransitJourneyRepository(client, TransitStopCatalog(context, client))
            val partials = Collections.synchronizedList(mutableListOf<TransitJourneyRepository.Result>())
            val first = repo.find(target, onPartial = { partials += it })
            // Pierwsze sprawdzenie: puste stanowiska dostają drugie okno (2 + 1 + 2 zapytania).
            assertEquals(5, requests.size)
            assertEquals(listOf(25), first.journeys.map { it.rides.first().fromId.toInt() })
            assertTrue(first.complete)
            assertEquals(1, partials.size)
            assertFalse("wynik cząstkowy nie może być użyty ponownie", partials.single().complete)
            assertFalse(partials.single().canReuse(target.key, System.currentTimeMillis()))
            assertEquals(first.journeys.map { it.key }, partials.single().journeys.map { it.key })

            requests.clear()
            val second = repo.find(target)
            // Kolejne: stanowiska bez kursu - jedno zapytanie; stanowisko z kursem - jak zawsze.
            assertEquals(3, requests.size)
            assertEquals(setOf(24, 25, 26), requests.toSet())
            assertEquals(first.journeys.map { it.key }, second.journeys.map { it.key })

            // Inny cel albo przesiadki - pełne sprawdzenie od nowa.
            requests.clear()
            repo.find(target, allowTransfers = true)
            assertEquals(5, requests.size)

            // Wiedza wygasa (pora dnia) - znów pełne sprawdzenie.
            repo.knowledgeTtlMs = 0
            requests.clear()
            repo.find(target)
            assertEquals(5, requests.size)
        } finally {
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); directory.deleteRecursively()
        }
    }

    @Test fun lastCompleteResultIsRestoredWithUpcomingDeparturesOnly() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "transit-cache-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = directory }
        val requests = Collections.synchronizedList(mutableListOf<Int>())
        val client = fixture(directory, requests)
        val file = File(directory, "last.json")
        try {
            val catalog = TransitStopCatalog(context, client)
            val repo = TransitJourneyRepository(client, catalog, TransitWalkingRepository(client), file)
            val now = System.currentTimeMillis()
            val ride = { dep: Long -> TransitJourney(listOf(TransitRide("6", "Kierunek", "25", "B", "99", "Cel", dep, dep + 300_000L)), 2, 100.0) }
            val stored = TransitJourneyRepository.Result(target.key, listOf(ride(now - 60_000L), ride(now + 600_000L)), now - 120_000L,
                nextWhenMs = now + 3_600_000L, boardingStops = listOf(TransitDestination("B", listOf("25"), 53.1236, 18.02664)),
                platformCursors = mapOf("25" to now + 3_600_000L))
            repo.saveLast(stored)
            repo.saveLast(stored.copy(journeys = emptyList(), complete = false))   // cząstkowego nie zapisujemy
            val restored = TransitJourneyRepository(client, catalog, TransitWalkingRepository(client), file).loadLast()!!
            assertEquals(listOf(now + 600_000L), restored.journeys.map { it.departureMs })
            assertEquals(stored.fetchedAt, restored.fetchedAt)
            assertEquals(stored.platformCursors, restored.platformCursors)
            assertEquals(stored.nextWhenMs, restored.nextWhenMs)
            assertEquals(stored.boardingStops, restored.boardingStops)
            // Stary zapis albo bez nadchodzących kursów - nic.
            assertNull(TransitJourneyRepository.restored(stored, stored.fetchedAt + TransitJourneyRepository.CACHE_MAX_AGE_MS + 1))
            assertNull(TransitJourneyRepository.restored(stored, now + 3_600_000L))
            // Uszkodzony plik - brak zapisu zamiast wyjątku.
            file.writeText("{nie json")
            assertNull(TransitJourneyRepository(client, catalog, TransitWalkingRepository(client), file).loadLast())
            assertNull(TransitResultCodec.decode("""{"v":2}"""))
        } finally {
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); directory.deleteRecursively()
        }
    }
}
