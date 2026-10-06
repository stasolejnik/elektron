package pl.zse.bydgoszcz.elektron.presentation.timetable

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedWeekCacheTest {
    @Test fun threeDaysShareOneSubscriptionAndEvictionKeepsOnlyActiveWork() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var active = 0
        var starts = 0
        val cache = SharedWeekCache<Int, Int>(scope, 2) { key -> flow {
            active++; starts++
            try { emit(key); awaitCancellation() } finally { active-- }
        } }
        val original = cache.get(1)
        val jobs = List(3) { launch(UnconfinedTestDispatcher(testScheduler)) { original.collect() } }
        try {
            runCurrent()
            assertEquals(1, active)
            assertEquals(1, starts)
            cache.get(2); cache.get(3) // week 1 is evicted, but its visible pages remain live
            runCurrent()
            assertEquals(1, active)
            jobs.forEach { it.cancel() }
            runCurrent()
            assertEquals(0, active)
            val restored = async(UnconfinedTestDispatcher(testScheduler)) { original.first() }
            runCurrent()
            assertEquals(1, restored.await())
            runCurrent()
            assertEquals(0, active)
            assertEquals(2, starts)
        } finally { jobs.forEach { it.cancel() }; scope.cancel() }
    }
}
