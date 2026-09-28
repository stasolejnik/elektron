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

    fun headline(sub: Substitution): String =
        sub.substituteTeacher?.trim()?.takeIf { it.isNotEmpty() }
            ?: sub.roomOrInfo.trim().takeIf { it.isNotEmpty() }
            ?: "Zastępstwo"

    /** Sala lub informacja pod nagłówkiem; null, gdy nagłówkiem jest już informacja. */
    fun place(sub: Substitution): String? {
        if (sub.substituteTeacher.isNullOrBlank()) return null
        val text = sub.roomOrInfo.trim().takeIf { it.isNotEmpty() } ?: return null
        return if (isRoom(text)) "s. $text" else text
    }

    fun notes(sub: Substitution): String? = sub.notes?.trim()?.takeIf { it.isNotEmpty() }
}
