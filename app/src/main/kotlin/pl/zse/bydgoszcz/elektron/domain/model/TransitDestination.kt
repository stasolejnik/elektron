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
    val navigationOrder: List<String> = NavigationOrder.DEFAULT,
    /** Przy wybranym przystanku przy szkole także kursy z innych przystanków do 1 km od szkoły (domyślnie nie). */
    val nearbyStops: Boolean = false,
    /**
     * Do szkoły: lista zaczyna się od kursu, który jest na miejscu najbliżej tylu minut przed lekcją
     * (wcześniejsze po "Pokaż wcześniejsze"); null (domyślnie) - od najbliższego kursu.
     */
    val arrivalMarginMinutes: Int? = null
) {
    val visible: Boolean get() = enabled
    /** Wybrany przystanek przy szkole jako filtr kursów; null - "Szkoła" albo włączone pobliskie przystanki. */
    val strictOrigin: TransitDestination? get() = preferredOrigin?.takeUnless { nearbyStops }
    /** Klucz wyboru przy szkole w wynikach - z pobliskimi przystankami inny niż sam przystanek. */
    val originKey: String? get() = preferredOrigin?.let { TransitStops.originKey(it, nearbyStops) }
    val canLoadJourneys: Boolean get() = destination != null && (enabled || showOnDashboard)

    companion object {
        /** Do wyboru w Ustawieniach → Przejazdy ("Kursy na czas przed lekcją"). */
        val ARRIVAL_MARGINS = listOf(5, 10, 15, 20, 30)
        const val DEFAULT_ARRIVAL_MARGIN = 10
    }
}

internal fun rememberTransitStop(previous: List<TransitDestination>, selected: TransitDestination): List<TransitDestination> =
    (listOf(selected) + previous.filterNot { it.key == selected.key }).take(5)

internal object TransitStops {
    /** Klucz przystanku przy szkole w wyniku: z pobliskimi przystankami ("+okolica") inny niż sam przystanek. */
    fun originKey(origin: TransitDestination, nearby: Boolean): String = if (nearby) "${origin.key}+okolica" else origin.key

    private val POLE_NUMBER = Regex("\\s+#\\d+$")

    /** Nazwa bez numeru słupka BUSearch ("Rycerska #2" -> "Rycerska"). */
    fun baseName(name: String): String = name.trim().replace(POLE_NUMBER, "")

    /**
     * Klucz nazwy przystanku bez polskich znaków i wielkości liter: katalog BUSearch ma część stanowisk
     * zapisanych bez znaków ("Rondo Fordonskie" obok "Rondo Fordońskie", "Przyłeki - Cicha") - to ten sam przystanek.
     */
    fun nameKey(name: String): String = java.text.Normalizer.normalize(baseName(name).replace('ł', 'l').replace('Ł', 'L'),
        java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)

    /** Z wariantów jednej nazwy ten z polskimi znakami ("Rondo Fordońskie", nie "Rondo Fordonskie"). */
    fun preferredName(names: Collection<String>): String =
        names.groupingBy { it }.eachCount().entries.maxWith(compareBy<Map.Entry<String, Int>> { e -> e.key.count { it.code > 127 } }
            .thenBy { it.value }.thenByDescending { it.key }).key

    /**
     * Zapisany cel albo przystanek początkowy, którego numerów stanowisk nie ma już w odświeżonym
     * katalogu (zmiana numeracji, nowe stanowisko, rozdzielenie przystanków o tej samej nazwie):
     * ten sam przystanek z katalogu - po nazwie, najwięcej wspólnych stanowisk, potem najbliżej.
     * Dawniej taki wybór dawał na stałe "Nie znaleziono połączenia" bez wskazówki. Zapisane jedno
     * stanowisko z numerem słupka ("Rycerska #2" - tak katalog podawał nazwy pod koniec 2026 r.) wraca
     * do całego przystanku: dawniej zostawały tylko jego linie i część kursów nie była widoczna.
     * null - zapis aktualny albo przystanku o tej nazwie już nie ma.
     */
    fun refreshed(saved: TransitDestination?, catalog: List<TransitDestination>, maxFromSchoolMeters: Double? = null): TransitDestination? {
        saved ?: return null
        if (catalog.isEmpty() || catalog.any { it.key == saved.key }) return null
        // Przystanek początkowy: tylko taki, jaki oferuje wybór (do 1 km od szkoły).
        val sameName = catalog.filter { nameKey(it.name) == nameKey(saved.name) &&
            (maxFromSchoolMeters == null || SchoolTransit.distance(it.latitude, it.longitude) <= maxFromSchoolMeters) }
        if (sameName.isEmpty()) return null
        return sameName.maxWith(compareBy<TransitDestination> { stop -> stop.stopIds.count { it in saved.stopIds } }
            .thenByDescending { SchoolTransit.distanceBetween(saved.latitude, saved.longitude, it.latitude, it.longitude) })
    }
}
