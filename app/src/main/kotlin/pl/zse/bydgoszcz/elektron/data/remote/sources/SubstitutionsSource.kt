package pl.zse.bydgoszcz.elektron.data.remote.sources

import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto

/**
 * Źródło zastępstw. Strona zwraca ISO-8859-2 bez poprawnego nagłówka —
 * implementacja MUSI wymusić encoding. Implementacja: ZseSubstitutionsSource.
 */
interface SubstitutionsSource {
    suspend fun fetchSubstitutions(): List<SubstitutionDto>

    /**
     * Wpisy + daty pokazane na stronie (także dni bez wpisów). Domyślnie daty z samych wpisów -
     * wystarcza atrapom w testach; prawdziwe źródło czyta nagłówki dat ze strony.
     */
    suspend fun fetchPage(): SubstitutionsPage {
        val items = fetchSubstitutions()
        return SubstitutionsPage(items.map { it.dateRaw }.toSet(), items)
    }
}

/** Strona zastępstw: [dates] - dni pokazane na stronie ("dd.mm.rrrr"), [items] - wpisy. */
data class SubstitutionsPage(val dates: Set<String>, val items: List<SubstitutionDto>)
