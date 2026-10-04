package pl.zse.bydgoszcz.elektron.domain.sync

import org.junit.Assert.*
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome

class BackgroundSyncPolicyTest {
    @Test fun firstRunAndClockRollbackAlwaysFetch() {
        for (source in SyncRequest.ALL_SOURCES) {
            assertTrue(BackgroundSyncPolicy.isDue(source, null, 100))
            assertTrue(BackgroundSyncPolicy.isDue(source, 200, 100))
        }
    }
    @Test fun onlySubstitutionsAreDueAfterFifteenMinutes() {
        val due = SyncRequest.ALL_SOURCES.filter { BackgroundSyncPolicy.isDue(it, 100, 100 + 900) }
        assertEquals(listOf(SyncOutcome.SUBSTITUTIONS), due)
    }
    @Test fun deadlinesAreInclusive() {
        for ((source, interval) in mapOf(SyncOutcome.SIDEBAR to 86400L,
            SyncOutcome.TIMETABLE to 21600L, SyncOutcome.ANNOUNCEMENTS to 3600L)) {
            assertFalse(BackgroundSyncPolicy.isDue(source, 100, 100 + interval - 1))
            assertTrue(BackgroundSyncPolicy.isDue(source, 100, 100 + interval))
        }
    }
}
