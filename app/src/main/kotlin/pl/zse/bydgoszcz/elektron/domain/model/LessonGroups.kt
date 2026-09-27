package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Podział klasy na grupy zajęciowe.
 *
 * Optivum dokleja oznaczenie grupy do skrótu przedmiotu, np.:
 *   "ang-1/2", "zaj.prakt-3/3", "rel-1/1", "wf-j1", "wf-j2", "inf-gr2".
 * Pole LessonGroup.groupLabel z parsera jest zawsze puste, więc grupę wyciągamy z nazwy.
 *
 * Wybór użytkownika trzymamy per klasa jako mapę: przedmiot bazowy -> etykieta grupy
 * (np. "zaj.prakt" -> "2/3") albo [NONE] ("nie chodzę"). Brak wpisu = pokazuj wszystko.
 */
object LessonGroups {

    /** Wartość wyboru oznaczająca "nie chodzę na ten przedmiot". */
    const val NONE = "-"

    data class ParsedSubject(val base: String, val label: String?)

    /** Przedmiot z podziałem na grupy i dostępnymi etykietami, do ekranu wyboru. */
    data class DividedSubject(val base: String, val labels: List<String>) {
        /** Jedna etykieta (np. religia "1/1") => wystarczy przełącznik pokaż/ukryj. */
        val isToggle: Boolean get() = labels.size == 1
    }

    // "-1/2", "-12/3"
    private val FRACTION = Regex("^(.+?)-(\\d{1,2})/(\\d{1,2})$")
    // "-j1", "-gr2", "-1" — krótka etykieta zakończona cyfrą (bez tego "j.pol-ang" itp.
    // z myślnikiem w nazwie byłyby mylone z grupą).
    private val SHORT_LABEL = Regex("^(.+?)-([A-Za-z]{0,3}\\d{1,2})$")
    private val TRAILING_DIGITS = Regex("(\\d+)$")

    fun parse(subject: String?): ParsedSubject? {
        val s = subject?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        FRACTION.find(s)?.let { m ->
            return ParsedSubject(m.groupValues[1].trim(), "${m.groupValues[2]}/${m.groupValues[3]}")
        }
        SHORT_LABEL.find(s)?.let { m ->
            return ParsedSubject(m.groupValues[1].trim(), m.groupValues[2])
        }
        return ParsedSubject(s, null)
    }

    /** Numer grupy z etykiety: "2/3" -> 2, "j2" -> 2 (do dopasowania zastępstw "2D(2)"). */
    fun labelNumber(label: String): Int? =
        if ('/' in label) label.substringBefore('/').toIntOrNull()
        else TRAILING_DIGITS.find(label)?.groupValues?.get(1)?.toIntOrNull()

    /** Czytelna nazwa opcji: "2/3" -> "Grupa 2", "j1" -> "j1". */
    fun displayLabel(label: String): String =
        if ('/' in label) "Grupa ${label.substringBefore('/')}" else label

    /** Wszystkie przedmioty z podziałem na grupy w podanych lekcjach (np. tydzień planu). */
    fun detect(lessons: List<Lesson>): List<DividedSubject> {
        val byBase = sortedMapOf<String, MutableSet<String>>(String.CASE_INSENSITIVE_ORDER)
        for (lesson in lessons) for (g in lesson.groups) {
            val p = parse(g.subject) ?: continue
            val label = p.label ?: continue
            byBase.getOrPut(p.base) { sortedSetOf(compareBy({ labelNumber(it) ?: Int.MAX_VALUE }, { it })) } += label
        }
        return byBase.map { (base, labels) -> DividedSubject(base, labels.toList()) }
    }

    private fun groupVisible(g: LessonGroup, selections: Map<String, String>): Boolean {
        val p = parse(g.subject) ?: return true
        val label = p.label ?: return true          // lekcja bez podziału — zawsze
        val choice = selections[p.base] ?: return true  // nie wybrano — pokazuj wszystko
        return choice == label
    }

    /**
     * Zostawia tylko grupy, na które użytkownik chodzi. Lekcja, w której nie zostaje żadna
     * grupa, znika. Zastępstwo z numerem grupy, która nie jest grupą użytkownika, jest
     * zdejmowane z lekcji.
     */
    fun filter(lessons: List<Lesson>, selections: Map<String, String>): List<Lesson> {
        if (selections.isEmpty()) return lessons
        return lessons.mapNotNull { lesson ->
            if (lesson.groups.isEmpty()) return@mapNotNull lesson
            val visible = lesson.groups.filter { groupVisible(it, selections) }
            if (visible.isEmpty()) return@mapNotNull null
            val sub = lesson.substitution
            val keepSub = sub == null || substitutionMatchesGroups(sub, visible)
            lesson.copy(groups = visible, substitution = if (keepSub) sub else null)
        }
    }

    private fun substitutionMatchesGroups(sub: Substitution, visible: List<LessonGroup>): Boolean {
        val n = sub.groupNumber ?: return true
        // Jeśli którakolwiek widoczna grupa jest dla całej klasy — zastępstwo nas dotyczy.
        val labels = visible.map { parse(it.subject)?.label }
        if (labels.any { it == null }) return true
        return labels.any { it != null && labelNumber(it) == n }
    }

    /**
     * Czy zastępstwo dotyczy użytkownika, biorąc pod uwagę wybrane grupy.
     * [rawLessons] to NIEFILTROWANE lekcje klasy (dowolny zakres zawierający datę zastępstwa).
     */
    fun substitutionRelevant(
        sub: Substitution,
        rawLessons: List<Lesson>,
        selections: Map<String, String>
    ): Boolean {
        if (sub.groupNumber == null || selections.isEmpty()) return true
        val lesson = rawLessons.firstOrNull { it.date == sub.date && it.number == sub.lessonNumber }
            ?: return true
        val visible = lesson.groups.filter { groupVisible(it, selections) }
        if (visible.isEmpty()) return false
        return substitutionMatchesGroups(sub, visible)
    }
}
