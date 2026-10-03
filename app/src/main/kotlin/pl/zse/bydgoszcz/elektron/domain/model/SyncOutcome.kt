package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Komunikat po synchronizacji, gdy część źródeł zawiodła. Liczą się plan i zastępstwa
 * (dane, które muszą być aktualne); ogłoszenia i lista klas - nie. Dawniej udany sync
 * czegokolwiek kasował komunikat, więc niedziałająca strona zastępstw była niewidoczna.
 * null - wszystko ważne się pobrało.
 */
object SyncOutcome {
    const val TIMETABLE = "timetable"
    const val SUBSTITUTIONS = "subs"
    const val ANNOUNCEMENTS = "anns"
    const val SIDEBAR = "sidebar"

    /**
     * Czy przebieg odświeżył dane, które muszą być aktualne (plan albo zastępstwa) - tylko wtedy
     * zapisujemy "zsynchronizowano teraz". Dawniej wystarczyła lista klas albo RSS, więc przy
     * niedziałającej stronie planu i zastępstw znikał baner o nieaktualnych danych.
     */
    fun freshDataLoaded(succeeded: Set<String>): Boolean =
        TIMETABLE in succeeded || SUBSTITUTIONS in succeeded

    fun errorMessage(failures: Map<String, Throwable>): String? {
        val important = failures[SUBSTITUTIONS] ?: failures[TIMETABLE] ?: return null
        val what = when {
            failures.containsKey(SUBSTITUTIONS) && failures.containsKey(TIMETABLE) -> "planu i zastępstw"
            failures.containsKey(SUBSTITUTIONS) -> "zastępstw"
            else -> "planu lekcji"
        }
        return if (important is SchoolPageChangedException) SchoolPageChangedException.USER_MESSAGE
            else "Nie udało się odświeżyć $what. ${SyncErrors.userMessage(important)}"
    }
}
