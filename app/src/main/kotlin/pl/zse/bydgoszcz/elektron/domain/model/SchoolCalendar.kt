package pl.zse.bydgoszcz.elektron.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Dni bez zajęć w szkole w Bydgoszczy (województwo kujawsko-pomorskie). Strona szkoły pokazuje stały
 * plan tygodnia także w święta i ferie - bez tego aplikacja przypominała o lekcjach, pokazywała
 * powiadomienie na żywo i "następną lekcję" np. 11 listopada.
 *
 * Reguły (rozporządzenie MEN w sprawie organizacji roku szkolnego i ustawa o dniach wolnych od pracy):
 *  - święta ustawowe (Wielkanoc i Boże Ciało liczone z daty Wielkanocy, Wigilia od 2025 r.),
 *  - zimowa przerwa świąteczna 23-31 grudnia,
 *  - wiosenna przerwa świąteczna od czwartku przed Wielkanocą do wtorku po niej,
 *  - wakacje od dnia po ostatnim piątku czerwca do 31 sierpnia, a gdy 1 września wypada w piątek
 *    albo sobotę - do najbliższego poniedziałku,
 *  - ferie zimowe z tabeli [WINTER_BREAKS] (ustala je MEN co roku dla województw).
 *
 * Dni wolne ustalane przez dyrektora szkoły nie są tu znane - szkoła ogłasza je na stronie zastępstw.
 */
object SchoolCalendar {

    /**
     * Ferie zimowe w kujawsko-pomorskim (od, do włącznie) wg komunikatów MEN na gov.pl.
     * Na kolejny rok szkolny trzeba dopisać nowy termin (MEN ogłasza je z wyprzedzeniem).
     * 2026/2027: https://www.gov.pl/web/edukacja/terminy-ferii-zimowych-w-roku-szkolnym-20262027
     */
    private val WINTER_BREAKS = listOf(
        LocalDate.of(2027, 2, 15) to LocalDate.of(2027, 2, 28)   // 2026/2027
    )

    /** Dlaczego tego dnia nie ma zajęć (np. "Święto Niepodległości"); null - zwykły dzień. Weekendy pomijane. */
    fun dayOff(date: LocalDate): String? {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return null
        holiday(date)?.let { return it }
        if (date.month == Month.DECEMBER && date.dayOfMonth in 23..31) return "Zimowa przerwa świąteczna"
        val easter = easter(date.year)
        if (!date.isBefore(easter.minusDays(3)) && !date.isAfter(easter.plusDays(2))) return "Wiosenna przerwa świąteczna"
        if (WINTER_BREAKS.any { (from, to) -> !date.isBefore(from) && !date.isAfter(to) }) return "Ferie zimowe"
        if (isSummerBreak(date)) return "Wakacje"
        return null
    }

    /**
     * Czy reguła "lekcja się odbywa" uwzględnia dni wolne. Testy jednostkowe budują lekcje na prawdziwe
     * "dziś", więc gradle wyłącza kalendarz (elektron.schoolCalendar=off) - inaczej padałyby w święta.
     */
    @Volatile internal var enabled: Boolean = System.getProperty("elektron.schoolCalendar") != "off"

    fun isDayOff(date: LocalDate): Boolean = enabled && dayOff(date) != null

    /** Święta ustawowo wolne od pracy w dni robocze. */
    private fun holiday(date: LocalDate): String? {
        val easter = easter(date.year)
        return when {
            date.month == Month.JANUARY && date.dayOfMonth == 1 -> "Nowy Rok"
            date.month == Month.JANUARY && date.dayOfMonth == 6 -> "Trzech Króli"
            date == easter.plusDays(1) -> "Poniedziałek Wielkanocny"
            date.month == Month.MAY && date.dayOfMonth == 1 -> "Święto Pracy"
            date.month == Month.MAY && date.dayOfMonth == 3 -> "Święto Konstytucji 3 Maja"
            date == easter.plusDays(60) -> "Boże Ciało"
            date.month == Month.AUGUST && date.dayOfMonth == 15 -> "Wniebowzięcie NMP"
            date.month == Month.NOVEMBER && date.dayOfMonth == 1 -> "Wszystkich Świętych"
            date.month == Month.NOVEMBER && date.dayOfMonth == 11 -> "Święto Niepodległości"
            date.month == Month.DECEMBER && date.dayOfMonth == 24 && date.year >= 2025 -> "Wigilia"
            date.month == Month.DECEMBER && date.dayOfMonth in 25..26 -> "Boże Narodzenie"
            else -> null
        }
    }

    /** Wakacje: od dnia po ostatnim piątku czerwca do dnia przed początkiem roku szkolnego. */
    private fun isSummerBreak(date: LocalDate): Boolean {
        val lastFriday = LocalDate.of(date.year, 6, 30).with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
        return date.isAfter(lastFriday) && date.isBefore(schoolYearStart(date.year))
    }

    /** 1 września, a gdy wypada w piątek albo sobotę - najbliższy poniedziałek. */
    fun schoolYearStart(year: Int): LocalDate {
        val first = LocalDate.of(year, 9, 1)
        return if (first.dayOfWeek == DayOfWeek.FRIDAY || first.dayOfWeek == DayOfWeek.SATURDAY)
            first.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else first
    }

    /** Wielkanoc (algorytm Gaussa w wersji Meeusa/Jonesa/Butchera, kalendarz gregoriański). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
