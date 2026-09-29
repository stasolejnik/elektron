package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Odświeżanie widżetów.
 *
 * Standardowy harmonogram widżetów (updatePeriodMillis) to minimum 30 min — za rzadko,
 * żeby "Teraz" / "Następna" przeskakiwało z dzwonkiem. Dlatego każdy render widżetu
 * planuje jednorazowe odświeżenie na moment, w którym stan się zmieni (koniec trwającej
 * lekcji, początek najbliższej, północ). Gdy na ekranie nie ma żadnego widżetu, Glance
 * nie wywołuje renderu i łańcuch sam wygasa.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Odśwież widżety w tle (po syncu, zmianie klasy lub grup). */
    fun requestUpdate() {
        scope.launch { updateNow() }
    }

    suspend fun updateNow() {
        WidgetDataVersion.bump()     // otwarte sesje Glance wczytają dane od nowa
        runCatching {
            NextLessonWidget().updateAll(context)
            DayPlanWidget().updateAll(context)
            SubstitutionsWidget().updateAll(context)
            NextLessonTileService.requestUpdate(context)
        }.onFailure { Log.w(TAG, "Nie udało się odświeżyć widżetów", it) }
    }

    /** Zaplanuj odświeżenie na [at] (z ograniczeniem 1 min – 3 h, zabezpieczenie przed zmianą czasu). */
    fun scheduleTick(at: LocalDateTime) {
        val delay = Duration.between(LocalDateTime.now(), at).plusSeconds(5)
            .coerceIn(Duration.ofMinutes(1), Duration.ofHours(3))
        val request = OneTimeWorkRequestBuilder<WidgetTickWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_TICK, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        private const val TAG = "WidgetUpdater"
        private const val WORK_TICK = "elektron_widget_tick"
    }
}

/** Jednorazowe odświeżenie widżetów w chwili dzwonka (planowane przez [WidgetUpdater.scheduleTick]). */
@HiltWorker
class WidgetTickWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        updater.updateNow()
        return Result.success()
    }
}
