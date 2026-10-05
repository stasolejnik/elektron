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
    val recentOrigins: List<TransitDestination> = emptyList()
) {
    val visible: Boolean get() = enabled && destination != null
}

internal fun rememberTransitStop(previous: List<TransitDestination>, selected: TransitDestination): List<TransitDestination> =
    (listOf(selected) + previous.filterNot { it.key == selected.key }).take(5)
