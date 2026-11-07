package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Treść powiadomienia o zastępstwie - te same reguły co w aplikacji (SubstitutionDisplay):
 * nagłówek to zastępca albo informacja ze strony ("Uczniowie przychodzą później"), nieobecny
 * nauczyciel po "Za:". Dawniej "Zastępuje: <nieobecny>" sugerowało odwrotną rolę, a zwolnienie
 * było "Nowym zastępstwem" bez dnia w tytule.
 */
object SubstitutionNotice {
    data class Content(val title: String, val text: String, val expanded: String)

    private val PL = Locale("pl", "PL")
    private val DATE = DateTimeFormatter.ofPattern("dd.MM")

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Dziś"
        today.plusDays(1) -> "Jutro"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, PL).replaceFirstChar { it.titlecase(PL) } +
            " " + date.format(DATE)
    }

    /**
     * [subject] - nazwa przedmiotu do wyświetlenia (po personalizacji), jeśli znana.
     *
     * Układ jak wiersz w zakładce Zastępstwa: tytuł "Dziś, 6. lekcja: edukacja zdrowotna" (dzień, lekcja i to,
     * co się zmieniło), pod nim zastępca z salą, potem "Za: przedmiot · nieobecny" i uwagi - każda informacja raz.
     * Zwinięte powiadomienie: tytuł i pierwsza linia (zastępca z salą albo, przy zwolnieniu, "Za: …").
     */
    fun content(sub: Substitution, subject: String?, today: LocalDate): Content {
        val day = dayLabel(sub.date, today)
        val group = sub.groupNumber?.let { " (grupa $it)" } ?: ""
        val place = SubstitutionDisplay.place(sub)
        val forWhom = listOfNotNull(subject?.takeIf { it.isNotBlank() }, sub.originalTeacher.trim().takeIf { it.isNotEmpty() })
            .takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Za: ")
        val notes = SubstitutionDisplay.notes(sub)
        val lines = listOfNotNull(place, forWhom, notes?.let { "Uwagi: $it" })
        return Content(
            title = "$day, ${sub.lessonNumber}. lekcja$group: ${SubstitutionDisplay.headline(sub)}",
            text = lines.firstOrNull() ?: "",
            expanded = lines.joinToString("\n")
        )
    }

    /**
     * Stały znacznik powiadomienia dla lekcji (dzień, numer, klasa, grupa, nieobecny nauczyciel). ID zastępstwa zmienia
     * się po poprawce sali lub zastępcy - z tym znacznikiem nowa wersja zastępuje starą w panelu.
     */
    fun tag(sub: Substitution): String =
        "sub:${sub.date.toEpochDay()}|${sub.lessonNumber}|${sub.classShortName.trim().uppercase(PL)}|${sub.groupNumber ?: 0}|" +
            // Nieobecny nauczyciel też: dwa różne zastępstwa tej samej lekcji (dwóch nieobecnych) nie
            // zastępują się w panelu. Poprawka (inna sala, inny zastępca) ma tego samego nieobecnego.
            sub.originalTeacher.trim().uppercase(PL)

    /**
     * ID zastępstwa + skrót uwag. Pozostałe pola (sala, zastępca, nauczyciel) są już w ID, które
     * zostaje bez zmian - liczy je tak samo watcher pushy.
     */
    fun fingerprint(sub: Substitution): String =
        "${sub.id}$FINGERPRINT_SEPARATOR${(sub.notes ?: "").trim().hashCode().toUInt().toString(16)}"

    /**
     * Zastępstwa już widziane, w których zmieniły się same uwagi. Bez zapisanego odcisku dla
     * danego ID (pierwsza synchronizacja po aktualizacji, zastępstwo z pusha) - nic: najpierw
     * zapisujemy odcisk, inaczej aktualizacja aplikacji powiadomiłaby o wszystkich naraz.
     */
    fun changedNotes(current: List<Substitution>, seenIds: Set<String>, seenFingerprints: Set<String>): List<Substitution> {
        val withFingerprint = seenFingerprints.mapTo(HashSet()) { it.substringBefore(FINGERPRINT_SEPARATOR) }
        return current.filter { it.id in seenIds && it.id in withFingerprint && fingerprint(it) !in seenFingerprints }
    }

    private const val FINGERPRINT_SEPARATOR = '\u0001'
}
