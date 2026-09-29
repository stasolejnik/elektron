package pl.zse.bydgoszcz.elektron.domain.model

/** Własna nazwa i/lub kolor przedmiotu. null = domyślne (nazwa ze strony szkoły, bez koloru). */
data class SubjectStyle(val name: String? = null, val color: Long? = null) {
    val isDefault: Boolean get() = name == null && color == null
}

/**
 * Personalizacja przedmiotów, wspólna dla wszystkich klas. Kluczem jest nazwa przedmiotu
 * bez sufiksu grupy ("wf", nie "wf-j1") - jedna zmiana obejmuje wszystkie grupy.
 * Zapis w DataStore jako linie "przedmiot<TAB>nazwa<TAB>kolor" (jak wybór grup).
 */
object SubjectStyles {

    /** Paleta kolorów przedmiotów (ARGB), czytelna w jasnym i ciemnym motywie. */
    val PALETTE: List<Long> = listOf(
        0xFFE53935, // czerwony
        0xFFF4511E, // pomarańczowy
        0xFFF6BF26, // żółty
        0xFF7CB342, // zielony
        0xFF009688, // morski
        0xFF039BE5, // błękitny
        0xFF3F51B5, // granatowy
        0xFF8E24AA, // fioletowy
        0xFFD81B60, // różowy
        0xFF795548, // brązowy
        0xFF757575  // szary
    )

    private const val MAX_NAME = 40

    /** Klucz przedmiotu: nazwa bez sufiksu grupy. */
    fun key(subject: String?): String? {
        val raw = subject?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return LessonGroups.parse(raw)?.base ?: raw
    }

    /** Nazwa do wyświetlenia: własna albo oryginalna (bez sufiksu grupy). */
    fun displayName(subject: String?, styles: Map<String, SubjectStyle>): String? {
        val k = key(subject) ?: return null
        return styles[k]?.name ?: k
    }

    fun colorOf(subject: String?, styles: Map<String, SubjectStyle>): Long? =
        key(subject)?.let { styles[it]?.color }

    /** Oczyszcza wpisaną nazwę: bez tabulatorów i nowych linii, maks. 40 znaków, pusta = domyślna. */
    fun cleanName(input: String?, original: String): String? {
        val cleaned = input?.replace(Regex("[\\t\\r\\n]+"), " ")?.trim()?.take(MAX_NAME)
        return cleaned?.takeIf { it.isNotEmpty() && it != original }
    }

    fun decode(raw: String?): Map<String, SubjectStyle> =
        raw?.lineSequence()?.mapNotNull { line ->
            val parts = line.split('\t')
            val key = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            val color = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.toLongOrNull(16)
            SubjectStyle(name, color).takeIf { !it.isDefault }?.let { key to it }
        }?.toMap() ?: emptyMap()

    fun encode(map: Map<String, SubjectStyle>): String =
        map.entries.filter { !it.value.isDefault }.sortedBy { it.key }.joinToString("\n") { (k, s) ->
            "$k\t${s.name ?: ""}\t${s.color?.toString(16)?.uppercase() ?: ""}"
        }
}
