package pl.zse.bydgoszcz.elektron.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun syncNow() {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_MANUAL, ExistingWorkPolicy.REPLACE, req)
    }

    fun ensurePeriodic() {
        // 15 min = realne minimum WorkManagera (Androida nie da się oszukać krócej bez
        // backendu wysyłającego push). UPDATE zamiast KEEP, żeby ta zmiana z 30 na 15 min
        // faktycznie dotarła też do osób, które już mają zaplanowaną starą, 30-minutową pracę.
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(androidx.work.workDataOf("background" to true))
            .setConstraints(networkConstraints())
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        const val WORK_MANUAL = "elektron_manual_sync"
        const val WORK_PERIODIC = "elektron_periodic_sync"
    }
}
