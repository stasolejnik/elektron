package pl.zse.bydgoszcz.elektron.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SystemEventsEntryPoint {
    fun scheduler(): LessonReminderScheduler
    fun widgetUpdater(): WidgetUpdater
}

/**
 * Restart telefonu, aktualizacja aplikacji (także z przycisku "Aktualizuj"), zmiana czasu lub
 * strefy: Android kasuje alarmy / zmienia się "teraz" - od razu ustawiamy przypomnienie
 * o najbliższej lekcji i odświeżamy widżety. Dawniej dopiero przy najbliższym syncu (do 15 min),
 * więc przypomnienie tuż po aktualizacji mogło przepaść.
 */
class SystemEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, SystemEventsEntryPoint::class.java)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // goAsync daje ok. 10 s.
                withTimeoutOrNull(8_000) {
                    ep.scheduler().reschedule()
                    ep.widgetUpdater().requestUpdate()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
