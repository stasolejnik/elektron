package pl.zse.bydgoszcz.elektron.data.repository

import okhttp3.OkHttpClient
import pl.zse.bydgoszcz.elektron.domain.model.SchoolTransit
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Dojście ze szkoły do przystanku - szacowane na telefonie z odległości w linii prostej (z zapasem na ulice),
 * bez zewnętrznego planera tras pieszych. Dawniej trasy liczył serwer FOSSGIS (routing.openstreetmap.de);
 * odkąd karta nie pokazuje "Wyjdź o…", dojście służy tylko do pominięcia kursów, na które z dalszych
 * przystanków przy "Szkoła" nie da się zdążyć - do tego wystarcza oszacowanie, a żadne dane nie wychodzą z telefonu.
 */
@Singleton
class TransitWalkingRepository @Inject constructor() {
    /** Zgodność z testami i dawnym konstruktorem (klient sieci nie jest już używany). */
    @Suppress("UNUSED_PARAMETER")
    internal constructor(client: OkHttpClient?, cacheFile: File? = null, elapsedMs: () -> Long = { 0L }) : this()

    data class Path(val minutes: Int, val distanceMeters: Double, val savedAt: Long)

    /** Oszacowanie dla przystanków (klucz - identyfikator stanowiska). */
    fun cachedFromSchool(stops: List<TransitDestination>): Map<String, Path> = buildMap {
        val now = System.currentTimeMillis()
        stops.forEach { stop ->
            val path = estimate(stop, now)
            stop.stopIds.forEach { put(it, path) }
        }
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun fromSchool(stops: List<TransitDestination>): Map<String, Path> = cachedFromSchool(stops)

    /** Bez sieci nie ma czego ponawiać. */
    fun retryDue(): Boolean = false

    companion object {
        /** Ulice nie biegną w linii prostej - typowo ok. 30% dłużej. */
        const val DETOUR = 1.3
        /** Spokojny marsz, ok. 4,5 km/h. */
        const val METERS_PER_MINUTE = 75.0

        internal fun estimate(stop: TransitDestination, now: Long = 0L): Path {
            val meters = SchoolTransit.distance(stop.latitude, stop.longitude) * DETOUR
            return Path(ceil(meters / METERS_PER_MINUTE).toInt().coerceAtLeast(1), meters, now)
        }
    }
}
