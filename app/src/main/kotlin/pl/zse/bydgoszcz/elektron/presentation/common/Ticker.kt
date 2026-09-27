package pl.zse.bydgoszcz.elektron.presentation.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
