package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Kolor zastępstw (Ustawienia -> Wygląd): numer lekcji, nagłówki, uwagi i tła zastępstw w aplikacji i widżetach.
 * [DEFAULT] - jak dotąd: pomarańczowy, a przy kolorach z tapety kolor z tapety. Pozostałe - wyraźnie różne barwy
 * i własny kolor z próbnika ([CUSTOM], odcienie jak przy własnym akcencie). Bez czerwieni - ta jest tylko dla
 * błędów i nieodwracalnych działań.
 */
enum class SubstitutionColor(val key: String, val label: String) {
    DEFAULT("default", "Domyślny"),
    PINK("pink", "Różowy"),
    PURPLE("purple", "Fioletowy"),
    BLUE("blue", "Niebieski"),
    TEAL("teal", "Turkusowy"),
    GREEN("green", "Zielony"),
    CUSTOM("custom", "Własny");

    companion object {
        fun fromKey(key: String?): SubstitutionColor = entries.firstOrNull { it.key == key } ?: DEFAULT
        /** Startowy kolor próbnika: żółty (inny niż domyślny pomarańczowy i gotowe kolory). */
        const val DEFAULT_CUSTOM = 0xFFE0B000
    }
}

/** Odcienie koloru zastępstw w jednym motywie (ARGB): kolor, tekst na nim, tło wyróżnienia i tekst na tle. */
data class SubstitutionTones(val color: Long, val onColor: Long, val container: Long, val onContainer: Long)

object SubstitutionColors {
    /** Domyślny pomarańczowy z palety aplikacji. */
    val ORANGE_LIGHT = SubstitutionTones(0xFFC96F00, 0xFFFFFFFF, 0xFFFFF0DB, 0xFF4F2C00)
    val ORANGE_DARK = SubstitutionTones(0xFFFF9F0A, 0xFF3A2000, 0xFF3A2A12, 0xFFFFDDB0)

    /**
     * Tryb jasny: kolor czytelny na białym (kontrast tekstu >= 4.5:1), delikatne tło wyróżnienia. null dla
     * [SubstitutionColor.DEFAULT] - zostaje kolor motywu (albo z tapety). [custom] - kolor z próbnika.
     */
    fun light(color: SubstitutionColor, custom: Long = SubstitutionColor.DEFAULT_CUSTOM): SubstitutionTones? = when (color) {
        SubstitutionColor.DEFAULT -> null
        SubstitutionColor.PINK -> SubstitutionTones(0xFFC0185A, 0xFFFFFFFF, 0xFFFCE1EB, 0xFF4A0722)
        SubstitutionColor.PURPLE -> SubstitutionTones(0xFF8036C0, 0xFFFFFFFF, 0xFFF1E4FD, 0xFF30104F)
        SubstitutionColor.BLUE -> SubstitutionTones(0xFF1A5FC0, 0xFFFFFFFF, 0xFFDDE8FA, 0xFF0A2A55)
        SubstitutionColor.TEAL -> SubstitutionTones(0xFF00796B, 0xFFFFFFFF, 0xFFD5F2EE, 0xFF00332D)
        SubstitutionColor.GREEN -> SubstitutionTones(0xFF237A45, 0xFFFFFFFF, 0xFFD9F2E1, 0xFF0A3A1E)
        SubstitutionColor.CUSTOM -> AccentMath.light(custom).toTones()
    }

    /** Tryb ciemny: jasny kolor na czarnym tle, ciemne tło wyróżnienia. */
    fun dark(color: SubstitutionColor, custom: Long = SubstitutionColor.DEFAULT_CUSTOM): SubstitutionTones? = when (color) {
        SubstitutionColor.DEFAULT -> null
        SubstitutionColor.PINK -> SubstitutionTones(0xFFFF8AB5, 0xFF4A0722, 0xFF4A1A2E, 0xFFFFD9E6)
        SubstitutionColor.PURPLE -> SubstitutionTones(0xFFC99BFF, 0xFF30104F, 0xFF3A2458, 0xFFF0E2FF)
        SubstitutionColor.BLUE -> SubstitutionTones(0xFF82B4FF, 0xFF0A2A55, 0xFF1A2C47, 0xFFD8E6FF)
        SubstitutionColor.TEAL -> SubstitutionTones(0xFF4FD8C4, 0xFF00332D, 0xFF123A35, 0xFFC8F5EE)
        SubstitutionColor.GREEN -> SubstitutionTones(0xFF5FD68A, 0xFF00391A, 0xFF163B25, 0xFFC8F5D6)
        SubstitutionColor.CUSTOM -> AccentMath.dark(custom).toTones()
    }

    private fun AccentMath.Tones.toTones() = SubstitutionTones(primary, onPrimary, container, onContainer)
}
