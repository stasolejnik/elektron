package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Kolor akcentu aplikacji i widżetów (Ustawienia -> Wygląd). BLUE = kolor z logo eLektronu.
 * Celowo bez pomarańczowego i czerwonego - te kolory oznaczają zastępstwa i błędy.
 */
enum class AccentColor(val key: String) {
    BLUE("blue"),
    INDIGO("indigo"),
    PURPLE("purple"),
    GREEN("green"),
    PINK("pink"),
    GRAPHITE("graphite");

    companion object {
        fun fromKey(key: String?): AccentColor = entries.firstOrNull { it.key == key } ?: BLUE
    }
}
