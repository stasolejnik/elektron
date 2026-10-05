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
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences
import javax.inject.Inject
import javax.inject.Singleton

private val Context.transitPreferences by preferencesDataStore(name = "school_departures")

@Singleton
class TransitPreferencesRepository internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.transitPreferences)
    private val destinationKey = stringPreferencesKey("destination")
    private val originKey = stringPreferencesKey("preferred_origin")
    private val transfersKey = booleanPreferencesKey("allow_transfers")
    private val recentDestinationsKey = stringPreferencesKey("recent_destinations")
    private val recentOriginsKey = stringPreferencesKey("recent_origins")
    private val enabledKey = booleanPreferencesKey("enabled")
    val preferences: Flow<TransitPreferences> = store.data.map {
        val destination = decode(it[destinationKey])
        val origin = decode(it[originKey])
        TransitPreferences(destination, destination != null && it[enabledKey] == true, origin,
            it[transfersKey] == true, history(it[recentDestinationsKey], destination), history(it[recentOriginsKey], origin))
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
    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        store.edit {
            require(!enabled || decode(it[destinationKey]) != null) { "Najpierw wybierz przystanek docelowy." }
            it[enabledKey] = enabled
        }; Unit
    }
    suspend fun clearDestination() = withContext(Dispatchers.IO) {
        store.edit {
            it[recentDestinationsKey] = encodeHistory(history(it[recentDestinationsKey], decode(it[destinationKey])))
            it.remove(destinationKey); it[enabledKey] = false
        }; Unit
    }
    private fun history(raw: String?, current: TransitDestination?) = decodeHistory(raw).ifEmpty { listOfNotNull(current) }
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
