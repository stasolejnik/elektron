package pl.zse.bydgoszcz.elektron.domain.util

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CancellableCatchingTest {

    @Test
    fun catchesOrdinaryErrors() {
        val r = runCatchingCancellable<Int> { throw java.io.IOException("x") }
        assertTrue(r.exceptionOrNull() is java.io.IOException)
        assertEquals(5, runCatchingCancellable { 5 }.getOrThrow())
    }

    @Test
    fun rethrowsCancellation() {
        // Zwykłe runCatching zamieniało anulowanie w "porażkę" i kod liczył dalej.
        try {
            runCatchingCancellable<Unit> { throw CancellationException("anulowano") }
            fail("Anulowanie musi lecieć dalej")
        } catch (_: CancellationException) {
        }
    }
}
