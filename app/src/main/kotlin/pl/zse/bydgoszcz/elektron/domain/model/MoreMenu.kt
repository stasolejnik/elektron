package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Przycisk "Więcej" na dolnym pasku (Ustawienia → Pasek nawigacji): rozwijany panel jak folder na ekranie
 * głównym Androida. Zapis to kolejność pasku ze znacznikiem [ROUTE] (miejsce przycisku) i osobno
 * uporządkowana zawartość panelu. Zakładka w panelu ma w kolejności pasku swoje dawne miejsce, ale się
 * tam nie liczy - pasek ją pomija, a przesuwanie stron pokazuje zawartość panelu w miejscu "Więcej".
 */
object MoreMenu {
    const val ROUTE = "more"
    /** Zawartość po włączeniu: od prawej strony pasku, gdzie robiło się najciaśniej. */
    val DEFAULT_CONTENT = listOf("transit", "announcements", "settings")

    /** Kolejność z [ROUTE] tylko przy włączonym "Więcej" (domyślnie na końcu) i bez powtórzeń. */
    fun slots(saved: List<String>, enabled: Boolean): List<String> {
        val routes = NavigationOrder.normalize(saved)
        if (!enabled) return routes
        val at = saved.filter { it in NavigationOrder.DEFAULT || it == ROUTE }.distinct().indexOf(ROUTE)
        return routes.toMutableList().apply { add(if (at < 0) size else at.coerceAtMost(size), ROUTE) }
    }

    /** Zawartość panelu: znane zakładki, bez powtórzeń, w zapisanej kolejności. */
    fun content(saved: List<String>, enabled: Boolean): List<String> =
        if (!enabled) emptyList() else saved.filter { it in NavigationOrder.DEFAULT }.distinct()

    /** Przyciski na pasku (z "Więcej"), bez zakładek z panelu i niedostępnych ([available]). */
    fun bar(slots: List<String>, content: List<String>, available: (String) -> Boolean): List<String> =
        slots.filter { it == ROUTE || (it !in content && available(it)) }

    /** Strony przy przesuwaniu: kolejność pasku z zawartością panelu w miejscu "Więcej". */
    fun pages(slots: List<String>, content: List<String>, available: (String) -> Boolean): List<String> =
        slots.flatMap { route -> when {
            route == ROUTE -> content.filter(available)
            route in content || !available(route) -> emptyList()
            else -> listOf(route)
        } }

    /** Po włączeniu: "Więcej" na samym prawym końcu z [DEFAULT_CONTENT]. */
    fun enable(saved: List<String>): Pair<List<String>, List<String>> =
        (NavigationOrder.normalize(saved) + ROUTE) to DEFAULT_CONTENT

    /** Po wyłączeniu: zakładki z panelu wracają na pasek w miejscu "Więcej" (w kolejności panelu). */
    fun disable(slots: List<String>, content: List<String>): List<String> =
        NavigationOrder.normalize(pages(slots, content) { true })

    /**
     * Nowy układ po przeciągnięciu: [bar] - przyciski pasku po zmianie (z "Więcej"), [content] - panel.
     * Przyciski pasku dostają swoje miejsca w kolejności; ukryte zakładki i te w panelu zostają, gdzie były.
     */
    fun commit(slots: List<String>, bar: List<String>, content: List<String>): Pair<List<String>, List<String>> {
        val requested = bar.distinct().filter { it in slots }
        val iterator = requested.iterator()
        val order = slots.map { if (it in requested) iterator.next() else it }
        return order to content.distinct().filter { it in NavigationOrder.DEFAULT }
    }
}
