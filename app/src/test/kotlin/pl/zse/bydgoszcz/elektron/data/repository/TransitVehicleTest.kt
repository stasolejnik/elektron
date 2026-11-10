package pl.zse.bydgoszcz.elektron.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.VehicleTrack

/** Pozycja pojazdu i jego przystanki z prawdziwych odpowiedzi BUSearch v2 (tramwaj 8, 10.11.2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitVehicleTest {
    private fun resource(name: String) = javaClass.getResourceAsStream("/transit/$name")!!.use { it.readBytes().toString(Charsets.UTF_8) }

    @Test fun vehiclePositionAndStopsAreRead() {
        val position = TransitLiveRepository.parseVehicle(resource("v2_vehicle.json"))
        assertEquals("8", position.line)
        assertTrue(position.tram)
        assertTrue(position.latitude in 53.0..53.3 && position.longitude in 17.8..18.2)
        val stops = TransitLiveRepository.parseEtas(resource("v2_vehicle_etas.json"))
        assertEquals(17, stops.size)
        // Minięte przystanki na początku trasy, ETA w pełnych minutach (zaokrąglone w górę).
        assertTrue(stops.first().passed)
        val upcoming = stops.first { !it.passed && it.etaMinutes != null }
        assertTrue(upcoming.etaMinutes!! >= 1)
        // Ile przystanków do wybranego (dalszego) stanowiska.
        val target = stops.last()
        val track = VehicleTrack(position, stops, 0L)
        val towards = track.towards(target.id)!!
        assertEquals(target.id, towards.first.id)
        assertEquals(stops.size - 1 - stops.indexOfFirst { !it.passed }, towards.second)
    }

    @Test fun departureCarriesTheVehicleId() {
        val board = TransitLiveRepository.parse("""{"departures":[{"line":"8","atMs":1794342047586,"source":"live","delayMinutes":-1,"vehicleId":"ISKA6900"},
            {"line":"2","atMs":1794341760000,"source":"timetable"}]}""")
        assertEquals("ISKA6900", board.first().vehicleId)
        assertEquals(null, board.last().vehicleId)
    }
}
