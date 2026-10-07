package pl.zse.bydgoszcz.elektron.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WidgetRefreshFailureTest {
    @Test fun failureOfFirstWidgetDoesNotBlockOtherWidgetsOrTile() = runTest {
        val calls = mutableListOf<String>()
        val results = refreshWidgetTargets(
            { calls += "next"; throw java.io.IOException("Uszkodzona sesja") },
            { calls += "day" }, { calls += "subs" }, { calls += "tile" }
        )
        assertEquals(listOf("next", "day", "subs", "tile"), calls)
        assertTrue(results.first().isFailure)
        assertTrue(results.drop(1).all { it.isSuccess })
    }
    @Test fun cancellationStillStopsRefreshInsteadOfBeingHiddenAsFailure() = runTest {
        var laterCalled = false
        try {
            refreshWidgetTargets({ throw CancellationException("Zamknięcie") }, { laterCalled = true })
            fail("Anulowanie musi być propagowane")
        } catch (_: CancellationException) { }
        assertFalse(laterCalled)
    }
}
