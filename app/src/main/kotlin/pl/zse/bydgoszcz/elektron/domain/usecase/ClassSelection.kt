package pl.zse.bydgoszcz.elektron.domain.usecase

import pl.zse.bydgoszcz.elektron.domain.sync.SyncCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wybór klasy + pierwszy sync jej danych (Setup i Ustawienia). Praca działa w zasięgu
 * SyncCoordinator - przeżywa przejście z Ustawień na ekran grup, przerywa trwające
 * synchronizacje i nie wchodzi z wyczyszczeniem bazy w środek innej pracy.
 */
@Singleton
class ClassSelection @Inject constructor(
    private val coordinator: SyncCoordinator
) {
    /** Wybiera klasę i synchronizuje jej dane. Szybka ponowna zmiana przerywa poprzedni sync. */
    fun select(classId: String) = coordinator.selectClass(classId)

    /**
     * Czyści lokalną bazę (cache ze strony szkoły + stan synchronizacji) i pobiera dane
     * od nowa. Ustawienia (DataStore: klasa, grupy, motyw) zostają. Wyczyszczony stan
     * synchronizacji oznacza, że pierwszy sync tylko go odbuduje — bez lawiny powiadomień.
     */
    fun resetCacheAndResync(classId: String) = coordinator.resetCacheAndResync(classId)
}
