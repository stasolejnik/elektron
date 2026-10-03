package pl.zse.bydgoszcz.elektron.domain.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Jak [runCatching], ale anulowanie korutyny ([CancellationException]) leci dalej.
 *
 * Zwykłe runCatching w kodzie suspend łapało też anulowanie: anulowany sync (zamknięty ekran,
 * zatrzymany worker) kończył się "porażką" z komunikatem błędu albo liczył dalej, zamiast
 * się zatrzymać. Do kodu wywołującego funkcje suspend używaj tej wersji.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
