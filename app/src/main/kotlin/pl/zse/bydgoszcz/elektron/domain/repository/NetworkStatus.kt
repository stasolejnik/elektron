package pl.zse.bydgoszcz.elektron.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Czy telefon ma teraz połączenie z internetem (zmienia się na bieżąco). */
interface NetworkStatus {
    val online: Flow<Boolean>

    /** Zawsze online - testy i miejsca, w których stan sieci nie ma znaczenia. */
    object AlwaysOnline : NetworkStatus {
        override val online: Flow<Boolean> = flowOf(true)
    }
}
