package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalTime

/** Ekran otwierany przy uruchomieniu aplikacji (Ustawienia -> Uruchamianie). */
enum class StartScreen(val key: String) {
    /** W godzinach lekcji plan, poza nimi strona główna (dawny "inteligentny start"). */
    SMART("smart"),
    DASHBOARD("dashboard"),
    TIMETABLE("timetable"),
    SUBSTITUTIONS("substitutions");

    /** Ekran do otwarcia teraz; SMART rozstrzyga według dzisiejszych lekcji (po filtrze grup). */
    fun resolve(todayLessons: List<Lesson>, now: LocalTime): StartScreen {
        if (this != SMART) return this
        if (todayLessons.isEmpty()) return DASHBOARD
        val from = todayLessons.minOf { it.timeFrom }.minusMinutes(10)
        val to = todayLessons.maxOf { it.timeTo }
        return if (now in from..to) TIMETABLE else DASHBOARD
    }

    companion object {
        fun fromKey(key: String?): StartScreen? = entries.firstOrNull { it.key == key }
    }
}
