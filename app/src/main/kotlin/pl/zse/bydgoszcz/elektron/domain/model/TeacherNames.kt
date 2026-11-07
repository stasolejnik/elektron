package pl.zse.bydgoszcz.elektron.domain.model

/** Zapis nazwisk nauczycieli w interfejsie. */
object TeacherNames {
    /** Inicjał bez spacji ("M.Kucharska" z listy nauczycieli planu) - po inicjale spacja, jak na stronie zastępstw. */
    private val INITIAL = Regex("(?<![\\p{L}.])(\\p{Lu})\\.(?=\\p{L})")

    /**
     * "M.Kucharska" -> "M. Kucharska", "J.Siepniewska-Stańczyk" -> "J. Siepniewska-Stańczyk". Dawniej ta sama
     * osoba była w planie "D.Musierowicz", a w zastępstwie obok "D. Musierowicz".
     */
    fun display(name: String): String = name.trim().replace(INITIAL, "$1. ")
}
