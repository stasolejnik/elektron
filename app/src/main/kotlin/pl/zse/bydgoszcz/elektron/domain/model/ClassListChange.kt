package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Zmiana listy klas na stronie szkoły (nowy rok szkolny): wybrana klasa zniknęła albo jej
 * identyfikator dostała inna klasa (np. "o3" to teraz 1D zamiast 3D). Plan pobierany dalej pod starym
 * identyfikatorem byłby po cichu planem innej klasy - aplikacja prosi wtedy o ponowny wybór.
 */
object ClassListChange {
    /** Mniej klas na liście to raczej zmiana strony albo błąd odczytu niż nowy rok szkolny. */
    const val MIN_CLASSES = 3

    /**
     * [before] - zapisana nazwa wybranej klasy (null - nieznana), [fresh] - lista klas z tej
     * synchronizacji (null - listy nie pobierano).
     */
    fun selectedIsStale(classId: String, before: String?, fresh: List<SchoolClass>?): Boolean {
        if (fresh == null || fresh.size < MIN_CLASSES) return false
        val now = fresh.firstOrNull { it.id == classId } ?: return true
        return before != null && !now.shortName.equals(before, ignoreCase = true)
    }
}
