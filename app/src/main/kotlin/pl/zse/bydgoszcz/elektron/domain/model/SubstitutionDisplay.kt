package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Jak pokazywać zastępstwo — jedno miejsce dla planu, strony głównej, zakładki Zastępstwa
 * i widżetów:
 *  - nagłówek: lekcja na zastępstwie, gdy szkoła podała ją w uwagach ("edukacja zdrowotna",
 *    "za nieobecny oddział; matematyka"); inaczej nauczyciel, który zastępuje; bez zastępcy —
 *    informacja ze strony ("Uczniowie przychodzą później", "Zajęcia Świetlicowe"),
 *  - druga linia: przy lekcji - nauczyciel i sala ("D. Musierowicz · s. 111"), inaczej sala albo tekst informacji,
 *  - uwagi ("za ostatnią lekcję") — zawsze widoczne, bez powtórzonej nazwy lekcji.
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
        lesson(sub)
            ?: (if (infoFirst) info(sub) else null)
            ?: sub.substituteTeacher?.trim()?.takeIf { it.isNotEmpty() }
            ?: sub.roomOrInfo.trim().takeIf { it.isNotEmpty() }
            ?: "Zastępstwo"

    /** Sama sala albo informacja szkoły ("s. 111", "Zajęcia Świetlicowe") - pole "Gdzie" obok osobno podanego zastępcy. */
    fun where(sub: Substitution): String? =
        sub.roomOrInfo.trim().takeIf { it.isNotEmpty() }?.let { if (isRoom(it)) Rooms.label(it) else it }

    /** Sala lub informacja pod nagłówkiem (przy [infoFirst] - nauczyciel pod informacją); null, gdy nie ma czego dodać. */
    fun place(sub: Substitution, infoFirst: Boolean = false): String? {
        val teacher = sub.substituteTeacher?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (lesson(sub) != null) return listOfNotNull(teacher, where(sub)).joinToString(" · ")
        if (infoFirst && info(sub) != null) return teacher
        return where(sub)
    }

    fun notes(sub: Substitution): String? {
        val text = sub.notes?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val (segment, lesson) = lessonSegment(sub) ?: return text
        // Fragment z dopiskiem ("j. angielski z 0 h lek. sala 204") zostaje w całości - dopisek jest ważny.
        if (segment != lesson) return text
        // Bez powtórzonej nazwy lekcji (jest w nagłówku): "za nieobecny oddział; matematyka" -> "za nieobecny oddział".
        return text.replaceFirst(segment, "").replace(Regex("\\s*[;,]\\s*[;,]\\s*"), "; ")
            .trim().trim(';', ',').trim().takeIf { it.isNotEmpty() }
    }

    private val SEPARATORS = Regex("\\s*[;,]\\s*")
    /** Dopiski po nazwie lekcji: " - teoria", " (zabrać zeszyty)", " z 0 h lek.", " sala 204", " na 8 h lek.". */
    private val LESSON_TAIL = Regex("\\s+-\\s+.*$|\\s*-\\s+.*$|\\s*\\(.*$|\\s+z\\s+\\d+\\s*(h|lek|godz).*$|\\s+sala\\s+.*$|\\s+na\\s+\\d+\\s*h.*$",
        RegexOption.IGNORE_CASE)
    /** Uwagi organizacyjne, nie nazwa lekcji. */
    private val NOT_LESSON = Regex("oddzia|lekcj|przenies|przeło[zż]|zaj[eę]ci|grup|osob|zwoln|przychodz|[sś]wietlic|uczni|^za\\s|\\d",
        RegexOption.IGNORE_CASE)
    /** Uwagi dotyczące części klasy ("grupa 1/2 zwolniona do domu") - lekcja nie dla wszystkich, bez nagłówka z lekcji. */
    private val PARTIAL = Regex("grup|osob|zwoln|przychodz|[sś]wietlic|uczni", RegexOption.IGNORE_CASE)

    /** (fragment uwag, nazwa lekcji) - pierwszy fragment uwag, który jest nazwą lekcji; tylko przy wpisanym zastępcy. */
    private fun lessonSegment(sub: Substitution): Pair<String, String>? {
        if (sub.substituteTeacher.isNullOrBlank()) return null
        val text = sub.notes?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (PARTIAL.containsMatchIn(text)) return null
        return SEPARATORS.split(text).asSequence().map { it.trim() }.mapNotNull { segment ->
            val name = segment.replace(LESSON_TAIL, "").trim().trimEnd('.', ':')
            name.takeIf { it.length in 2..40 && it.any(Char::isLetter) && !NOT_LESSON.containsMatchIn(it) }?.let { segment to it }
        }.firstOrNull()
    }

    /** Lekcja na zastępstwie podana przez szkołę w uwagach ("edukacja zdrowotna"); null, gdy jej nie ma. */
    fun lesson(sub: Substitution): String? = lessonSegment(sub)?.second

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

    /**
     * Okienko i zwolnienie dotyczą całej klasy, chyba że uwaga mówi tylko o grupie, a wpis nie ma numeru grupy
     * ("okienko dla grupy 2/2" albo "Zajęcia Świetlicowe" z "I gr. - do domu" przy "1 I"): reszta klasy
     * ma wtedy zajęcia, a nie wiadomo, której grupy z planu to dotyczy - lekcja zostaje z informacją szkoły.
     */
    private val WINDOW = Regex("okienk", RegexOption.IGNORE_CASE)
    // Także "dot. uczęszczających na religię, reszta zwolniona do domu": część klasy zostaje w szkole.
    private val GROUP = Regex("grup|\\bgr\\.|\\breszt|\\bpozosta[lł]|\\bdot\\.|ucz[eę]szczaj|\\bosoby\\b", RegexOption.IGNORE_CASE)

    fun freesLesson(sub: Substitution): Boolean {
        if (!sub.substituteTeacher.isNullOrBlank()) return false
        val text = sub.roomOrInfo + " " + (sub.notes ?: "")
        if (sub.groupNumber == null && GROUP.containsMatchIn(text)) return false
        return FREES.containsMatchIn(text) || WINDOW.containsMatchIn(text)
    }

    /**
     * Lekcja odbywa się: nie ma zwolnienia ani dnia wolnego ([SchoolCalendar] - święta, przerwy, ferie,
     * wakacje, koniec zajęć klas maturalnych). Wspólna reguła przypomnień, powiadomienia na żywo, strony głównej,
     * widżetów i Przejazdów.
     */
    fun takesPlace(lesson: Lesson): Boolean =
        !SchoolCalendar.isDayOff(lesson.date) && !SchoolCalendar.classFinished(lesson.className, lesson.date) &&
            (lesson.substitution?.let { !freesLesson(it) } ?: true)
}
