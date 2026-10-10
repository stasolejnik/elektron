package pl.zse.bydgoszcz.elektron.data.mapper

import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import java.text.Normalizer
import java.util.Locale

/**
 * Zastępstwa bez klasy, np. "Wychowanie fizyczne - Zajęcia Świetlicowe": przy zajęciach łączonych
 * kilku klas Optivum podaje przedmiot zamiast klasy. Klasę (i grupę) ustalamy z zapisanego planu,
 * w tej samej dacie i lekcji:
 *  1. grupa prowadzona przez nieobecnego nauczyciela (strona zastępstw: "Dariusz Czyżewski",
 *     lista nauczycieli planu: "D.Czyżewski" - nazwisko i inicjał imienia); gdy w szkole jest kilku
 *     nauczycieli o tym nazwisku i inicjale, nie zgadujemy;
 *  2. w lekcjach bez takiej grupy: grupa łączona bez nauczyciela w planie klasy ("wf-j2 #1AF") ze
 *     zgodnym przedmiotem - tylko gdy w tej lekcji klasy jest dokładnie jedna taka grupa I gdy zapisany
 *     plan klasy współdzielącej grupę (tu 1A) potwierdza nauczyciela tej samej grupy.
 * Bez dopasowania wpis nie dotyczy żadnej zapisanej klasy.
 */
internal object ClasslessSubstitutions {
    /**
     * Grupa lekcji z zapisanego planu. [subject] - nazwa z planu (z etykietą grupy, np. "wf-2/2"),
     * [classRef] - znacznik z planu ("#1AF" - grupa łączona z innymi klasami).
     */
    data class Slot(val dateRaw: String, val lessonNumber: Int, val classShortName: String, val subject: String?,
        val teacherName: String?, val classRef: String? = null)

    /** [teacherNames] - wszyscy nauczyciele z listy planu (do wykrycia niejednoznacznych nazwisk). */
    fun resolve(entries: List<SubstitutionDto>, slots: List<Slot>, teacherNames: Collection<String> = emptyList()): List<SubstitutionDto> =
        entries.flatMap { entry ->
            val sameLesson = slots.filter { it.dateRaw == entry.dateRaw && it.lessonNumber == entry.lessonNumber }
            val ambiguous = teacherNames.count { teacherMatches(entry.originalTeacher, it) } > 1
            val byTeacher = if (ambiguous) emptyList() else sameLesson.filter { teacherMatches(entry.originalTeacher, it.teacherName) }
            val taughtClasses = byTeacher.mapTo(HashSet()) { it.classShortName }
            // Grupa łączona bez nauczyciela tylko z potwierdzeniem: w planie klasy współdzielącej tę grupę
            // ("#1AF" - 1A i 1F) ta sama grupa ma nieobecnego nauczyciela. Sam przedmiot nie wystarcza -
            // nauczyciel mógł prowadzić inną grupę, a wpis (np. zwolnienie) trafiłby do niewłaściwych uczniów.
            val joined = sameLesson.filter { slot ->
                slot.classShortName !in taughtClasses && slot.teacherName == null && slot.classRef?.startsWith("#") == true &&
                    subjectMatches(entry.subject, slot.subject) && byTeacher.any { confirms(it, slot) }
            }.groupBy { it.classShortName }.values.mapNotNull { it.singleOrNull() }
            (byTeacher + joined).map { slot ->
                val label = slot.subject?.let(LessonGroups::parse)?.label
                entry.copy(classShortName = slot.classShortName, groupNumber = label?.let(LessonGroups::labelNumber))
            }
        }.distinctBy { listOf(it.dateRaw, it.lessonNumber, it.classShortName, it.groupNumber, it.originalTeacher) }

    /** Lekcja nauczyciela [taught] w innej klasie potwierdza grupę łączoną [joint]: ta sama grupa, a klasa należy do łączenia. */
    internal fun confirms(taught: Slot, joint: Slot): Boolean {
        val a = LessonGroups.parse(taught.subject) ?: return false
        val b = LessonGroups.parse(joint.subject) ?: return false
        if (!a.base.equals(b.base, ignoreCase = true) || a.label != b.label) return false
        return normalizeClass(taught.classShortName) in jointClasses(joint.classRef)
    }

    /** "#1AF" -> {"1A", "1F"}; "#1A2B" -> {"1A", "2B"}. */
    internal fun jointClasses(ref: String?): Set<String> {
        val result = mutableSetOf<String>()
        var number = ""
        Regex("(\\d+)|(\\p{L})").findAll(ref.orEmpty().removePrefix("#")).forEach { m ->
            if (m.groupValues[1].isNotEmpty()) number = m.groupValues[1]
            else if (number.isNotEmpty()) result += number + m.groupValues[2].uppercase(Locale.ROOT)
        }
        return result
    }

    private fun normalizeClass(name: String) = name.filter { !it.isWhitespace() }.uppercase(Locale.ROOT)

    /** "Dariusz Czyżewski" (strona zastępstw) i "D.Czyżewski" (plan): to samo nazwisko i pierwsza litera imienia. */
    fun teacherMatches(pageName: String, planName: String?): Boolean {
        val page = parts(pageName) ?: return false
        val plan = parts(planName ?: return false) ?: return false
        return page.second == plan.second && page.first.isNotEmpty() && plan.first.isNotEmpty() && page.first[0] == plan.first[0]
    }

    /**
     * Przedmiot ze strony zastępstw ("Wychowanie fizyczne", "Język angielski") i z planu ("wf-j2",
     * "j.angielski-2/2"): skrót z pierwszych liter ("wf"), skrót z inicjałów i ostatniego słowa
     * ("jangielski") albo ten sam początek pierwszego słowa (co najmniej 3 litery).
     */
    fun subjectMatches(pageSubject: String?, planSubject: String?): Boolean {
        val words = normalize(pageSubject ?: return false).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        val base = normalize(LessonGroups.parse(planSubject)?.base ?: return false).replace(Regex("[^a-z]"), "")
        if (words.isEmpty() || base.isEmpty()) return false
        val initials = words.joinToString("") { it.take(1) }
        val initialsAndLast = words.dropLast(1).joinToString("") { it.take(1) } + words.last()
        return base == initials || base == initialsAndLast || base == words.joinToString("") ||
            (base.length >= 3 && words.first().startsWith(base)) || (words.first().length >= 3 && base.startsWith(words.first()))
    }

    /** (imię albo inicjał, nazwisko) - bez wielkości liter i polskich znaków. */
    private fun parts(name: String): Pair<String, String>? {
        val tokens = normalize(name).split(Regex("[\\s.]+")).filter { it.isNotEmpty() }
        if (tokens.size < 2) return null
        return tokens.first() to tokens.last()
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
}
