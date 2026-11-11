package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.zse.bydgoszcz.elektron.data.repository.TransitJourneyRepository
import pl.zse.bydgoszcz.elektron.data.repository.TransitPreferencesRepository
import pl.zse.bydgoszcz.elektron.data.repository.TransitSchoolDay
import pl.zse.bydgoszcz.elektron.domain.model.TransitCommute
import pl.zse.bydgoszcz.elektron.domain.model.TransitDirection
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourneys
import pl.zse.bydgoszcz.elektron.domain.util.AppClock
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Odświeżanie kursów dla widżetu "Przejazdy" bez otwierania aplikacji. Planer pytany jest tylko wtedy,
 * gdy widżet jest na ekranie, sam (bez dotknięcia ↻) w ciągu dnia ([TransitWidgetData.autoRefresh]) i tylko,
 * gdy zapisany wynik nie nadaje się już do użycia (10 min, inny kierunek, za mało kursów): w porze dojazdu
 * najwyżej co [MIN_INTERVAL_MS], poza nią najwyżej co [IDLE_INTERVAL_MS]. Nocą - ostatni wynik albo dotknięcie.
 */
@Singleton
class TransitWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: TransitPreferencesRepository,
    private val journeys: TransitJourneyRepository,
    private val schoolDay: TransitSchoolDay,
    private val updater: WidgetUpdater
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** W tle; po nowym wyniku widżet przerysowuje się sam (zapis wyniku - patrz [TransitJourneyRepository.saved]). */
    fun refreshInBackground(force: Boolean = false) {
        scope.launch { refresh(force) }
    }

    /**
     * Stan dla widżetu: sprawdzanie trwa (najwyżej [REFRESHING_MAX_MS] - po przerwanym procesie znacznik wygasa)
     * albo ostatnie sprawdzenie się nie udało i nie ma nowszego wyniku ([resultFetchedAt], także z aplikacji).
     */
    internal fun status(resultFetchedAt: Long?): TransitWidgetData.Status {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = store.getLong(KEY_REFRESHING_SINCE, 0L)
        val failedAt = store.getLong(KEY_FAILED_AT, 0L)
        return TransitWidgetData.Status(refreshing = since > 0L && now - since in 0 until REFRESHING_MAX_MS,
            failed = failedAt > 0L && failedAt > (resultFetchedAt ?: 0L))
    }

    /** true - zapisano nowy wynik. [force] - dotknięcie ikony odświeżania: także poza porą dojazdu i bez czekania na świeżość. */
    suspend fun refresh(force: Boolean): Boolean = mutex.withLock {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatchingCancellable { refreshLocked(force) }
            .onFailure {
                Log.w(TAG, "Nie udało się odświeżyć Przejazdów dla widżetu", it)
                store.edit().putLong(KEY_FAILED_AT, System.currentTimeMillis()).apply()
            }
            .onSuccess { saved -> if (saved) store.edit().remove(KEY_FAILED_AT).apply() }
            .also {
                // Koniec sprawdzania (także nieudanego) - widżet bez "sprawdzam…".
                if (store.contains(KEY_REFRESHING_SINCE)) { store.edit().remove(KEY_REFRESHING_SINCE).apply(); updater.updateTransit() }
            }
            .getOrDefault(false)
    }

    private suspend fun refreshLocked(force: Boolean): Boolean {
        if (!hasWidget()) return false
        val prefs = preferences.preferences.first()
        val destination = prefs.destination ?: return false
        if (!prefs.canLoadJourneys) return false
        val lessons = schoolDay.lessons.first()
        val now = AppClock.now()
        val nowMs = AppClock.millis()
        if (!force && !TransitWidgetData.autoRefresh(lessons, now)) return false
        val interval = if (TransitCommute.dashboardDirection(lessons, now) != null) MIN_INTERVAL_MS else IDLE_INTERVAL_MS
        val direction = TransitWidgetData.direction(lessons, now)
        val toSchool = direction == TransitDirection.TO_SCHOOL
        val zone = ZoneId.systemDefault()
        val target = TransitCommute.target(lessons, now)
        val fromMs = if (toSchool) TransitCommute.searchFrom(target?.start, now).atZone(zone).toInstant().toEpochMilli() / 60_000L * 60_000L else null
        val cached = journeys.loadLast(nowMs)
        // Świeży wynik, w którym zostało dość kursów, do których da się zdążyć - bez zapytania. Ze szkoły kursy
        // z dalszych przystanków szybko odpadają (dojście 10-17 min), więc przy mniej niż [MIN_SHOWN] - od nowa.
        val fresh = cached?.let { TransitWidgetData.matches(it, prefs, direction) &&
            it.canReuse(destination.key, nowMs, prefs.originKey, prefs.allowTransfers, direction, fromMs) &&
            TransitWidgetData.visible(it.journeys, prefs, toSchool, nowMs).size >= MIN_SHOWN } == true
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = store.getLong(KEY_LAST_FETCH, 0L)
        val sinceLast = System.currentTimeMillis() - last
        if (!force && (fresh || sinceLast in 0 until interval)) return false
        // Dotknięcie ↻ kilka razy pod rząd - jedno zapytanie.
        if (force && sinceLast in 0 until FORCE_INTERVAL_MS) return false
        // "sprawdzam…" w widżecie od razu, zanim odpowie planer (kilka sekund przy wielu stanowiskach).
        store.edit().putLong(KEY_LAST_FETCH, System.currentTimeMillis()).putLong(KEY_REFRESHING_SINCE, System.currentTimeMillis()).apply()
        updater.updateTransit()
        val origin = prefs.preferredOrigin
        var result = if (toSchool) journeys.findToSchool(destination, fromMs!!, null, prefs.allowTransfers, origin, prefs.nearbyStops)
            else journeys.find(destination, origin, null, prefs.allowTransfers, null, null, prefs.nearbyStops)
        // Jedna strona planera bywa krótka - najwyżej trzy kolejne, aż będzie tyle kursów (z dojściem), ile mieści widżet.
        repeat(3) {
            val shown = TransitWidgetData.visible(result.journeys, prefs, toSchool, nowMs).size
            val next = result.nextWhenMs ?: return@repeat
            if (shown >= TransitWidgetData.MAX_ROWS) return@repeat
            if (toSchool && next > TransitCommute.searchEndMs(target?.start?.atZone(zone)?.toInstant()?.toEpochMilli(), fromMs!!)) return@repeat
            val page = if (toSchool) journeys.findToSchool(destination, fromMs!!, next, prefs.allowTransfers, origin, prefs.nearbyStops)
                else journeys.find(destination, origin, next, prefs.allowTransfers, result.platformCursors, null, prefs.nearbyStops)
            result = page.copy(
                journeys = if (toSchool) TransitJourneys.rankToSchool(result.journeys + page.journeys, nowMs, prefs.allowTransfers)
                    else TransitJourneys.rank(result.journeys + page.journeys, nowMs, prefs.strictOrigin, prefs.allowTransfers),
                boardingStops = (result.boardingStops + page.boardingStops).distinctBy { it.key },
                partialFailure = result.partialFailure || page.partialFailure,
                complete = result.complete && page.complete,
                searchFromMs = result.searchFromMs)
        }
        if (!TransitWidgetData.matches(result, preferences.preferences.first(), direction)) return false
        journeys.saveLast(result)
        Log.i(TAG, "Przejazdy dla widżetu: ${result.journeys.size} kursów (${if (toSchool) "do szkoły" else "ze szkoły"}${if (force) ", ↻" else ""})")
        return true
    }

    private suspend fun hasWidget(): Boolean = runCatchingCancellable {
        GlanceAppWidgetManager(context).getGlanceIds(TransitWidget::class.java).isNotEmpty()
    }.getOrDefault(false)

    companion object {
        private const val TAG = "TransitWidget"
        private const val PREFS = "transit_widget"
        private const val KEY_LAST_FETCH = "last_fetch"
        private const val KEY_REFRESHING_SINCE = "refreshing_since"
        private const val KEY_FAILED_AT = "failed_at"
        private const val REFRESHING_MAX_MS = 90_000L
        /** Odświeżenie w tle najwyżej co tyle (wynik i tak jest świeży 10 min). */
        const val MIN_INTERVAL_MS = 5 * 60_000L
        /** Poza porą dojazdu (w ciągu dnia) - rzadziej. */
        const val IDLE_INTERVAL_MS = 15 * 60_000L
        /** Mniej kursów do pokazania - odświeżenie przed upływem 10 min. */
        private const val MIN_SHOWN = 3
        private const val FORCE_INTERVAL_MS = 20_000L
    }
}

/** Dotknięcie ↻ w widżecie: sprawdzenie od razu, także poza porą dojazdu. */
class TransitWidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ep = WidgetDataLoader.entryPoint(context)
        // Nowy wynik przerysowuje widżet sam (zapis -> ElektronApplication); bez niego - stan od nowa (np. "Sprawdzono").
        if (!ep.transitWidgetRefresher().refresh(force = true)) ep.widgetUpdater().updateTransit()
    }
}
