package pl.zse.bydgoszcz.elektron.data.remote.sources.zse

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto
import pl.zse.bydgoszcz.elektron.data.remote.http.EncodingAwareBody
import pl.zse.bydgoszcz.elektron.data.remote.parser.ZastepstwaParser
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja SubstitutionsSource dla zastepstwa.zse.bydgoszcz.pl.
 * ⚠️ Serwer NIE wysyła poprawnego nagłówka Content-Type — wymuszamy ISO-8859-2.
 */
@Singleton
class ZseSubstitutionsSource @Inject constructor(
    private val client: OkHttpClient
) : SubstitutionsSource {

    override suspend fun fetchSubstitutions(): List<SubstitutionDto> = withContext(Dispatchers.IO) {
        val url = SchoolEndpoints.Substitutions.INDEX
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} dla $url")
                return@withContext emptyList()
            }
            val doc = EncodingAwareBody.asDocument(resp, forcedCharset = java.nio.charset.Charset.forName("ISO-8859-2"))
            ZastepstwaParser.parse(doc)
        }
    }

    companion object { private const val TAG = "ZseSubstitutionsSource" }
}
