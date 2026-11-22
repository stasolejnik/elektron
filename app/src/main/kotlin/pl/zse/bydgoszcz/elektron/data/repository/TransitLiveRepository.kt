package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import pl.zse.bydgoszcz.elektron.domain.model.LiveDeparture
import pl.zse.bydgoszcz.elektron.domain.model.VehiclePosition
import pl.zse.bydgoszcz.elektron.domain.model.VehicleStop
import pl.zse.bydgoszcz.elektron.domain.model.VehicleTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tablica odjazdów przystanku z BUSearch (te same dane co planer, bez nowego serwera): opóźnienia
 * śledzonych pojazdów. Tylko na widocznym ekranie Przejazdów i tylko dla kursów odjeżdżających
 * wkrótce - patrz TransitLive.
 */
@Singleton
class TransitLiveRepository @Inject constructor(private val client: OkHttpClient) {
    suspend fun departures(platformId: String, line: String): List<LiveDeparture> = withContext(Dispatchers.IO) {
        require(platformId.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
        val url = "${BusearchProto.BASE}/stops/$platformId/departures".toHttpUrl().newBuilder()
            .addQueryParameter("line", line.take(32)).build()
        parse(BusearchProto.fetch(client, Request.Builder().url(url).get().build(), BusearchProto.DEPARTURES, 500_000, "Tablica odjazdów")!!)
    }

    /**
     * Pozycja pojazdu i jego przystanki (po dotknięciu kursu, co kilka sekund, dopóki okno jest otwarte).
     * Wysyłany jest tylko identyfikator pojazdu z tablicy odjazdów.
     */
    suspend fun vehicle(vehicleId: String): VehicleTrack = withContext(Dispatchers.IO) {
        require(vehicleId.matches(VEHICLE_ID))
        val base = "${BusearchProto.BASE}/vehicles/$vehicleId"
        val position = parseVehicle(get(base, BusearchProto.VEHICLE, "Pozycja pojazdu"))
        val stops = runCatchingCancellable { parseEtas(get("$base/etas", BusearchProto.ETAS, "Przystanki pojazdu")) }.getOrDefault(emptyList())
        VehicleTrack(position, stops, System.currentTimeMillis())
    }

    /**
     * Przebieg trasy kursu po ulicach/torach (lat, lon) - z planera BUSearch: od pierwszego przystanku kursu
     * o jego godzinie z rozkładu do ostatniego, z "shape". Wysyłane są tylko przystanki kursu (dane publiczne).
     * null - planer nie znalazł tego kursu (wtedy mapa łączy przystanki prostymi odcinkami).
     */
    suspend fun tripShape(line: String, stops: List<VehicleStop>, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): List<Pair<Double, Double>>? =
        withContext(Dispatchers.IO) {
            val first = stops.firstOrNull() ?: return@withContext null
            val last = stops.lastOrNull() ?: return@withContext null
            val start = first.plannedTime ?: return@withContext null
            val stopId = first.id.toIntOrNull() ?: return@withContext null
            var at = now.with(start).withSecond(0).withNano(0)
            if (at.isAfter(now.plusHours(2))) at = at.minusDays(1)   // kurs zaczęty przed północą
            val departure = at.toInstant().toEpochMilli()
            val body = JSONObject().put("from", JSONObject().put("stopId", stopId))
                .put("to", JSONObject().put("lat", last.latitude).put("lon", last.longitude))
                .put("whenMs", departure - 60_000L).put("maxTransfers", 0).put("shape", true).put("limit", 8)
            val request = Request.Builder().url(TransitJourneyRepository.JOURNEYS_URL)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            parseShape(BusearchProto.fetch(client, request, BusearchProto.JOURNEYS, 2_000_000, "Trasa kursu")!!, line, departure)
        }

    private suspend fun get(url: String, message: BusearchProto.Message, what: String): JSONObject =
        BusearchProto.fetch(client, Request.Builder().url(url).get().build(), message, 300_000, what)!!

    internal companion object {
        val VEHICLE_ID = Regex("[A-Za-z0-9_-]{1,32}")

        /**
         * API v2: {"vehicle": {...}}. Pozycja jest zawsze wyliczana przez BUSearch: z przejazdów przez przystanki
         * na żywo ("board") albo z rozkładu i opóźnienia ("timetable") - ta druga (i "positionImprecise") to [VehiclePosition.estimated].
         */
        fun parseVehicle(text: String): VehiclePosition = parseVehicle(JSONObject(text))
        fun parseVehicle(response: JSONObject): VehiclePosition {
            val o = response.getJSONObject("vehicle")
            val lat = o.getDouble("lat"); val lon = o.getDouble("lon")
            require(lat in -90.0..90.0 && lon in -180.0..180.0)
            return VehiclePosition(o.getString("vehicleId"), o.optString("line"), o.optString("type").equals("TRAM", ignoreCase = true),
                lat, lon, o.optDouble("bearing").takeIf { it.isFinite() }, o.optString("destination").takeIf { it.isNotBlank() },
                estimated = o.optString("positionSource") != "board" || o.optBoolean("positionImprecise"),
                stale = o.optBoolean("positionStale"), updatedAtMs = o.optLong("positionUpdatedAtMs").takeIf { it > 0 })
        }

        /** Odcinek tego kursu (ta linia, odjazd w ciągu 2 min od [departureMs]) z odpowiedzi planera - jego "shape". */
        fun parseShape(text: String, line: String, departureMs: Long): List<Pair<Double, Double>>? = parseShape(JSONObject(text), line, departureMs)
        fun parseShape(response: JSONObject, line: String, departureMs: Long): List<Pair<Double, Double>>? {
            val journeys = response.optJSONArray("journeys") ?: return null
            for (i in 0 until journeys.length()) {
                val legs = journeys.getJSONObject(i).optJSONArray("legs") ?: continue
                val transit = (0 until legs.length()).map { legs.getJSONObject(it) }.filter { it.optString("mode") == "transit" }
                val leg = transit.singleOrNull() ?: continue
                if (!leg.optString("line").equals(line, ignoreCase = true) || kotlin.math.abs(leg.optLong("departureMs") - departureMs) > 120_000L) continue
                val shape = leg.optJSONArray("shape") ?: continue
                val points = (0 until minOf(shape.length(), 5_000)).mapNotNull { j ->
                    shape.optJSONArray(j)?.let { p -> (p.optDouble(0) to p.optDouble(1)).takeIf { it.first in -90.0..90.0 && it.second in -180.0..180.0 } }
                }
                if (points.size >= 2) return points
            }
            return null
        }

        /** API v2: {"stops": [{id, name, lat, lon, passed, etaMinutes, plannedTime}]}. */
        fun parseEtas(text: String): List<VehicleStop> = parseEtas(JSONObject(text))
        fun parseEtas(response: JSONObject): List<VehicleStop> {
            val array = response.getJSONArray("stops")
            require(array.length() <= 200)
            return (0 until array.length()).mapNotNull { i -> runCatching {
                val o = array.getJSONObject(i)
                VehicleStop(o.getString("id"), o.optString("baseName").ifBlank { o.getString("name") }, o.getDouble("lat"), o.getDouble("lon"),
                    o.optBoolean("passed"), o.optDouble("etaMinutes").takeIf { it.isFinite() && it >= 0 }?.let { kotlin.math.ceil(it).toInt() },
                    runCatching { java.time.LocalTime.parse(o.getString("plannedTime")) }.getOrNull())
            }.getOrNull() }
        }

        /** API v2: {"departures": [...]}; brak delayMinutes to opóźnienie nieznane, a nie "na czas". */
        fun parse(text: String): List<LiveDeparture> = parse(JSONObject(text))
        fun parse(response: JSONObject): List<LiveDeparture> {
            val array = response.getJSONArray("departures")
            require(array.length() <= 500)
            return (0 until array.length()).mapNotNull { i -> runCatching {
                val o = array.getJSONObject(i)
                // Kurs kończy się na tym przystanku - nie da się nim odjechać.
                if (o.optBoolean("arrivalOnly")) return@runCatching null
                val delay = if (o.has("delayMinutes") && !o.isNull("delayMinutes")) o.getInt("delayMinutes").also { require(it in -60..240) } else null
                LiveDeparture(o.getString("line").also { require(it.isNotBlank() && it.length <= 32) }, o.getLong("atMs"), delay,
                    live = o.optString("source") == "live",
                    vehicleId = o.optString("vehicleId").takeIf { it.matches(VEHICLE_ID) })
            }.getOrNull() }
        }
    }
}
