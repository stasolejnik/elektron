package pl.zse.bydgoszcz.elektron.data.remote.sources.zse

import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsPage
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import pl.zse.bydgoszcz.elektron.data.remote.http.readCancellable
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

    override suspend fun fetchSubstitutions(): List<SubstitutionDto> = fetchPage().items

    override suspend fun fetchPage(): SubstitutionsPage = withContext(Dispatchers.IO) {
        val url = SchoolEndpoints.Substitutions.INDEX
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).readCancellable { resp ->
            // Błąd serwera to porażka synchronizacji (komunikat w aplikacji), nie "brak zastępstw".
            // Zapisane zastępstwa zostają nietknięte.
            if (!resp.isSuccessful) throw java.io.IOException("Zastępstwa: HTTP ${resp.code} dla $url")
            val doc = EncodingAwareBody.asDocument(resp, forcedCharset = java.nio.charset.Charset.forName("ISO-8859-2"))
            if (!ZastepstwaParser.hasRecognizedLayout(doc)) {
                throw pl.zse.bydgoszcz.elektron.domain.model.SchoolPageChangedException("zastępstwa")
            }
            // Strona z dniami zastępuje zapisane dni - tylko cała (ze stopką). Ucięta: błąd, dane zostają.
            if (ZastepstwaParser.pageDates(doc).isNotEmpty() && !ZastepstwaParser.hasFooter(doc)) {
                throw java.io.IOException("Zastępstwa: niepełna odpowiedź serwera")
            }
            val parsed = ZastepstwaParser.parseDetailed(doc)
            SubstitutionsPage(ZastepstwaParser.pageDates(doc), parsed.items, parsed.incompleteDates, parsed.classless)
        }
    }

    companion object { private const val TAG = "ZseSubstitutionsSource" }
}
