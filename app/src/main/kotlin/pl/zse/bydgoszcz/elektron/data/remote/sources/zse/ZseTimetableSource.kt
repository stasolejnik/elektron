package pl.zse.bydgoszcz.elektron.data.remote.sources.zse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import pl.zse.bydgoszcz.elektron.data.remote.http.readCancellable
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
        client.newCall(req).readCancellable { resp ->
            // Błąd serwera to porażka synchronizacji, nie "pusta lista klas" (dawniej liczona jako sukces).
            if (!resp.isSuccessful) throw java.io.IOException("Lista klas: HTTP ${resp.code} dla $url")
            val doc = EncodingAwareBody.asDocument(resp)
            OptivumListaParser.parse(doc, SchoolEndpoints.Timetable.BASE).also { items ->
                if (items.none { it.kind == ClassListItemDto.Kind.CLASS }) {
                    throw pl.zse.bydgoszcz.elektron.domain.model.SchoolPageChangedException("lista klas")
                }
            }
        }
    }

    override suspend fun fetchTimetable(classId: String): TimetableDto = withContext(Dispatchers.IO) {
        val url = SchoolEndpoints.Timetable.classPlan(classId)
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).readCancellable { resp ->
            // Błąd serwera to porażka synchronizacji (komunikat w aplikacji), nie pusty plan.
            // Zapisany plan zostaje nietknięty.
            if (!resp.isSuccessful) throw java.io.IOException("Plan lekcji: HTTP ${resp.code} dla $url")
            val doc = EncodingAwareBody.asDocument(resp)
            OptivumTimetableParser.parse(doc, classId)
        }
    }

    companion object { private const val TAG = "ZseTimetableSource" }
}
