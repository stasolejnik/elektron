package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Kolor akcentu aplikacji i widżetów (Ustawienia -> Wygląd). Najpopularniejsze kolory
 * akcentu w kolejności koła barw, od domyślnego niebieskiego (logo eLektronu):
 * niebieski -> fioletowy -> różowy -> czerwony -> zielony (-> z powrotem niebieski),
 * na końcu własny. Bez pomarańczowego - oznacza zastępstwa. Kolejność = kolejność kółek.
 */
enum class AccentColor(val key: String) {
    BLUE("blue"),
    PURPLE("purple"),
    PINK("pink"),
    RED("red"),
    GREEN("green"),
    /** Własny kolor z próbnika - odcienie liczone przez [AccentMath]. */
    CUSTOM("custom");

    companion object {
        fun fromKey(key: String?): AccentColor = entries.firstOrNull { it.key == key } ?: BLUE
    }
}

/** Wybór akcentu: paleta + własny kolor (pamiętany także, gdy wybrana jest inna paleta). */
data class AccentSetting(
    val color: AccentColor = AccentColor.BLUE,
    val custom: Long = DEFAULT_CUSTOM,
    /**
     * Kolor zastępstw (Ustawienia -> Wygląd). Razem z akcentem, bo trafia w te same miejsca: motyw aplikacji
     * i paletę widżetów. W przeciwieństwie do akcentu działa także przy kolorach z tapety.
     */
    val substitution: SubstitutionColor = SubstitutionColor.DEFAULT,
    /** Własny kolor zastępstw z próbnika (pamiętany także, gdy wybrany jest inny). */
    val substitutionCustom: Long = SubstitutionColor.DEFAULT_CUSTOM
) {
    companion object {
        /** Startowy kolor próbnika: turkus. */
        const val DEFAULT_CUSTOM = 0xFF1EA896
    }
}
