package pl.zse.bydgoszcz.elektron.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import pl.zse.bydgoszcz.elektron.domain.model.SyncErrors
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.sync.SyncCoordinator
import pl.zse.bydgoszcz.elektron.domain.sync.SyncRequest

/**
 * Cykliczna synchronizacja w tle (co 15 min). Całą pracę - pobieranie, komunikaty, powiadomienia,
 * przypomnienia, widżety - wykonuje SyncCoordinator; tu tylko polityka ponowień WorkManagera.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: SyncCoordinator,
    private val notificationsRepo: NotificationsRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        try {
            val outcome = coordinator.sync(SyncRequest.full(background = inputData.getBoolean("background", false)))
            // Przerwane przez zmianę klasy / reset: dane pobiera właśnie synchronizacja nowej klasy.
            if (outcome.interrupted) return Result.success()
            if (outcome.succeeded.isEmpty()) {
                // Bug audytu #9: bez limitu Result.retry() ponawiał się w nieskończoność
                // przy dłuższej awarii strony szkoły. Po MAX_RETRY_ATTEMPTS oddajemy
                // kontrolę cyklicznemu harmonogramowi (za 15 min) zamiast dobijać się dalej.
                return if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
            }
            notificationsRepo.pruneHistory()
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "SyncWorker wyjątek", e)
            notificationsRepo.setLastSyncError(SyncErrors.userMessage(e))
            return if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 5
    }
}
