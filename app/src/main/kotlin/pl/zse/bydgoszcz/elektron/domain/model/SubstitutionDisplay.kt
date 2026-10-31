package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Jak pokazywać zastępstwo — jedno miejsce dla planu, strony głównej, zakładki Zastępstwa
 * i widżetów:
 *  - nagłówek: nauczyciel, który zastępuje (najważniejsza informacja); bez zastępcy —
 *    informacja ze strony ("Uczniowie przychodzą później", "Zajęcia Świetlicowe"),
 *  - druga linia: sala ("s. 211") albo tekst informacji,
 *  - uwagi ("za ostatnią lekcję", "historia") — zawsze widoczne.
 */
object SubstitutionDisplay {

    /** Krótki token bez spacji = numer/nazwa sali ("211", "pc2", "W-6", "S1"). */
    private val ROOM = Regex("^[\\p{L}\\d.\\-/]{1,6}$")

    fun isRoom(text: String): Boolean = ROOM.matches(text.trim())

    /** Informacja szkoły (nie numer sali), np. "Zajęcia w auli"; null przy samej sali. */
    fun info(sub: Substitution): String? = sub.roomOrInfo.trim().takeIf { it.isNotEmpty() && !isRoom(it) }

    /**
     * [infoFirst] - widok dnia w planie: informacja szkoły jest nagłówkiem także przy wpisanym
     * zastępcy, a nauczyciel trafia do drugiej linii. Strona główna, plan tygodniowy, zakładka
     * Zastępstwa, widżety i powiadomienia: najpierw nauczyciel.
     */
    fun headline(sub: Substitution, infoFirst: Boolean = false): String =
        (if (infoFirst) info(sub) else null)
            ?: sub.substituteTeacher?.trim()?.takeIf { it.isNotEmpty() }
            ?: sub.roomOrInfo.trim().takeIf { it.isNotEmpty() }
            ?: "Zastępstwo"

    /** Sala lub informacja pod nagłówkiem (przy [infoFirst] - nauczyciel pod informacją); null, gdy nie ma czego dodać. */
    fun place(sub: Substitution, infoFirst: Boolean = false): String? {
        val teacher = sub.substituteTeacher?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (infoFirst && info(sub) != null) return teacher
        val text = sub.roomOrInfo.trim().takeIf { it.isNotEmpty() } ?: return null
        return if (isRoom(text)) "s. $text" else text
    }

    fun notes(sub: Substitution): String? = sub.notes?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Wpisy, po których lekcja się dla ucznia NIE odbywa (zwolnienie klasy/grupy): bez zastępcy
     * i z informacją typu "Uczniowie zwolnieni do domu", "Uczniowie przychodzą później",
     * "lekcja odwołana", "przeniesiona na 22.10.2026 (0)". "Przeniesione do sali" i "na salę 215" to zmiana
     * sali, a "przeniesiona z czwartku" - lekcja, która się odbywa. "Zajęcia świetlicowe" to NIE zwolnienie
     * - uczniowie są w szkole. Przeniesiona lekcja nie pojawia się w nowym terminie, zostaje informacja w planie.
     */
    private val FREES = Regex(
        "zwolnien|przychodz\\p{L}* p[oó]\\p{L}*niej|odwo[lł]an|do domu|nie odbywa|wolne od zaj|" +
            "(?:przeniesion|prze[lł]o[zż]on)\\p{L}* na (?!sal)",
        RegexOption.IGNORE_CASE
    )

    /** Okienko zwalnia lekcję, chyba że wpis dotyczy tylko grupy ("okienko dla grupy 2/2" przy "5 J" bez numeru grupy). */
    private val WINDOW = Regex("okienk", RegexOption.IGNORE_CASE)
    private val GROUP = Regex("grup|\\bgr\\.", RegexOption.IGNORE_CASE)

    fun freesLesson(sub: Substitution): Boolean {
        if (!sub.substituteTeacher.isNullOrBlank()) return false
        val text = sub.roomOrInfo + " " + (sub.notes ?: "")
        return FREES.containsMatchIn(text) || (WINDOW.containsMatchIn(text) && !GROUP.containsMatchIn(text))
    }

    /**
     * Lekcja odbywa się: nie ma zwolnienia ani dnia wolnego ([SchoolCalendar] - święta, przerwy, ferie,
     * wakacje). Wspólna reguła przypomnień, powiadomienia na żywo, strony głównej, widżetów i Przejazdów.
     */
    fun takesPlace(lesson: Lesson): Boolean =
        !SchoolCalendar.isDayOff(lesson.date) && (lesson.substitution?.let { !freesLesson(it) } ?: true)
}
