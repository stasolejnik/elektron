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

    /**
     * Koniec każdej lekcji wg dzwonków (numer -> godzina). Z planu klasy - dzwonki są wspólne,
     * więc liczą się też lekcje innych grup i innych dni (zastępstwo za lekcję, której
     * użytkownik nie ma po filtrze grup, też ma koniec).
     */
    fun lessonEnds(lessons: List<Lesson>): Map<Int, java.time.LocalTime> =
        lessons.groupBy { it.number }.mapValues { (_, l) -> l.maxOf { it.timeTo } }

    /**
     * Zastępstwo już minęło (jego lekcja się skończyła) - znika z zakładki Zastępstwa i ze
     * strony głównej; w planie lekcji zostaje. Nieznany koniec lekcji: do końca dnia.
     */
    fun isOver(sub: Substitution, now: java.time.LocalDateTime, ends: Map<Int, java.time.LocalTime>): Boolean {
        val today = now.toLocalDate()
        return when {
            sub.date < today -> true
            sub.date > today -> false
            else -> ends[sub.lessonNumber]?.let { now.toLocalTime() >= it } ?: false
        }
    }
}
