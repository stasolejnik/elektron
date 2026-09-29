package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Jedno miejsce z logiką "czy to zastępstwo dotyczy tej klasy" — wcześniej ta sama
 * logika (i te same bugi: brak ignoreCase) była skopiowana w SyncWorker,
 * SubstitutionsViewModel, DashboardViewModel i odbiorniku FCM. Teraz wszystkie
 * cztery miejsca wołają to samo.
 */
object SubstitutionRelevance {
    fun matchesClass(sub: Substitution, classShortName: String): Boolean =
        sub.classShortName.equals(classShortName, ignoreCase = true)

    /**
     * Czy dzisiejsze lekcje klasy już się skończyły (po filtrze grup). Liczony jest koniec
     * ostatniej lekcji W PLANIE - także zwolnionej, żeby informacja "Uczniowie zwolnieni do
     * domu" była widoczna w zakładce Zastępstwa przez cały dzień lekcji. Bez planu na dziś:
     * false (nic nie chowamy na ślepo).
     */
    fun todayFinished(todayLessons: List<Lesson>, now: java.time.LocalTime): Boolean {
        val end = todayLessons.maxOfOrNull { it.timeTo } ?: return false
        return now >= end
    }
}
