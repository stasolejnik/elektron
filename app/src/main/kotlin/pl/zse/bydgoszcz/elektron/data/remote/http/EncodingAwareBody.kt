package pl.zse.bydgoszcz.elektron.data.remote.http

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.nio.charset.Charset

/**
 * Response -> Document z rozstrzyganiem kodowania.
 * 1. forcedCharset (jeśli podany)  2. charset= z nagłówka
 * 3. <meta charset>                 4. UTF-8
 * Dla zastepstwa.zse.bydgoszcz.pl: forcedCharset = ISO_8859_2 (wymuszone).
 */
object EncodingAwareBody {

    fun asDocument(
        response: Response,
        forcedCharset: Charset? = null,
        xmlMode: Boolean = false
    ): Document {
        val bytes = response.body?.bytes()
            ?: error("Puste body z ${response.request.url}")
        val charset = forcedCharset
            ?: response.header("Content-Type")?.let(::extractCharsetFromHeader)
            ?: sniffCharsetFromHtml(bytes)
            ?: Charsets.UTF_8
        val text = String(bytes, charset)
        val baseUri = response.request.url.toString()
        return if (xmlMode) Jsoup.parse(text, baseUri, Parser.xmlParser())
        else Jsoup.parse(text, baseUri)
    }

    // Wartość także w cudzysłowie (charset="ISO-8859-2") - dawniej taki nagłówek był pomijany
    // (wzorzec nie dopuszczał cudzysłowu), więc kodowanie zgadywane było dopiero z <meta>.
    private val HEADER_CHARSET = Regex("charset\\s*=\\s*[\"']?([\\w\\-.:]+)", RegexOption.IGNORE_CASE)

    internal fun extractCharsetFromHeader(contentType: String): Charset? {
        val m = HEADER_CHARSET.find(contentType) ?: return null
        return runCatching { Charset.forName(m.groupValues[1]) }.getOrNull()
    }

    internal fun sniffCharsetFromHtml(bytes: ByteArray): Charset? {
        val head = bytes.copyOfRange(0, minOf(4096, bytes.size))
        val ascii = String(head, Charsets.ISO_8859_1)
        val m = Regex("<meta[^>]+charset=[\"']?([\\w\\-]+)", RegexOption.IGNORE_CASE).find(ascii)
            ?: return null
        return runCatching { Charset.forName(m.groupValues[1]) }.getOrNull()
    }
}
