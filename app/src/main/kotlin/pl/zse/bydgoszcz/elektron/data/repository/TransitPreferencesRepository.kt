package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import pl.zse.bydgoszcz.elektron.domain.model.MoreMenu
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences
import javax.inject.Inject
import javax.inject.Singleton

// Uszkodzony plik (np. przerwany zapis przy pełnej pamięci) - domyślne zamiast trwale wyłączonych
// Odjazdów i ustawień paska nawigacji (ten sam plik), które naprawiało tylko czyszczenie danych.
private val Context.transitPreferences by preferencesDataStore(
    name = "school_departures",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { e ->
        android.util.Log.w("TransitPreferences", "Uszkodzony plik ustawień Odjazdów - przywracam domyślne", e)
        emptyPreferences()
    }
)

@Singleton
class TransitPreferencesRepository internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.transitPreferences)
    private val destinationKey = stringPreferencesKey("destination")
    private val originKey = stringPreferencesKey("preferred_origin")
    private val transfersKey = booleanPreferencesKey("allow_transfers")
    private val nearbyKey = booleanPreferencesKey("nearby_school_stops")
    private val arrivalMarginKey = androidx.datastore.preferences.core.intPreferencesKey("arrival_margin_minutes")
    private val recentDestinationsKey = stringPreferencesKey("recent_destinations")
    private val recentOriginsKey = stringPreferencesKey("recent_origins")
    private val hiddenTabsKey = stringSetPreferencesKey("hidden_navigation_tabs")
    private val dashboardKey = booleanPreferencesKey("show_dashboard_departure")
    private val enabledKey = booleanPreferencesKey("show_tab")
    private val orderKey = stringPreferencesKey("navigation_order_v1")
    // "Więcej": włączone i zawartość panelu; miejsce przycisku jako "more" w navigation_order_v1
    // (starsza wersja aplikacji ten znacznik pomija).
    private val moreEnabledKey = booleanPreferencesKey("more_enabled")
    private val moreContentKey = stringPreferencesKey("more_content")
    val preferences: Flow<TransitPreferences> = store.data.map {
        val destination = decode(it[destinationKey])
        val origin = decode(it[originKey])
        TransitPreferences(destination, it[enabledKey] ?: false, origin,
            it[transfersKey] == true, history(it[recentDestinationsKey], destination), history(it[recentOriginsKey], origin), it[hiddenTabsKey].orEmpty(), it[dashboardKey] ?: false,
            pl.zse.bydgoszcz.elektron.domain.model.NavigationOrder.normalize(it[orderKey]?.split(',').orEmpty()),
            navigationSlots = MoreMenu.slots(it[orderKey]?.split(',').orEmpty(), it[moreEnabledKey] == true),
            moreContent = MoreMenu.content(it[moreContentKey]?.split(',').orEmpty(), it[moreEnabledKey] == true),
            moreEnabled = it[moreEnabledKey] == true,
            nearbyStops = it[nearbyKey] == true,
            arrivalMarginMinutes = it[arrivalMarginKey]?.takeIf { m -> m in TransitPreferences.ARRIVAL_MARGINS })
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun select(destination: TransitDestination) = withContext(Dispatchers.IO) {
        store.edit {
            val previous = history(it[recentDestinationsKey], decode(it[destinationKey]))
            it[destinationKey] = encode(destination).toString()
            it[recentDestinationsKey] = encodeHistory(pl.zse.bydgoszcz.elektron.domain.model.rememberTransitStop(previous, destination))
        }; Unit
    }
    suspend fun selectOrigin(origin: TransitDestination?) = withContext(Dispatchers.IO) {
        store.edit {
            val previous = history(it[recentOriginsKey], decode(it[originKey]))
            if (origin == null) {
                it[recentOriginsKey] = encodeHistory(previous)
                it.remove(originKey)
            } else {
                it[originKey] = encode(origin).toString()
                it[recentOriginsKey] = encodeHistory(pl.zse.bydgoszcz.elektron.domain.model.rememberTransitStop(previous, origin))
            }
        }; Unit
    }
    suspend fun setAllowTransfers(allow: Boolean) = withContext(Dispatchers.IO) {
        store.edit { it[transfersKey] = allow }; Unit
    }
    /** null - od najbliższego kursu; inaczej jedna z [TransitPreferences.ARRIVAL_MARGINS]. */
    suspend fun setArrivalMargin(minutes: Int?) = withContext(Dispatchers.IO) {
        require(minutes == null || minutes in TransitPreferences.ARRIVAL_MARGINS)
        store.edit { if (minutes == null) it.remove(arrivalMarginKey) else it[arrivalMarginKey] = minutes }; Unit
    }
    suspend fun setNearbyStops(enabled: Boolean) = withContext(Dispatchers.IO) {
        store.edit { it[nearbyKey] = enabled }; Unit
    }
    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        store.edit {
            it[enabledKey] = enabled
        }; Unit
    }
    suspend fun setTabVisible(route: String, visible: Boolean) {
        require(route in setOf("dashboard", "timetable", "substitutions", "transit", "announcements"))
        if (route == "transit") { setEnabled(visible); return }
        withContext(Dispatchers.IO) {
            store.edit { prefs ->
                val hidden = prefs[hiddenTabsKey].orEmpty()
                prefs[hiddenTabsKey] = if (visible) hidden - route else hidden + route
            }
        }
    }
    suspend fun setShowOnDashboard(show: Boolean) = withContext(Dispatchers.IO) {
        store.edit { it[dashboardKey] = show }; Unit
    }
    /**
     * Świeża instalacja: zakładka i karta Przejazdów oraz "Więcej" włączone. Tylko klucze, których
     * jeszcze nie ma - istniejącym użytkownikom aktualizacja nie przestawia pasku.
     */
    suspend fun applyNewInstallDefaults() = withContext(Dispatchers.IO) {
        store.edit { prefs ->
            if (enabledKey !in prefs) prefs[enabledKey] = true
            if (dashboardKey !in prefs) prefs[dashboardKey] = true
            if (moreEnabledKey !in prefs) {
                val (order, content) = MoreMenu.enable(prefs[orderKey]?.split(',').orEmpty())
                prefs[orderKey] = order.joinToString(",")
                prefs[moreContentKey] = content.joinToString(",")
                prefs[moreEnabledKey] = true
            }
        }; Unit
    }

    /** Włącza "Więcej" (na prawym końcu z [MoreMenu.DEFAULT_CONTENT]) albo wyłącza (zakładki wracają na pasek). */
    suspend fun setMoreEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        store.edit { prefs ->
            val saved = prefs[orderKey]?.split(',').orEmpty()
            val wasEnabled = prefs[moreEnabledKey] == true
            if (enabled == wasEnabled) return@edit
            if (enabled) {
                val (order, content) = MoreMenu.enable(saved)
                prefs[orderKey] = order.joinToString(",")
                prefs[moreContentKey] = content.joinToString(",")
            } else {
                val slots = MoreMenu.slots(saved, true)
                val content = MoreMenu.content(prefs[moreContentKey]?.split(',').orEmpty(), true)
                prefs[orderKey] = MoreMenu.disable(slots, content).joinToString(",")
                prefs.remove(moreContentKey)
            }
            prefs[moreEnabledKey] = enabled
        }; Unit
    }

    /** Układ po przeciągnięciu z "Więcej": [bar] - przyciski pasku (z "Więcej"), [content] - panel. */
    suspend fun setNavigationLayout(bar: List<String>, content: List<String>) = withContext(Dispatchers.IO) {
        store.edit { prefs ->
            if (prefs[moreEnabledKey] != true) {
                prefs[orderKey] = pl.zse.bydgoszcz.elektron.domain.model.NavigationOrder.reorderVisible(
                    prefs[orderKey]?.split(',').orEmpty(), bar).joinToString(",")
                return@edit
            }
            val slots = MoreMenu.slots(prefs[orderKey]?.split(',').orEmpty(), true)
            val (order, panel) = MoreMenu.commit(slots, bar, content)
            prefs[orderKey] = order.joinToString(",")
            prefs[moreContentKey] = panel.joinToString(",")
        }; Unit
    }

    suspend fun setNavigationOrder(visibleOrder: List<String>) = withContext(Dispatchers.IO) {
        store.edit { prefs ->
            val merged = pl.zse.bydgoszcz.elektron.domain.model.NavigationOrder.reorderVisible(
                prefs[orderKey]?.split(',').orEmpty(), visibleOrder)
            prefs[orderKey] = merged.joinToString(",")
        }; Unit
    }
    /**
     * Ten sam przystanek z odświeżonego katalogu (inne numery stanowisk) w miejsce zapisanego - celu
     * lub przystanku początkowego - i w historii; kolejność historii bez zmian (to nie nowy wybór).
     * [replacements]: klucz zapisanego przystanku -> nowy. Przystanek zmieniony w międzyczasie przez
     * użytkownika (inny klucz) zostaje.
     */
    suspend fun refreshStops(replacements: Map<String, TransitDestination>) = withContext(Dispatchers.IO) {
        if (replacements.isEmpty()) return@withContext
        store.edit { prefs ->
            fun replace(currentKey: Preferences.Key<String>, recentKey: Preferences.Key<String>) {
                val old = decode(prefs[currentKey])
                old?.let { replacements[it.key] }?.let { prefs[currentKey] = encode(it).toString() }
                val history = history(prefs[recentKey], old)
                if (history.any { it.key in replacements }) {
                    prefs[recentKey] = encodeHistory(history.map { replacements[it.key] ?: it }.distinctBy { it.key })
                }
            }
            replace(destinationKey, recentDestinationsKey)
            replace(originKey, recentOriginsKey)
        }; Unit
    }
    /** Usuwa przystanek z ostatnich ([origin] - przy szkole, inaczej Twoje); wybrany przystanek zostaje wybrany. */
    suspend fun removeRecent(origin: Boolean, key: String) = withContext(Dispatchers.IO) {
        store.edit { prefs ->
            val recentKey = if (origin) recentOriginsKey else recentDestinationsKey
            val current = decode(prefs[if (origin) originKey else destinationKey])
            prefs[recentKey] = encodeHistory(history(prefs[recentKey], current).filterNot { it.key == key })
        }; Unit
    }
    suspend fun clearDestination() = withContext(Dispatchers.IO) {
        store.edit {
            it[recentDestinationsKey] = encodeHistory(history(it[recentDestinationsKey], decode(it[destinationKey])))
            it.remove(destinationKey)
        }; Unit
    }
    // Bez zapisanej historii (starsza wersja) - sam wybrany przystanek; zapisana pusta (po usunięciu) zostaje pusta.
    private fun history(raw: String?, current: TransitDestination?) = if (raw == null) listOfNotNull(current) else decodeHistory(raw)
    private fun encode(stop: TransitDestination) = JSONObject().put("name", stop.name)
        .put("ids", JSONArray(stop.stopIds)).put("lat", stop.latitude).put("lon", stop.longitude)
    private fun encodeHistory(stops: List<TransitDestination>) = JSONArray().apply { stops.forEach { put(encode(it)) } }.toString()
    private fun decodeHistory(raw: String?): List<TransitDestination> = runCatching {
        val array = JSONArray(raw ?: return emptyList())
        (0 until minOf(array.length(), 5)).mapNotNull { decode(array.optJSONObject(it)?.toString()) }.distinctBy { it.key }
    }.getOrDefault(emptyList())
    private fun decode(raw: String?): TransitDestination? = runCatching {
        val obj = JSONObject(raw ?: return null)
        val ids = obj.getJSONArray("ids")
        TransitDestination(obj.getString("name"), (0 until ids.length()).map { ids.getString(it) }, obj.getDouble("lat"), obj.getDouble("lon"))
    }.getOrNull()
}
