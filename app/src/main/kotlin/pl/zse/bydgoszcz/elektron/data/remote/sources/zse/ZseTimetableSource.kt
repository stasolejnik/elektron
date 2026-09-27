package pl.zse.bydgoszcz.elektron.data.remote.sources.zse

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto
import pl.zse.bydgoszcz.elektron.data.remote.http.EncodingAwareBody
import pl.zse.bydgoszcz.elektron.data.remote.parser.OptivumListaParser
import pl.zse.bydgoszcz.elektron.data.remote.parser.OptivumTimetableParser
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja TimetableSource dla plan.zse.bydgoszcz.pl (UTF-8, Optivum VULCAN).
 * Cała sieć przez OkHttp — encoding wykrywany przez EncodingAwareBody.
 */
@Singleton
class ZseTimetableSource @Inject constructor(
    private val client: OkHttpClient
) : TimetableSource {

    override suspend fun fetchSidebar(): List<ClassListItemDto> = withContext(Dispatchers.IO) {
        val url = SchoolEndpoints.Timetable.SIDEBAR
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} dla $url")
                return@withContext emptyList()
            }
            val doc = EncodingAwareBody.asDocument(resp)
            OptivumListaParser.parse(doc, SchoolEndpoints.Timetable.BASE)
        }
    }

    override suspend fun fetchTimetable(classId: String): TimetableDto = withContext(Dispatchers.IO) {
        val url = SchoolEndpoints.Timetable.classPlan(classId)
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} dla $url")
                return@withContext TimetableDto(classId, classId, null, null, emptyList())
            }
            val doc = EncodingAwareBody.asDocument(resp)
            OptivumTimetableParser.parse(doc, classId)
        }
    }

    companion object { private const val TAG = "ZseTimetableSource" }
}
