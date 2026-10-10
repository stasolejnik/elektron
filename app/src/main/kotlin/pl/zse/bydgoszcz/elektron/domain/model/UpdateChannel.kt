package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Kanał aktualizacji z GitHuba (tylko wersja z GitHuba, nie F-Droid). Domyślnie stabilny:
 * uczniowie dostają wyłącznie wydania stabilne. Beta: także wydania testowe (Pre-release na
 * GitHubie albo wersja z dopiskiem, np. 1.0.1-beta1) - i oczywiście kolejne stabilne.
 */
enum class UpdateChannel(val key: String, val label: String) {
    STABLE("stable", "Stabilne"),
    BETA("beta", "Beta");

    /** Czy wydanie [tag] (z flagą Pre-release z GitHuba) pasuje do kanału. */
    fun accepts(tag: String, prerelease: Boolean): Boolean =
        this == BETA || (!prerelease && AppVersion.isStable(tag))

    companion object {
        fun fromKey(key: String?): UpdateChannel = entries.firstOrNull { it.key == key } ?: STABLE
    }
}
