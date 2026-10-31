package pl.zse.bydgoszcz.elektron.data.remote.sources.zse

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.SchoolPageChangedException
import java.io.IOException

/** Ścieżka produkcyjna: prawdziwe bajty ISO-8859-2 bez kodowania w nagłówku, kontrola układu i stopki. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZseSubstitutionsSourceTest {
    private fun source(bytes: ByteArray) = ZseSubstitutionsSource(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(bytes.toResponseBody("text/html".toMediaType())).build()
    }.build())

    private val real: ByteArray = javaClass.getResourceAsStream("/zastepstwa/2026-10-22_080536.html")!!.use { it.readBytes() }

    @Test fun realPageKeepsPolishLettersAndAllEntries() = runBlocking {
        val page = source(real).fetchPage()
        assertEquals(32, page.items.size)
        assertEquals(2, page.classless.size)   // zajęcia łączone, wpisy bez klasy
        assertTrue(page.items.any { it.originalTeacher.any { c -> c in "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ" } })
        assertTrue(page.items.none { '�' in it.originalTeacher || 'Ã' in it.originalTeacher })
    }

    @Test fun truncatedPageIsAnError() {
        val e = assertThrows(IOException::class.java) {
            runBlocking { source(real.copyOf(real.size * 6 / 10)).fetchPage() }
        }
        assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("niepełna odpowiedź"))
    }

    @Test fun errorPageWithHttp200IsNotAnEmptyPage() {
        val html = "<html><head><title>Awaria</title></head><body>Przerwa techniczna</body></html>"
        assertThrows(SchoolPageChangedException::class.java) {
            runBlocking { source(html.toByteArray(Charsets.ISO_8859_1)).fetchPage() }
        }
    }
}
