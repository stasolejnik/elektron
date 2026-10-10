package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.SchoolTransit
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import pl.zse.bydgoszcz.elektron.domain.model.TransitStops

/**
 * Odjazdy: przystanki o tej samej nazwie, nieaktualne numery stanowisk, trasy piesze po powrocie sieci.
 * Robolectric: trasa piesza czyta odpowiedź przez org.json z Androida (w zwykłym teście JVM jego
 * metody zwracają wartości domyślne).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitStopsRefreshTest {
    private fun platform(id: String, name: String, lat: Double, lon: Double) = TransitDestination(name, listOf(id), lat, lon)

    @Test fun sameNameFarApartAreSeparateStopsButAComplexStaysOne() {
        val stops = TransitStopParser.group(listOf(
            platform("1", "Szkoła", 53.120, 18.000), platform("2", "Szkoła", 53.1205, 18.0005),   // ok. 65 m - jeden zespół
            platform("3", "Szkoła", 53.200, 18.200),                                               // ok. 16 km - inny przystanek
            platform("4", "Rondo", 53.121, 18.001)))
        assertEquals(listOf("Rondo", "Szkoła", "Szkoła"), stops.map { it.name })
        assertEquals(setOf(setOf("1", "2"), setOf("3")), stops.filter { it.name == "Szkoła" }.map { it.stopIds.toSet() }.toSet())
        // Bliższy szkole przystanek o tej samej nazwie - pierwszy na liście.
        val school = stops.filter { it.name == "Szkoła" }
        assertTrue(SchoolTransit.distance(school[0].latitude, school[0].longitude) < SchoolTransit.distance(school[1].latitude, school[1].longitude))
    }

    @Test fun savedStopWithChangedPlatformNumbersIsReplacedByTheSameStop() {
        val catalog = listOf(TransitDestination("Rondo Jagiellonów", listOf("9", "27"), 53.1236, 18.007),
            TransitDestination("Szkoła", listOf("40"), 53.12, 18.0), TransitDestination("Szkoła", listOf("41"), 53.2, 18.2))
        // Aktualny zapis - bez zmian.
        assertNull(TransitStops.refreshed(catalog[0], catalog))
        assertNull(TransitStops.refreshed(null, catalog))
        // Nowe stanowisko w zespole ("9","26" -> "9","27"): ten sam przystanek z katalogu.
        assertEquals(catalog[0], TransitStops.refreshed(TransitDestination("Rondo Jagiellonów", listOf("9", "26"), 53.1236, 18.007), catalog))
        // Dwa przystanki o tej nazwie, żadnych wspólnych stanowisk - bliższy zapisanemu położeniu.
        assertEquals(catalog[2], TransitStops.refreshed(TransitDestination("Szkoła", listOf("77"), 53.199, 18.199), catalog))
        // Przystanek początkowy: tylko taki, jaki oferuje wybór (do 1 km od szkoły) - "Szkoła" przy 53.2/18.2 odpada.
        assertNull(TransitStops.refreshed(TransitDestination("Szkoła", listOf("77"), 53.199, 18.199), listOf(catalog[2]), maxFromSchoolMeters = 1000.0))
        // Przystanku o tej nazwie już nie ma - zostaje jak jest.
        assertNull(TransitStops.refreshed(TransitDestination("Zlikwidowany", listOf("5"), 53.1, 18.0), catalog))
    }

    @Test fun walkingIsRetriedAfterANetworkFailureOnlyOnceTheCooldownPassed() = runBlocking {
        var offline = true
        var clock = 1_000_000L
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (offline) throw java.io.IOException("Offline")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":"Ok","sources":[{"distance":0}],"destinations":[{"distance":0}],"durations":[[300]],"distances":[[400]]}""".toResponseBody()).build()
        }.build()
        val walking = TransitWalkingRepository(client, null) { clock }
        val stop = platform("24", "Start", 53.12194, 18.02764)
        try {
            assertFalse(walking.retryDue())
            assertTrue(walking.fromSchool(listOf(stop)).isEmpty())
            assertFalse("w przerwie po błędzie - bez ponawiania", walking.retryDue())
            clock += 61_000L
            assertTrue(walking.retryDue())
            offline = false
            assertEquals(5, walking.fromSchool(listOf(stop))["24"]?.minutes)
            assertFalse(walking.retryDue())
        } finally { client.dispatcher.executorService.shutdown() }
    }
}
