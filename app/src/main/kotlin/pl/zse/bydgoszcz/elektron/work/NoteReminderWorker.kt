package pl.zse.bydgoszcz.elektron.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class NoteReminderWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted parameters: WorkerParameters,
    private val scheduler: NoteReminderScheduler
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        inputData.getString("key")?.let { scheduler.deliver(it, inputData.getLong("revision", -1)) }
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
}
