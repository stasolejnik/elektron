package pl.zse.bydgoszcz.elektron.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Binarne API BUSearch (/v2/app): te same odpowiedzi pobrane w obu formatach (22.11.2026). Dekoder
 * ma dawać JSON wersji JSON - wszystkie pola, które zdekodował, są w JSON z tą samą wartością,
 * a parsery Przejazdów zwracają to samo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BusearchProtoTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/transit/$name")!!.use { it.readBytes() }
    private fun json(name: String) = JSONObject(String(bytes(name), Charsets.UTF_8))

    /** Pola zaokrąglane w wersji binarnej (całe stopnie, km/h, minuty). */
    private val rounded = setOf("bearing", "movementBearing", "speedKmh", "etaMinutes")

    private fun assertSubset(proto: Any?, json: Any?, path: String) {
        when (proto) {
            is JSONObject -> {
                if (json !is JSONObject) fail("$path: brak obiektu w JSON")
                json as JSONObject
                proto.keys().forEach { key ->
                    if (!json.has(key)) {
                        // Pusta lista w binarnej (zawsze obecna) przy liście pominiętej w JSON.
                        if ((proto.get(key) as? JSONArray)?.length() == 0) return@forEach
                        fail("$path.$key: brak w JSON")
                    }
                    val p = proto.get(key); val j = json.get(key)
                    if (key in rounded && p is Number && j is Number) assertEquals("$path.$key", j.toDouble(), p.toDouble(), 1.0)
                    else assertSubset(p, j, "$path.$key")
                }
            }
            is JSONArray -> {
                if (json !is JSONArray) fail("$path: brak listy w JSON")
                json as JSONArray
                assertEquals("$path: długość", json.length(), proto.length())
                for (i in 0 until proto.length()) assertSubset(proto.get(i), json.get(i), "$path[$i]")
            }
            is Double -> assertEquals(path, (json as Number).toDouble(), proto, 2e-5)
            is Number -> assertEquals(path, (json as Number).toLong(), proto.toLong())
            else -> assertEquals(path, json, proto)
        }
    }

    private fun compare(name: String, message: BusearchProto.Message): Pair<JSONObject, JSONObject> {
        val proto = BusearchProto.decode(bytes("$name.pb"), message)
        val json = json("$name.json")
        assertSubset(proto, json, name)
        return proto to json
    }

    @Test fun journeysWithShapeMatchJson() {
        val (proto, json) = compare("v2_compare_journeys", BusearchProto.JOURNEYS)
        assertTrue(proto.getJSONArray("journeys").length() > 0)
        assertEquals(json.optLong("nextWhenMs"), proto.optLong("nextWhenMs"))
        // Przebieg trasy (różnicowo w 1e-5 stopnia) - te same punkty co [[lat, lon], ...] w JSON.
        // Kurs bez przesiadki (parseShape szuka kursu jednym pojazdem).
        val journeys = proto.getJSONArray("journeys")
        val leg = (0 until journeys.length()).map { journeys.getJSONObject(it).getJSONArray("legs") }.map { legs ->
            (0 until legs.length()).map { legs.getJSONObject(it) }.filter { it.optString("mode") == "transit" }
        }.first { it.size == 1 }.single()
        assertTrue(leg.getJSONArray("shape").length() > 10)
        val shapeOf = { o: JSONObject -> TransitLiveRepository.parseShape(o, leg.getString("line"), leg.getLong("departureMs")) }
        val fromProto = shapeOf(proto)!!; val fromJson = shapeOf(json)!!
        assertEquals(fromJson.size, fromProto.size)
        fromJson.zip(fromProto).forEach { (j, p) -> assertEquals(j.first, p.first, 2e-5); assertEquals(j.second, p.second, 2e-5) }
    }

    @Test fun departuresMatchJson() {
        val (proto, json) = compare("v2_compare_departures", BusearchProto.DEPARTURES)
        assertEquals(TransitLiveRepository.parse(json), TransitLiveRepository.parse(proto))
    }

    @Test fun stopsMatchJson() {
        val (proto, json) = compare("v2_compare_stops", BusearchProto.STOPS)
        assertEquals(TransitStopParser.platforms(json.toString()).map { it.stopIds to it.name },
            TransitStopParser.platforms(proto.toString()).map { it.stopIds to it.name })
    }

    @Test fun vehicleAndStopsMatchJson() {
        val (vehicle, vehicleJson) = compare("v2_compare_vehicle", BusearchProto.VEHICLE)
        val position = TransitLiveRepository.parseVehicle(vehicle)
        val expected = TransitLiveRepository.parseVehicle(vehicleJson)
        // Współrzędne w binarnej z dokładnością 1e-6 stopnia (ok. 10 cm).
        assertEquals(expected.latitude, position.latitude, 1e-6)
        assertEquals(expected.longitude, position.longitude, 1e-6)
        assertEquals(expected.copy(bearing = null, latitude = 0.0, longitude = 0.0), position.copy(bearing = null, latitude = 0.0, longitude = 0.0))
        assertTrue(position.tram)
        val (etas, etasJson) = compare("v2_compare_etas", BusearchProto.ETAS)
        val fromJson = TransitLiveRepository.parseEtas(etasJson); val fromProto = TransitLiveRepository.parseEtas(etas)
        assertEquals(fromJson.size, fromProto.size)
        fromJson.zip(fromProto).forEach { (j, p) ->
            assertEquals(j.latitude, p.latitude, 1e-6); assertEquals(j.longitude, p.longitude, 1e-6)
            assertEquals(j.copy(etaMinutes = null, latitude = 0.0, longitude = 0.0), p.copy(etaMinutes = null, latitude = 0.0, longitude = 0.0))
        }
    }

    @Test fun unknownFieldsAreSkippedAndBrokenDataRejected() {
        // Pole 99 (varint, fixed64, bajty, fixed32) nieznane aplikacji, potem departures[0].line = "5".
        val unknown = byteArrayOf(0x98.toByte(), 0x06, 0x01, 0x99.toByte(), 0x06, 1, 2, 3, 4, 5, 6, 7, 8,
            0x9a.toByte(), 0x06, 0x02, 0x41, 0x42, 0x9d.toByte(), 0x06, 1, 2, 3, 4)
        val departure = byteArrayOf(0x0a, 0x03, 0x0a, 0x01, '5'.code.toByte())
        val decoded = BusearchProto.decode(unknown + departure, BusearchProto.DEPARTURES)
        assertEquals("5", decoded.getJSONArray("departures").getJSONObject(0).getString("line"))
        assertFalse(decoded.getJSONArray("departures").getJSONObject(0).has("delayMinutes"))
        // Ucięta odpowiedź (długość większa niż dane) - błąd, a nie pusta lista.
        runCatching { BusearchProto.decode(byteArrayOf(0x0a, 0x10, 0x0a), BusearchProto.DEPARTURES) }
            .onSuccess { fail("Ucięta odpowiedź przeszła") }
    }
}
