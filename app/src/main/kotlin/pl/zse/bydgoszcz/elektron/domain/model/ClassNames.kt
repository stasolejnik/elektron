package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Nazwy oddziałów z Optivum często powtarzają skrót klasy: tytuł planu to
 * "Plan lekcji oddziału - 1D 1D", a lista klas bywa w formie "1D 1D technik ...".
 * Usuwamy kolejne powtórzone słowa, żeby wszędzie w aplikacji było po prostu "1D".
 */
object ClassNames {
    private val WS = Regex("\\s+")

    fun clean(raw: String?): String? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return raw
        val out = mutableListOf<String>()
        for (token in s.split(WS)) {
            if (out.lastOrNull()?.equals(token, ignoreCase = true) != true) out += token
        }
        return out.joinToString(" ")
    }

    fun cleanNonNull(raw: String): String = clean(raw) ?: raw

    /**
     * Skrót klasy ("1D") z nazwy oddziału ("1D 1D PBŚ", "1D PBŚ", "5B") - pierwsze słowo.
     * Jedno miejsce dla listy klas i dopasowania zastępstw do lekcji (dawniej liczone osobno,
     * w planie dodatkowo obcinane do 2 znaków). Zastępstwa mają skrót w tej samej postaci.
     */
    fun shortName(name: String): String {
        val trimmed = name.trim()
        return trimmed.split(WS).first().ifEmpty { trimmed }
    }
}
