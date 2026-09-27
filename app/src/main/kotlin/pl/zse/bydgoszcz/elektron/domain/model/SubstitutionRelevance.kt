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
}
