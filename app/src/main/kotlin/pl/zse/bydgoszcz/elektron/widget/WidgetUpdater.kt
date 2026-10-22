package pl.zse.bydgoszcz.elektron.widget

import pl.zse.bydgoszcz.elektron.domain.util.AppClock

import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val tickMutex = kotlinx.coroutines.sync.Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Odśwież widżety w tle (po syncu, zmianie klasy lub grup). */
    fun requestUpdate() {
        scope.launch { updateNow() }
    }

    suspend fun requestDurableUpdate() = withContext(Dispatchers.IO) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(1, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "elektron_widget_refresh", ExistingWorkPolicy.REPLACE, request).result.get()
        Unit
    }

    suspend fun updateNow() {
        WidgetDataVersion.bump()     // otwarte sesje Glance wczytają dane od nowa
        refreshWidgetTargets(
            { NextLessonWidget().updateAll(context) },
            { DayPlanWidget().updateAll(context) },
            { SubstitutionsWidget().updateAll(context) },
            { NextLessonTileService.requestUpdate(context) }
        ).forEach { result -> result.onFailure { Log.w(TAG, "Nie udało się odświeżyć jednego z widżetów", it) } }
    }

    /**
     * Zaplanuj odświeżenie na [at] (czas aplikacji, także symulowany; ograniczenie 1 min - 24 h).
     * Dokładny alarm BEZ budzenia telefonu: przy włączonym ekranie przychodzi co do sekundy (dzwonek),
     * przy wygaszonym - zaraz po jego włączeniu, więc nie dokłada wybudzeń. Dawniej zadanie WorkManagera
     * z opóźnieniem, które system odkładał (Doze, grupy czuwania) - widżet zmieniał się po dzwonku
     * z opóźnieniem, a powiadomienie na żywo już wcześniej. Trzy widżety planują osobno; zostaje
     * najwcześniejszy termin, który jeszcze nie minął.
     */
    fun scheduleTick(at: LocalDateTime): kotlinx.coroutines.Job = scope.launch(Dispatchers.IO) {
        tickMutex.lock()
        try {
            val zone = java.time.ZoneId.systemDefault()
            val delay = Duration.between(AppClock.now().atZone(zone), at.atZone(zone)).plusSeconds(1)
                .coerceIn(Duration.ofMinutes(1), Duration.ofDays(1))
            val planned = System.currentTimeMillis() + delay.toMillis()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = prefs.getLong(KEY_TICK_AT, 0L)
            // Wcześniejszy alarm nadal czeka (jego PendingIntent istnieje) - nie przesuwamy go później.
            if (current > System.currentTimeMillis() && current <= planned && tickPending()) return@launch
            val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return@launch
            val pi = tickIntent()
            try {
                if (pl.zse.bydgoszcz.elektron.work.LessonReminderScheduler.canUseExactAlarms(context))
                    am.setExact(android.app.AlarmManager.RTC, planned, pi)
                else am.set(android.app.AlarmManager.RTC, planned, pi)
            } catch (e: SecurityException) {
                am.set(android.app.AlarmManager.RTC, planned, pi)
            }
            prefs.edit().putLong(KEY_TICK_AT, planned).apply()
            // Zadanie z poprzedniej wersji (WorkManager) - już niepotrzebne.
            if (!prefs.getBoolean(KEY_LEGACY_CANCELLED, false)) {
                runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_TICK) }
                prefs.edit().putBoolean(KEY_LEGACY_CANCELLED, true).apply()
            }
            // Odświeżenia co minutę (lekcja, przerwa) bez wpisu - w raporcie tylko dzwonki i dłuższe odstępy.
            if (delay.toMinutes() >= 2) Log.i(TAG, "Odświeżenie widżetów: ${at.toLocalTime().withNano(0)} (za ${delay.toMinutes()} min)")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Nie udało się zaplanować odświeżenia widżetów", e)
        } finally { tickMutex.unlock() }
    }

    /** Alarm odświeżenia przyszedł - następny termin zaplanują widżety po przerysowaniu. */
    internal fun onTickFired() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TICK_AT).apply()
    }

    /** Restart, aktualizacja aplikacji, zmiana czasu: system skasował alarmy - termin od nowa. */
    fun resetTicks() = onTickFired()

    private fun tickPending(): Boolean = android.app.PendingIntent.getBroadcast(
        context, TICK_REQUEST, android.content.Intent(context, WidgetTickReceiver::class.java),
        android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE) != null

    private fun tickIntent(): android.app.PendingIntent = android.app.PendingIntent.getBroadcast(
        context, TICK_REQUEST, android.content.Intent(context, WidgetTickReceiver::class.java),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

    companion object {
        private const val TAG = "WidgetUpdater"
        private const val WORK_TICK = "elektron_widget_tick"
        private const val PREFS = "widget_ticks"
        private const val KEY_TICK_AT = "tick_at"
        private const val KEY_LEGACY_CANCELLED = "legacy_cancelled"
        private const val TICK_REQUEST = 4301
    }
}

/**
 * Odświeżenie z alarmu [WidgetUpdater.scheduleTick]. Glance rysuje widżety w osobnej pracy, więc
 * odbiornik tylko zleca odświeżenie (updateAll) i od razu kończy.
 */
class WidgetTickReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val pending = goAsync()
        val updater = WidgetDataLoader.entryPoint(context).widgetUpdater()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updater.onTickFired()
                kotlinx.coroutines.withTimeoutOrNull(8_000) { runTickUpdate { updater.updateNow() } }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("WidgetUpdater", "Nie udało się odświeżyć widżetów z alarmu", e)
            } finally { pending.finish() }
        }
    }
}

/** Jednorazowe odświeżenie z poprzedniej wersji (WorkManager) - zostaje, żeby zaplanowane zadania się wykonały. */
@HiltWorker
class WidgetTickWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runTickUpdate { updater.updateNow() }
        return Result.success()
    }
}

/**
 * Odświeżenie w ticku, którego nie przerwie anulowanie workera.
 *
 * updateAll() tylko zleca sesję Glance (osobna praca WorkManagera), więc provideGlance
 * pierwszego widżetu rusza, zanim ten worker odświeży kolejne. provideGlance planuje następny
 * tick przez enqueueUniqueWork(REPLACE) pod TĄ SAMĄ nazwą - REPLACE anuluje działającego
 * workera, a pozostałe widżety (i kafelek) zostawały nieodświeżone do następnego dzwonka.
 */
internal suspend fun runTickUpdate(update: suspend () -> Unit) = withContext(NonCancellable) { update() }

/** Trwałe odświeżenie po serii pushy; nie zależy od życia usługi FCM. */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        updater.updateNow()
        return Result.success()
    }
}

/** One broken launcher widget must not prevent updating the remaining targets. */
internal suspend fun refreshWidgetTargets(vararg targets: suspend () -> Unit): List<Result<Unit>> =
    targets.map { target -> runCatchingCancellable { target() } }
