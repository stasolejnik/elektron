package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Zajęcia łączone z innymi oddziałami. Na stronie planu takie zajęcia nie mają nauczyciela,
 * tylko znacznik grupy (LessonGroup.classRef):
 *  - "#1AF" - rocznik 1, oddziały A i F (WF j2 w 1A i 1F),
 *  - "5B,5D" - lista oddziałów (komórka tekstowa "5B,5D zaj uni"),
 *  - "#RHJ", "#A_D" - wewnętrzny kod szkoły (religia) - bez rozszyfrowania.
 */
object JointGroups {

    private val YEAR_LETTERS = Regex("^#(\\d)(\\p{Lu}{2,})$")
    private val CLASS_LIST = Regex("^\\d\\p{L}{1,2}(,\\d\\p{L}{1,2})+$")

    /**
     * Opis do planu: "łączona z 1A" (bez własnego oddziału), "grupa łączona" dla kodów
     * nie do rozszyfrowania, null - to nie są zajęcia łączone.
     * [ownClass] - nazwa oddziału użytkownika, np. "1F Woj" (liczy się pierwsze słowo).
     */
    fun describe(classRef: String?, ownClass: String?): String? {
        val ref = classRef?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val own = ownClass?.trim()?.substringBefore(' ')?.uppercase()
        val classes = YEAR_LETTERS.find(ref)?.let { m ->
            val year = m.groupValues[1]
            m.groupValues[2].map { "$year$it" }
        } ?: if (CLASS_LIST.matches(ref)) ref.split(',').map { it.uppercase() } else null
        if (classes == null) return if (ref.startsWith("#")) "grupa łączona" else null
        val others = classes.filter { it != own }
        return if (others.isEmpty()) "grupa łączona" else "łączona z " + others.joinToString(", ")
    }
}
