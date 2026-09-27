package pl.zse.bydgoszcz.elektron.data.remote.sources

import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto

/**
 * Źródło zastępstw. Strona zwraca ISO-8859-2 bez poprawnego nagłówka —
 * implementacja MUSI wymusić encoding. Implementacja: ZseSubstitutionsSource.
 */
interface SubstitutionsSource {
    suspend fun fetchSubstitutions(): List<SubstitutionDto>
}
