package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
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
 * Aktualna data i godzina dla UI ("zostało X min", "Za X min"), odświeżane równo z pełną
 * [periodMs] - i OD RAZU przy każdym powrocie na ekran (STARTED). Dawniej zegar tykał tylko co
 * 30 s: po powrocie do aplikacji, która została w tle na planie, widniał stary czas.
 * W tle nie tyka (oszczędność baterii).
 */
@androidx.compose.runtime.Composable
fun rememberNow(periodMs: Long = 30_000L): androidx.compose.runtime.State<java.time.LocalDateTime> {
    val visible = LocalScreenVisible.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    return androidx.compose.runtime.produceState(java.time.LocalDateTime.now(), owner, periodMs, visible) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            if (visible) while (true) {
                value = java.time.LocalDateTime.now()
                delay(periodMs - System.currentTimeMillis() % periodMs)
            }
        }
    }
}

/** Sąsiednie strony pagera przygotowują dane, ale nie potrzebują tykającego zegara. */
val LocalScreenVisible = androidx.compose.runtime.staticCompositionLocalOf { true }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun visibleMinuteTicker(visible: Flow<Boolean>): Flow<Unit> =
    visible.flatMapLatest { if (it) minuteTicker() else kotlinx.coroutines.flow.flowOf(Unit) }
