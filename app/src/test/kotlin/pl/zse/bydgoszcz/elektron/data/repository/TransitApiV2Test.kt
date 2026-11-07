package pl.zse.bydgoszcz.elektron.data.repository

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import pl.zse.bydgoszcz.elektron.domain.model.TransitStops

/** BUSearch API v2: prawdziwa odpowiedź planera (Rycerska -> szkoła, 7.11.2026), katalog i dociąganie kursów. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitApiV2Test {
    private fun resource(name: String) = javaClass.getResourceAsStream("/transit/$name")!!.bufferedReader().use { it.readText() }

    private fun page(nextWhenMs: Long?, vararg departures: Long) = JSONObject(
        """{${nextWhenMs?.let { "\"nextWhenMs\":$it," } ?: ""}"journeys":[${departures.joinToString(",") { d ->
            """{"legs":[{"mode":"walk","fromStop":{"id":null,"name":"Miejsce startu"},"toStop":{"id":"1","name":"A"},"departureMs":${d - 60_000},"arrivalMs":$d},
               {"mode":"transit","line":"5","destination":"X","fromStop":{"id":"1","name":"A"},"toStop":{"id":"2","name":"B"},"departureMs":$d,"arrivalMs":${d + 600_000}}]}"""
        }}]}""")

    @Test fun catalogGroupsPolesOfOneStopWithoutTheirNumbers() {
        val stops = TransitStopParser.parse(resource("v2_stops_rycerska.json"))
        val rycerska = stops.single { it.name == "Rycerska" }
        // Wszystkie stanowiska (tramwaje 5, 8, 9 i autobusy) - dawniej osobne "Rycerska #1..#8".
        assertEquals(setOf("1147", "1128", "1148", "804", "1143", "1151", "1145", "1150"), rycerska.stopIds.toSet())
        assertTrue(stops.none { '#' in it.name })
    }

    @Test fun realPlannerResponseGivesRidesFromTheHomeStop() {
        val platforms = TransitStopParser.platforms(resource("v2_stops_rycerska.json")).associateBy { it.stopIds.single() }
        val home = TransitStopParser.parse(resource("v2_stops_rycerska.json")).single { it.name == "Rycerska" }
        val response = JSONObject(resource("v2_to_school_2026-11-07.json"))
        val trips = TransitJourneyParser.parseToSchool(response, home, platforms, response.getLong("searchedFromMs"), allowTransfers = true)
        assertTrue(trips.isNotEmpty())
        assertTrue(trips.all { it.rides.first().fromId in home.stopIds && it.schoolArrivalMs != null })
        assertTrue(trips.any { it.rides.single().line == "5" && it.rides.single().toName == "Jagiellońska - Łużycka" })
    }

    @Test fun searchContinuesAMinuteAfterTheEarliestDeparture() {
        val w = 1_800_000_000_000L
        // Planer przeskakuje o 20 min - kursy 5 co 10 min znikały. Teraz minutę po najwcześniejszym odjeździe.
        assertEquals(w + 6 * 60_000L, TransitJourneyParser.nextCursor(page(w + 20 * 60_000L, w + 5 * 60_000L, w + 9 * 60_000L), w))
        // nextWhenMs stoi w miejscu (poniedziałek 7:00) - i tak idziemy dalej.
        assertEquals(w + 2 * 60_000L, TransitJourneyParser.nextCursor(page(w, w + 60_000L), w))
        // Strona bez kursów z Twojego przystanku - dalej o okno planera, a nie minuta po minucie.
        assertEquals(w + 20 * 60_000L, TransitJourneyParser.nextCursor(page(w + 20 * 60_000L, w + 60_000L), w, matched = false))
        // Bez nextWhenMs - koniec połączeń.
        assertNull(TransitJourneyParser.nextCursor(page(null, w + 60_000L), w))
        assertEquals(w + 30 * 60_000L, TransitJourneyParser.nextCursor(page(w + 30 * 60_000L), w))
    }

    @Test fun savedSinglePoleReturnsToTheWholeStop() {
        val catalog = TransitStopParser.parse(resource("v2_stops_rycerska.json"))
        val saved = TransitDestination("Rycerska #2", listOf("1128"), 53.13724, 17.99887)
        val refreshed = TransitStops.refreshed(saved, catalog)!!
        assertEquals("Rycerska", refreshed.name)
        assertTrue("1128" in refreshed.stopIds && refreshed.stopIds.size == 8)
    }

    @Test fun nearbySchoolStopsAddRidesToOtherStopsNearTheSchool() {
        val platforms = TransitStopParser.platforms(resource("v2_stops_rycerska.json")).associateBy { it.stopIds.single() }
        val home = TransitStopParser.parse(resource("v2_stops_rycerska.json")).single { it.name == "Rycerska" }
        val jag = platforms.getValue("11")
        val target = TransitDestination("Jagiellońska - Łużycka", listOf("11"), jag.latitude, jag.longitude)
        val response = JSONObject(resource("v2_to_school_2026-11-07.json"))
        val now = response.getLong("searchedFromMs")
        val only = TransitJourneyParser.parseToSchool(response, home, platforms, now, allowTransfers = true, target = target)
        val nearby = TransitJourneyParser.parseToSchool(response, home, platforms, now, allowTransfers = true, target = target, otherSchoolStops = true)
        assertEquals(listOf("5"), only.map { it.rides.single().line })
        // Np. 67 do "Skłodowskiej-Curie - Jurasza" - z dojściem do szkoły liczonym przez planer.
        assertTrue(nearby.size > only.size)
        assertTrue(nearby.any { it.rides.last().toName == "Skłodowskiej-Curie - Jurasza" && it.schoolArrivalMs != null })
        assertTrue(nearby.any { it.rides.single().line == "5" })
        // Inny klucz wyniku niż sam przystanek - bez pomieszania z zapisanym wynikiem.
        assertTrue(pl.zse.bydgoszcz.elektron.domain.model.TransitStops.originKey(target, true) != target.key)
    }
}
