package pl.zse.bydgoszcz.elektron.data.repository

import org.json.JSONArray
import org.json.JSONObject
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourney
import pl.zse.bydgoszcz.elektron.domain.model.TransitRide

/**
 * Zapis ostatniego wyniku połączeń w pamięci podręcznej (cacheDir, poza kopią zapasową).
 * Odczyt jest niezaufany: błędny lub obcy plik = brak zapisu, nigdy wyjątek dla UI.
 */
internal object TransitResultCodec {
    private const val VERSION = 1
    private const val MAX_JOURNEYS = 300

    fun encode(result: TransitJourneyRepository.Result): String = JSONObject()
        .put("v", VERSION)
        .put("destination", result.destinationKey)
        .put("origin", result.originKey ?: JSONObject.NULL)
        .put("transfers", result.allowTransfers)
        .put("fetchedAt", result.fetchedAt)
        .put("next", result.nextWhenMs ?: JSONObject.NULL)
        .put("partial", result.partialFailure)
        .put("journeys", JSONArray().apply { result.journeys.take(MAX_JOURNEYS).forEach { put(journey(it)) } })
        .put("boarding", JSONArray().apply { result.boardingStops.forEach { put(stop(it)) } })
        .put("cursors", result.platformCursors?.let { cursors -> JSONObject().apply { cursors.forEach { (k, v) -> put(k, v) } } } ?: JSONObject.NULL)
        .toString()

    fun decode(text: String): TransitJourneyRepository.Result? = runCatching {
        val o = JSONObject(text)
        require(o.getInt("v") == VERSION)
        val journeys = o.getJSONArray("journeys")
        require(journeys.length() <= MAX_JOURNEYS)
        val boarding = o.getJSONArray("boarding")
        TransitJourneyRepository.Result(
            destinationKey = o.getString("destination"),
            journeys = (0 until journeys.length()).map { journey(journeys.getJSONObject(it)) },
            fetchedAt = o.getLong("fetchedAt"),
            originKey = if (o.isNull("origin")) null else o.getString("origin"),
            nextWhenMs = if (o.isNull("next")) null else o.getLong("next"),
            allowTransfers = o.getBoolean("transfers"),
            boardingStops = (0 until boarding.length()).map { stop(boarding.getJSONObject(it)) },
            platformCursors = if (o.isNull("cursors")) null else o.getJSONObject("cursors").let { c ->
                c.keys().asSequence().associateWith { c.getLong(it) }
            },
            partialFailure = o.getBoolean("partial")
        )
    }.getOrNull()

    private fun journey(j: TransitJourney) = JSONObject()
        .put("walk", j.walkMinutes).put("distance", j.distanceMeters).put("walkAvailable", j.walkAvailable)
        .put("rides", JSONArray().apply { j.rides.forEach { r ->
            put(JSONObject().put("line", r.line).put("direction", r.direction).put("fromId", r.fromId).put("fromName", r.fromName)
                .put("toId", r.toId).put("toName", r.toName).put("departure", r.departureMs).put("arrival", r.arrivalMs))
        } })

    private fun journey(o: JSONObject): TransitJourney {
        val rides = o.getJSONArray("rides")
        require(rides.length() in 1..2)
        return TransitJourney(
            rides = (0 until rides.length()).map { i -> rides.getJSONObject(i).let { r ->
                TransitRide(r.getString("line"), r.getString("direction"), r.getString("fromId"), r.getString("fromName"),
                    r.getString("toId"), r.getString("toName"), r.getLong("departure"), r.getLong("arrival"))
            } },
            walkMinutes = o.getInt("walk").also { require(it in 0..600) },
            distanceMeters = o.getDouble("distance").also { require(it.isFinite() && it in 0.0..10_000.0) },
            walkAvailable = o.getBoolean("walkAvailable"),
            // Dojście bez trasy ulicami jest dociągane po odczycie, jak przy świeżym wyniku.
            walkPending = !o.getBoolean("walkAvailable")
        )
    }

    private fun stop(s: TransitDestination) = JSONObject().put("name", s.name).put("ids", JSONArray(s.stopIds))
        .put("lat", s.latitude).put("lon", s.longitude)

    private fun stop(o: JSONObject): TransitDestination {
        val ids = o.getJSONArray("ids")
        return TransitDestination(o.getString("name"), (0 until ids.length()).map { ids.getString(it) }, o.getDouble("lat"), o.getDouble("lon"))
    }
}
