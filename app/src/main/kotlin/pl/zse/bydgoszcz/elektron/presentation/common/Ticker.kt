package pl.zse.bydgoszcz.elektron.presentation.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow

/**
 * Emituje co [periodMs] — do przeliczania stanu zależnego od aktualnej godziny
 * ("Trwa teraz", "Za X min", ukrywanie minionych zastępstw). Bez tego stan liczył się
 * tylko przy zmianie danych w bazie i przy otwartym ekranie stał w miejscu.
 * Używany w combine() z SharingStarted.WhileSubscribed — nie tyka, gdy ekran niewidoczny.
 */
fun minuteTicker(periodMs: Long = 30_000L): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
    }
}

/**
 * Bieżąca data — emituje nową wartość po północy. ViewModele sekcji żyją przez cały czas
 * działania aplikacji (sekcje są stronami pagera), więc zakresy dat liczone raz przy
 * utworzeniu ("od dziś do +21 dni") po kilku dniach w pamięci byłyby nieaktualne.
 */
fun currentDateFlow(): Flow<java.time.LocalDate> =
    minuteTicker(60_000L).map { java.time.LocalDate.now() }.distinctUntilChanged()
