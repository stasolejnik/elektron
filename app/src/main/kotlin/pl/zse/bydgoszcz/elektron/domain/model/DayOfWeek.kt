package pl.zse.bydgoszcz.elektron.domain.model

/** Dzień tygodnia z polską nazwą i numerem ISO (1=Poniedziałek..7=Niedziela). */
enum class DayOfWeek(val displayName: String, val isoNumber: Int) {
    PONIEDZIALEK("Poniedziałek", 1),
    WTOREK("Wtorek", 2),
    SRODA("Środa", 3),
    CZWARTEK("Czwartek", 4),
    PIATEK("Piątek", 5),
    SOBOTA("Sobota", 6),
    NIEDZIELA("Niedziela", 7);

    companion object {
        fun fromIso(n: Int): DayOfWeek? = entries.firstOrNull { it.isoNumber == n }
    }
}
