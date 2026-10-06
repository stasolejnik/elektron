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
        val wm = runCatching { WorkManager.getInstance(app) }.getOrElse {
            WorkManager.initialize(app, Configuration.Builder().build())
            WorkManager.getInstance(app)
        }
        val updater = WidgetUpdater(app)
        fun pending() = wm.getWorkInfosForUniqueWork("elektron_widget_tick").get().single { !it.state.isFinished }
        updater.scheduleTick(LocalDateTime.now().plusMinutes(3)).join()
        val earliest = pending().id
        updater.scheduleTick(LocalDateTime.now().plusHours(2)).join()
        assertEquals(earliest, pending().id)
        updater.scheduleTick(LocalDateTime.now().plusMinutes(1)).join()
        assertNotEquals(earliest, pending().id)
        wm.cancelUniqueWork("elektron_widget_tick").result.get()
    }
}
