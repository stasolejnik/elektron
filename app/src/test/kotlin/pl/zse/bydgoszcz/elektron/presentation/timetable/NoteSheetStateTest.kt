package pl.zse.bydgoszcz.elektron.presentation.timetable

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composition
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NoteSheetStateTest {
    private class NoViews : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    @Test fun editsKeepTheSheetAndReadCurrentDismissPolicy() = runTest {
        val dirty = mutableStateOf(false)
        val saving = mutableStateOf(false)
        var discarded = 0
        var sheet: SheetState? = null
        var confirm: ((SheetValue) -> Boolean)? = null
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val runner = launch(UnconfinedTestDispatcher(testScheduler) + clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(NoViews(), recomposer)
        try {
            composition.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    sheet = rememberNoteSheetState(dirty.value, saving.value) { discarded++ }
                    confirm = rememberNoteSheetConfirmation(dirty.value, saving.value) { discarded++ }
                }
            }
            val original = sheet!!
            val originalConfirm = confirm!!
            fun recompose() {
                Snapshot.sendApplyNotifications()
                runCurrent()
                clock.sendFrame(0L)
                runCurrent()
                assertSame("Typing must not restart the sheet's opening animation", original, sheet)
                assertSame(originalConfirm, confirm)
            }
            dirty.value = true
            recompose()
            assertFalse(confirm!!(SheetValue.Hidden))
            assertEquals(1, discarded)
            saving.value = true
            recompose()
            assertFalse(confirm!!(SheetValue.Hidden))
            assertEquals("Saving blocks dismissal without a discard dialog", 1, discarded)
            dirty.value = false
            saving.value = false
            recompose()
            assertTrue(confirm!!(SheetValue.Hidden))
            assertEquals("Clean notes dismiss without a discard dialog", 1, discarded)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.cancel()
        }
    }
}
