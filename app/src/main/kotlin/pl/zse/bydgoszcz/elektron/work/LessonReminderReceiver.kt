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
import pl.zse.bydgoszcz.elektron.domain.model.LessonReminders
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun scheduler(): LessonReminderScheduler
    fun notifications(): LocalNotificationSink
}

/** Alarm przypomnienia: pokazuje powiadomienie i ustawia następne przypomnienie. */
class LessonReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // goAsync daje ok. 10 s - dłużej system uznałby odbiornik za zawieszony.
                withTimeoutOrNull(8_000) { handle(ep, intent) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(ep: ReminderEntryPoint, intent: Intent) {
        val now = LocalDateTime.now()
        val what = intent.getStringExtra(LessonReminderScheduler.EXTRA_WHAT)
        val startMs = intent.getLongExtra(LessonReminderScheduler.EXTRA_START, 0L)
        val endMs = intent.getLongExtra(LessonReminderScheduler.EXTRA_END, 0L)
        if (what != null && startMs > 0) {
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneId.systemDefault())
            // Mocno spóźniony alarm (uśpiony telefon) - lekcja już trwa, przypomnienie nie ma sensu.
            if (now < start.plusMinutes(5)) {
                ep.notifications().postReminder(
                    id = intent.getIntExtra(LessonReminderScheduler.EXTRA_ID, 0),
                    title = LessonReminders.title(what, start, now),
                    body = intent.getStringExtra(LessonReminderScheduler.EXTRA_BODY).orEmpty(),
                    timeoutMs = (endMs - System.currentTimeMillis()).coerceAtLeast(60_000L)
                )
            }
        }
        ep.scheduler().reschedule(now)
    }
}
