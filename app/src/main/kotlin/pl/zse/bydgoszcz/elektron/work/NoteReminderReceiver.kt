package pl.zse.bydgoszcz.elektron.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Alarm przypomnienia o notatce: to samo dostarczenie co w NoteReminderWorker (który zostaje zapasem). */
class NoteReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val revision = intent.getLongExtra(EXTRA_REVISION, -1)
        val pending = goAsync()
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, SystemEventsEntryPoint::class.java)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // goAsync daje ok. 10 s. Nieudane dostarczenie ponowi zlecenie WorkManagera.
                withTimeoutOrNull(8_000) { ep.noteScheduler().deliver(key, revision) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("NoteReminders", "Alarm: nie udało się dostarczyć przypomnienia notatki", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_KEY = "note_key"
        const val EXTRA_REVISION = "note_revision"
    }
}
