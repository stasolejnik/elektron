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

/**
 * Aktualna data i godzina dla UI, odświeżane równo z pełną [periodMs] (domyślnie co 30 s) -
 * np. "zostało X min" przy trwającej lekcji w planie.
 */
@androidx.compose.runtime.Composable
fun rememberNow(periodMs: Long = 30_000L): androidx.compose.runtime.State<java.time.LocalDateTime> =
    androidx.compose.runtime.produceState(java.time.LocalDateTime.now(), periodMs) {
        while (true) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
            value = java.time.LocalDateTime.now()
        }
    }
