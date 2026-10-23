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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LiveLessonEntryPoint {
    fun liveLessons(): LiveLessonScheduler
}

/** Granica lekcji, odświeżenie paska, "Ukryj na dziś" i odrzucenie powiadomienia na żywo (tylko własne PendingIntenty). */
class LiveLessonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val scheduler = EntryPointAccessors.fromApplication(context.applicationContext, LiveLessonEntryPoint::class.java).liveLessons()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // goAsync daje ok. 10 s.
                withTimeoutOrNull(8_000) {
                    when (intent.action) {
                        LiveLessonScheduler.ACTION_HIDE -> {
                            android.util.Log.i("LiveLesson", "Ukryj na dziś")
                            scheduler.hideToday()
                        }
                        LiveLessonScheduler.ACTION_DISMISSED -> scheduler.dismissed()
                        else -> scheduler.reschedule()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("LiveLesson", "Nie udało się obsłużyć powiadomienia na żywo", e)
            } finally {
                pending.finish()
            }
        }
    }
}
