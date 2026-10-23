package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import pl.zse.bydgoszcz.elektron.data.remote.http.readCancellable
import pl.zse.bydgoszcz.elektron.domain.model.LiveDeparture
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tablica odjazdów przystanku z BUSearch (te same dane co planer, bez nowego serwera): opóźnienia
 * śledzonych pojazdów. Tylko na widocznym ekranie Przejazdów i tylko dla kursów odjeżdżających
 * wkrótce - patrz TransitLive.
 */
@Singleton
class TransitLiveRepository @Inject constructor(private val client: OkHttpClient) {
    suspend fun departures(platformId: String, line: String): List<LiveDeparture> = withContext(Dispatchers.IO) {
        require(platformId.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
        val url = "https://api.busearch.pl/v1/cities/bydgoszcz/stops/$platformId/departures".toHttpUrl().newBuilder()
            .addQueryParameter("line", line.take(32)).build()
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        val text = client.newCall(request).readCancellable {
            if (!it.isSuccessful) throw IOException("Tablica odjazdów: HTTP ${it.code}")
            val source = it.body?.source() ?: throw IOException("Pusta odpowiedź")
            source.request(500_001)
            if (source.buffer.size > 500_000) throw IOException("Zbyt duża odpowiedź")
            source.readUtf8()
        }
        parse(text)
    }

    internal companion object {
        fun parse(text: String): List<LiveDeparture> {
            val array = JSONArray(text)
            require(array.length() <= 500)
            return (0 until array.length()).mapNotNull { i -> runCatching {
                val o = array.getJSONObject(i)
                val delay = if (o.has("delayMin") && !o.isNull("delayMin")) o.getInt("delayMin").also { require(it in -60..240) } else null
                LiveDeparture(o.getString("linia").also { require(it.isNotBlank() && it.length <= 32) }, o.getLong("atMs"), delay,
                    live = o.optString("source") == "live")
            }.getOrNull() }
        }
    }
}
