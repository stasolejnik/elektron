package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetScheduleTest {
    @Test fun laterWidgetCannotPostponeAnEarlierRefresh() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val alarms = org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.AlarmManager::class.java))
        val updater = WidgetUpdater(app)
        updater.resetTicks()
        fun next() = alarms.scheduledAlarms.filter { it.operation?.let { op -> org.robolectric.Shadows.shadowOf(op).savedIntent.component?.className } ==
            WidgetTickReceiver::class.java.name }.minOf { it.triggerAtTime }
        updater.scheduleTick(LocalDateTime.now().plusMinutes(3)).join()
        val earliest = next()
        updater.scheduleTick(LocalDateTime.now().plusHours(2)).join()
        assertEquals("późniejszy widżet nie przesuwa wcześniejszego odświeżenia", earliest, next())
        updater.scheduleTick(LocalDateTime.now().plusMinutes(1)).join()
        assertTrue(next() < earliest)
        // Alarm bez budzenia telefonu.
        assertTrue(alarms.scheduledAlarms.all { it.type == android.app.AlarmManager.RTC })
        // Po restarcie / aktualizacji (alarmy skasowane) nowy termin jest ustawiany od razu.
        updater.resetTicks()
        updater.scheduleTick(LocalDateTime.now().plusHours(2)).join()
        assertTrue(next() > earliest)
    }
}
