package pl.zse.bydgoszcz.elektron.domain.sync

import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome

object BackgroundSyncPolicy {
    fun isDue(source: String, lastSuccess: Long?, now: Long): Boolean {
        val interval = when (source) {
            SyncOutcome.SIDEBAR -> 24 * 60 * 60L
            SyncOutcome.TIMETABLE -> 6 * 60 * 60L
            SyncOutcome.ANNOUNCEMENTS -> 60 * 60L
            else -> 0L // zastępstwa przy każdym cyklicznym przebiegu
        }
        return lastSuccess == null || now < lastSuccess || now - lastSuccess >= interval
    }
}
