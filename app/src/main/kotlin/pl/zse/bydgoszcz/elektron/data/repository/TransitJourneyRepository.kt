package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import pl.zse.bydgoszcz.elektron.data.remote.http.readCancellable
import pl.zse.bydgoszcz.elektron.domain.model.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitJourneyRepository @Inject constructor(private val client: OkHttpClient, private val catalog: TransitStopCatalog, private val walking: TransitWalkingRepository) {
    internal constructor(client: OkHttpClient, catalog: TransitStopCatalog) : this(client, catalog, TransitWalkingRepository(client))
    data class Result(val destinationKey: String, val journeys: List<TransitJourney>, val fetchedAt: Long, val originKey: String? = null, val nextWhenMs: Long? = null, val allowTransfers: Boolean = false, val boardingStops: List<TransitDestination> = emptyList(), val platformCursors: Map<String, Long>? = null) {
        fun canReuse(key: String, nowMs: Long, origin: String? = null, transfers: Boolean = false): Boolean = destinationKey == key && originKey == origin && allowTransfers == transfers && nowMs - fetchedAt in 0 until 60_000L &&
            (journeys.isEmpty() || journeys.any { it.departureMs > nowMs })
    }
    private data class Batch(val platform: String, val journeys: List<TransitJourney>, val cursor: Long?)
    suspend fun find(destination: TransitDestination, origin: TransitDestination? = null, afterMs: Long? = null, allowTransfers: Boolean = false, platformCursors: Map<String, Long>? = null): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val platforms = catalog.load().platforms.associateBy { it.stopIds.single() }
        // A selected stop is a constraint, not just a coordinate near other stops.
        // Query each platform of the selected stop using the API's documented stopId.
        val startingPoints = origin?.stopIds?.map { JSONObject().put("stopId", it.toLongOrNull() ?: it) }
            ?: listOf(JSONObject().put("lat", SchoolTransit.LATITUDE).put("lon", SchoolTransit.LONGITUDE))
        val batches = kotlinx.coroutines.coroutineScope {
            val permits = kotlinx.coroutines.sync.Semaphore(2)
            // Each platform has its own search window. Finished platforms must not be
            // fetched again, and later windows must not restart at another platform's cursor.
            startingPoints.filter { afterMs == null || platformCursors == null || it.optString("stopId") in platformCursors }.map { from -> async {
                permits.acquire()
                try {
                    val platform = from.optString("stopId")
                    var whenMs = if (afterMs != null) platformCursors?.get(platform) ?: afterMs else now
                    var cursor: Long? = null
                    val trips = mutableListOf<TransitJourney>()
                    repeat(2) {
                        val body = JSONObject().put("from", from)
                            .put("to", JSONObject().put("lat", destination.latitude).put("lon", destination.longitude))
                            .put("maxPrzesiadki", if (allowTransfers) 1 else 0).put("ksztalt", false).put("whenMs", whenMs)
                        val request = Request.Builder().url("https://api.busearch.pl/bydgoszcz/api/polaczenia")
                            .header("Accept", "application/json").post(body.toString().toRequestBody("application/json".toMediaType())).build()
                        val response = client.newCall(request).readCancellable {
                            if (!it.isSuccessful) throw IOException("Połączenia: HTTP ${it.code}")
                            val source = it.body?.source() ?: throw IOException("Pusta odpowiedź")
                            source.request(4_000_001)
                            if (source.buffer.size > 4_000_000) throw IOException("Zbyt duża odpowiedź")
                            JSONObject(source.readUtf8())
                        }
                        trips += TransitJourneyParser.parse(response, destination, platforms, now, allowTransfers)
                            .filter { origin == null || it.rides.first().fromId in origin.stopIds }
                        cursor = response.optLong("nastepneWhenMs", 0).takeIf { it > whenMs && it <= now + 24 * 60 * 60_000L }
                        if (trips.isNotEmpty() || cursor == null) return@async Batch(platform, trips.toList(), cursor)
                        whenMs = cursor!!
                    }
                    Batch(platform, trips.toList(), cursor)
                } finally { permits.release() }
            } }.map { it.await() }
        }
        val journeys = batches.flatMap { it.journeys }
        val nextCursors = batches.mapNotNull { batch -> batch.cursor?.let { batch.platform to it } }.toMap()
        val nextWhenMs = nextCursors.values.minOrNull()
        val boardingStops = journeys.mapNotNull { platforms[it.rides.first().fromId] }.distinctBy { it.key }
        val paths = walking.cachedFromSchool(boardingStops)
        val withPaths = journeys.map { trip ->
            val path = paths[trip.rides.first().fromId]
            if (path != null) trip.copy(walkMinutes = path.minutes, distanceMeters = path.distanceMeters, walkAvailable = true)
            else trip.copy(walkAvailable = false, walkPending = true)
        }
        Result(destination.key, TransitJourneys.rank(withPaths, System.currentTimeMillis(), origin, allowTransfers), System.currentTimeMillis(), origin?.key, nextWhenMs, allowTransfers, boardingStops, nextCursors)
    }
    /** Walking requests do not block displaying or paging departures. */
    suspend fun enrichWalking(result: Result): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val pendingStops = result.journeys.filter { it.walkPending && it.departureMs > now }.map { it.rides.first().fromId }.toSet()
        val paths = walking.fromSchool(result.boardingStops.filter { stop -> stop.stopIds.any { it in pendingStops } })
        result.copy(journeys = result.journeys.map { trip ->
            if (!trip.walkPending) return@map trip
            val path = paths[trip.rides.first().fromId]
            if (path != null) trip.copy(walkMinutes = path.minutes, distanceMeters = path.distanceMeters, walkAvailable = true, walkPending = false)
            else trip.copy(walkAvailable = false, walkPending = false)
        })
    }

}

internal object TransitJourneyParser {
    fun parse(response: JSONObject, destination: TransitDestination, platforms: Map<String, TransitDestination>, nowMs: Long, allowTransfers: Boolean = true): List<TransitJourney> {
        require(response.getBoolean("ok")) { "Planer nie zwrócił poprawnych danych" }
        val options = response.getJSONArray("polaczenia")
        require(options.length() <= 200)
        return (0 until options.length()).mapNotNull { index -> runCatching {
            val segments = options.getJSONObject(index).getJSONArray("odcinki")
            val rides = mutableListOf<TransitRide>()
            var reached = false
            for (i in 0 until segments.length()) {
                val leg = segments.getJSONObject(i)
                if (leg.optString("rodzaj") != "przejazd") continue
                val from = leg.getJSONObject("zPrzystanku")
                val to = leg.getJSONObject("doPrzystanku")
                val fromId = from.getString("id")
                val departure = leg.getLong("odjazdMs")
                var toId = to.getString("id")
                var arrival = leg.getLong("przyjazdMs")
                var toName = to.getString("nazwa")
                if (fromId in destination.stopIds) break // already at the chosen stop
                val calls = leg.optJSONArray("poDrodze")
                if (calls != null) {
                    val fromIndex = (0 until calls.length()).firstOrNull { calls.getJSONObject(it).optString("id") == fromId }
                    val targetIndex = fromIndex?.let { start -> (start + 1 until calls.length()).firstOrNull {
                        calls.getJSONObject(it).optString("id") in destination.stopIds
                    } }
                    if (targetIndex != null) {
                        val target = calls.getJSONObject(targetIndex)
                        toId = target.getString("id"); arrival = target.getLong("at"); toName = destination.name; reached = true
                    }
                }
                if (toId in destination.stopIds) reached = true
                require(departure >= nowMs && arrival >= departure && arrival <= nowMs + 24 * 60 * 60_000L)
                val line = leg.getString("linia"); val direction = leg.getString("cel")
                require(line.isNotBlank() && line.length <= 32 && direction.isNotBlank() && direction.length <= 200)
                if (rides.isNotEmpty()) {
                    val previous = rides.last()
                    val walkMs = if (previous.toId == fromId) 0L else {
                        val exit = platforms[previous.toId] ?: return@runCatching null
                        val boarding = platforms[fromId] ?: return@runCatching null
                        val distance = SchoolTransit.distanceBetween(exit.latitude, exit.longitude, boarding.latitude, boarding.longitude)
                        require(distance <= 500) // reject distant, disconnected transfers
                        SchoolTransit.walkMinutes(distance) * 60_000L
                    }
                    require(departure >= previous.arrivalMs + walkMs + 60_000L)
                }
                rides += TransitRide(line, direction, fromId, from.getString("nazwa"), toId, toName, departure, arrival)
                if (reached) break
            }
            require(reached && rides.size in 1..(if (allowTransfers) 2 else 1))
            val start = platforms[rides.first().fromId] ?: return@runCatching null
            val distance = SchoolTransit.distance(start.latitude, start.longitude)
            require(distance <= 1000)
            TransitJourney(rides, SchoolTransit.walkMinutes(distance), distance)
        }.getOrNull() }
    }
}
