package pl.zse.bydgoszcz.elektron.data.remote.http

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class EncodingAwareBodyTest {

    private val iso2 = Charset.forName("ISO-8859-2")

    @Test
    fun headerCharsetWithAndWithoutQuotes() {
        assertEquals(iso2, EncodingAwareBody.extractCharsetFromHeader("text/html; charset=ISO-8859-2"))
        // Dawniej wartość w cudzysłowie była pomijana.
        assertEquals(iso2, EncodingAwareBody.extractCharsetFromHeader("text/html; charset=\"ISO-8859-2\""))
        assertEquals(iso2, EncodingAwareBody.extractCharsetFromHeader("text/html;charset='iso-8859-2'"))
        assertEquals(Charsets.UTF_8, EncodingAwareBody.extractCharsetFromHeader("text/html; Charset = utf-8"))
    }

    @Test
    fun unknownOrMissingCharsetIsIgnored() {
        assertNull(EncodingAwareBody.extractCharsetFromHeader("text/html"))
        assertNull(EncodingAwareBody.extractCharsetFromHeader("text/html; charset=nie-ma-takiego"))
        assertNull(EncodingAwareBody.extractCharsetFromHeader("text/html; charset=\"\""))
    }

    @Test
    fun quotedHeaderDecidesOverMeta() {
        val html = "<html><head><meta charset=\"utf-8\"></head><body><p>Zażółć</p></body></html>"
        val response = Response.Builder()
            .request(Request.Builder().url("https://zastepstwa.zse.bydgoszcz.pl/").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Type", "text/html; charset=\"ISO-8859-2\"")
            .body(html.toByteArray(iso2).toResponseBody("text/html".toMediaType()))
            .build()
        assertEquals("Zażółć", EncodingAwareBody.asDocument(response).selectFirst("p")?.text())
    }
}
