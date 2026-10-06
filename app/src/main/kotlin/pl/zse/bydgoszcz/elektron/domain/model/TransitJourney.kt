package pl.zse.bydgoszcz.elektron.domain.model

import kotlin.math.*

/** Fixed school location from the map linked by the school's contact page. */
object SchoolTransit {
    const val LATITUDE = 53.1233851
    const val LONGITUDE = 18.0266431
    const val BUFFER_MINUTES = 2
    fun distance(latitude: Double, longitude: Double): Double {
        return distanceBetween(LATITUDE, LONGITUDE, latitude, longitude)
    }
    fun distanceBetween(fromLatitude: Double, fromLongitude: Double, latitude: Double, longitude: Double): Double {
        val a = Math.toRadians(latitude - fromLatitude)
        val b = Math.toRadians(longitude - fromLongitude)
        val h = sin(a / 2).pow(2) + cos(Math.toRadians(fromLatitude)) * cos(Math.toRadians(latitude)) * sin(b / 2).pow(2)
        return 6371000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    /** Conservative estimate, not a GPS or pedestrian navigation measurement. */
    fun walkMinutes(distanceMeters: Double): Int = ceil(distanceMeters * 1.4 / 75.0).toInt().coerceAtLeast(1)
}

data class TransitRide(val line: String, val direction: String, val fromId: String, val fromName: String,
    val toId: String, val toName: String, val departureMs: Long, val arrivalMs: Long)

data class TransitJourney(val rides: List<TransitRide>, val walkMinutes: Int, val distanceMeters: Double, val walkAvailable: Boolean = true, val walkPending: Boolean = false) {
    val departureMs get() = rides.first().departureMs
    val arrivalMs get() = rides.last().arrivalMs
    val transfers get() = rides.size - 1
    // One departure can appear in several direct/transfer variants from the planner.
    val key = rides.first().let { "${it.line}:${it.fromId}:${it.departureMs}" }
    fun reachable(nowMs: Long) = departureMs >= nowMs + (walkMinutes + SchoolTransit.BUFFER_MINUTES) * 60_000L
}

object TransitJourneys {
    /** Keep a departure visible until it leaves, even after the walking deadline passes. */
    fun rank(journeys: List<TransitJourney>, nowMs: Long, preferredOrigin: TransitDestination? = null, allowTransfers: Boolean = true): List<TransitJourney> {
        return journeys.filter { it.departureMs > nowMs && (allowTransfers || it.transfers == 0) &&
            (preferredOrigin == null || it.rides.first().fromId in preferredOrigin.stopIds)
        }.groupBy { it.key }.values.map { variants ->
            variants.minWith(compareBy<TransitJourney> { it.arrivalMs }.thenBy { it.transfers })
        }.sortedWith(compareBy<TransitJourney> { it.arrivalMs }.thenBy { it.departureMs }
            .thenBy { it.rides.first().line }.thenBy { it.rides.first().fromId })
    }
}
