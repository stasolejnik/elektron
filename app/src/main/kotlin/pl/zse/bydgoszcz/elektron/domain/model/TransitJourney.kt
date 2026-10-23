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

/**
 * Połączenie. Ze szkoły: [walkMinutes] to dojście ze szkoły do pierwszego przystanku.
 * Do szkoły: dojście z ostatniego przystanku do szkoły, a [schoolArrivalMs] - kiedy będziesz w szkole.
 */
data class TransitJourney(val rides: List<TransitRide>, val walkMinutes: Int, val distanceMeters: Double, val walkAvailable: Boolean = true, val walkPending: Boolean = false,
    val schoolArrivalMs: Long? = null) {
    val departureMs get() = rides.first().departureMs
    val arrivalMs get() = rides.last().arrivalMs
    val transfers get() = rides.size - 1
    // One departure can appear in several direct/transfer variants from the planner.
    val key = rides.first().let { "${it.line}:${it.fromId}:${it.departureMs}" }
    fun reachable(nowMs: Long) = departureMs >= nowMs + (walkMinutes + SchoolTransit.BUFFER_MINUTES) * 60_000L
    /** Ze szkoły: kiedy najpóźniej wyjść, żeby spokojnie zdążyć (dojście i zapas); [departure] - odjazd z opóźnieniem. */
    fun leaveSchoolMs(departure: Long = departureMs) = departure - (walkMinutes + SchoolTransit.BUFFER_MINUTES) * 60_000L
}

object TransitJourneys {
    // For already ranked repository results: filtering preserves arrival order without sorting again.
    // [live] - opóźnienia: spóźniony kurs zostaje na liście do faktycznego odjazdu.
    fun upcoming(ranked: List<TransitJourney>, nowMs: Long, preferredOrigin: TransitDestination? = null, allowTransfers: Boolean = true,
        live: Map<String, LiveStatus> = emptyMap()): List<TransitJourney> =
        ranked.filter { TransitLive.expected(it, live) > nowMs && (allowTransfers || it.transfers == 0) &&
            (preferredOrigin == null || it.rides.first().fromId in preferredOrigin.stopIds) }

    /**
     * Ze szkoły przy "Szkoła" (najbliższe przystanki, bez wybranego): tylko kursy, na które da się
     * dojść przed odjazdem (z opóźnieniem na żywo). Dawniej lista zaczynała się od "za 1 min" z dojściem
     * 17 min na dalszy przystanek. Przy wybranym przystanku kurs zostaje do odjazdu ("Pospiesz się").
     */
    fun walkable(journeys: List<TransitJourney>, nowMs: Long, live: Map<String, LiveStatus> = emptyMap()): List<TransitJourney> =
        journeys.filter { it.schoolArrivalMs != null || TransitLive.expected(it, live) - it.walkMinutes * 60_000L >= nowMs }

    /** Do szkoły: kolejne odjazdy z Twojego przystanku, przy tej samej godzinie najpierw szybszy. */
    fun rankToSchool(journeys: List<TransitJourney>, nowMs: Long, allowTransfers: Boolean = true): List<TransitJourney> =
        journeys.filter { it.departureMs > nowMs && (allowTransfers || it.transfers == 0) }
            .groupBy { it.key }.values.map { variants ->
                variants.minWith(compareBy<TransitJourney> { it.schoolArrivalMs ?: it.arrivalMs }.thenBy { it.transfers })
            }.sortedWith(compareBy<TransitJourney> { it.departureMs }.thenBy { it.schoolArrivalMs ?: it.arrivalMs }
                .thenBy { it.rides.first().line })

    /**
     * Do szkoły: ostatni kurs, którym zdążysz na lekcję (najpóźniejszy odjazd z dotarciem do szkoły
     * przed jej początkiem). null - żaden z listy nie zdąży albo brak lekcji.
     */
    fun lastOnTime(journeys: List<TransitJourney>, lessonStartMs: Long?, live: Map<String, LiveStatus> = emptyMap()): TransitJourney? {
        lessonStartMs ?: return null
        return journeys.filter { trip -> trip.schoolArrivalMs?.let { it + delayMs(trip, live) <= lessonStartMs } == true }
            .maxByOrNull { TransitLive.expected(it, live) }
    }

    /** Zapas, z jakim dojazd ma być w szkole przed lekcją - od tego kursu zaczyna się lista. */
    const val SCHOOL_MARGIN_MINUTES = 10L

    /**
     * Do szkoły: od którego kursu (indeks w liście po odjeździe) zacząć - ostatni, który dociera
     * co najmniej [SCHOOL_MARGIN_MINUTES] przed lekcją; gdy żaden - ostatni na czas; gdy i takiego
     * nie ma albo brak lekcji - pierwszy. Wcześniejsze kursy są nad nim, po "Pokaż wcześniejsze".
     */
    fun anchorToSchool(journeys: List<TransitJourney>, lessonStartMs: Long?, live: Map<String, LiveStatus> = emptyMap()): Int {
        lessonStartMs ?: return 0
        fun at(trip: TransitJourney) = (trip.schoolArrivalMs ?: trip.arrivalMs) + delayMs(trip, live)
        val margin = journeys.indexOfLast { at(it) <= lessonStartMs - SCHOOL_MARGIN_MINUTES * 60_000L }
        if (margin >= 0) return margin
        return journeys.indexOfLast { at(it) <= lessonStartMs }.coerceAtLeast(0)
    }

    /** Przesunięcie dotarcia o opóźnienie pierwszego kursu (przybliżenie - autobus nadrabia rzadko). */
    fun delayMs(trip: TransitJourney, live: Map<String, LiveStatus>): Long =
        live[trip.key]?.let { it.expectedMs - trip.departureMs } ?: 0L

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
