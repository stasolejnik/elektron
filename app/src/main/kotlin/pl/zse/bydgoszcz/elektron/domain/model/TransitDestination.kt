package pl.zse.bydgoszcz.elektron.domain.model

/** A destination is a stop complex, including platforms for both directions. */
data class TransitDestination(val name: String, val stopIds: List<String>, val latitude: Double, val longitude: Double) {
    init {
        require(name.isNotBlank() && name.length <= 200)
        require(stopIds.isNotEmpty() && stopIds.size <= 100 && stopIds.distinct().size == stopIds.size)
        require(stopIds.all { it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) })
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
    }
    val key: String = stopIds.sorted().joinToString(",")
}

data class TransitPreferences(
    val destination: TransitDestination? = null,
    val enabled: Boolean = false,
    val preferredOrigin: TransitDestination? = null,
    val allowTransfers: Boolean = false,
    val recentDestinations: List<TransitDestination> = emptyList(),
    val recentOrigins: List<TransitDestination> = emptyList(),
    val hiddenTabs: Set<String> = emptySet(),
    val showOnDashboard: Boolean = false,
    val navigationOrder: List<String> = NavigationOrder.DEFAULT
) {
    val visible: Boolean get() = enabled
    val canLoadJourneys: Boolean get() = destination != null && (enabled || showOnDashboard)
}

internal fun rememberTransitStop(previous: List<TransitDestination>, selected: TransitDestination): List<TransitDestination> =
    (listOf(selected) + previous.filterNot { it.key == selected.key }).take(5)

internal object TransitStops {
    /**
     * Zapisany cel albo przystanek początkowy, którego numerów stanowisk nie ma już w odświeżonym
     * katalogu (zmiana numeracji, nowe stanowisko, rozdzielenie przystanków o tej samej nazwie):
     * ten sam przystanek z katalogu - po nazwie, najwięcej wspólnych stanowisk, potem najbliżej.
     * Dawniej taki wybór dawał na stałe "Nie znaleziono połączenia" bez wskazówki.
     * null - zapis aktualny albo przystanku o tej nazwie już nie ma.
     */
    fun refreshed(saved: TransitDestination?, catalog: List<TransitDestination>, maxFromSchoolMeters: Double? = null): TransitDestination? {
        saved ?: return null
        if (catalog.isEmpty() || catalog.any { it.key == saved.key }) return null
        // Przystanek początkowy: tylko taki, jaki oferuje wybór (do 1 km od szkoły).
        val sameName = catalog.filter { it.name.equals(saved.name, ignoreCase = true) &&
            (maxFromSchoolMeters == null || SchoolTransit.distance(it.latitude, it.longitude) <= maxFromSchoolMeters) }
        if (sameName.isEmpty()) return null
        return sameName.maxWith(compareBy<TransitDestination> { stop -> stop.stopIds.count { it in saved.stopIds } }
            .thenByDescending { SchoolTransit.distanceBetween(saved.latitude, saved.longitude, it.latitude, it.longitude) })
    }
}
