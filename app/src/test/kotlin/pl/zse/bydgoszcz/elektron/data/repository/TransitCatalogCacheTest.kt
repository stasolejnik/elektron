package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class TransitCatalogCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = File(context.cacheDir, "transit_stops.json")
    private val json = """[{"id":"24","nazwa":"Szkoła","lat":53.123,"lon":18.027}]"""

    @Test fun staleCacheSurvivesFailureAndExplicitRefreshBypassesCooldown() = runBlocking {
        file.writeText(json)
        assertTrue(file.setLastModified(System.currentTimeMillis() - 48 * 60 * 60 * 1000L))
        var requests = 0
        var offline = true
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            if (offline) throw IOException("Offline")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        try {
            val catalog = TransitStopCatalog(context, client)
            repeat(3) { assertTrue(catalog.load().stale) }
            assertEquals(1, requests)
            offline = false
            assertFalse(catalog.load(force = true).stale)
            assertFalse(catalog.load().stale)
            assertEquals(2, requests)
        } finally { file.delete() }
    }

    @Test fun missingCacheDoesNotCauseRepeatedRequestsAfterFailure() = runBlocking {
        file.delete()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor {
            requests++
            throw IOException("Offline")
        }.build()
        val catalog = TransitStopCatalog(context, client)
        repeat(3) {
            try { catalog.load(); fail("Expected failure") } catch (_: IOException) { }
        }
        assertEquals(1, requests)
        try { catalog.load(force = true); fail("Expected failure") } catch (_: IOException) { }
        assertEquals(2, requests)
    }

    @Test fun oversizedDiskCacheIsRejectedAndReplaced() = runBlocking {
        file.writeText(json + " ".repeat(2_000_001))
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        try {
            assertEquals("24", TransitStopCatalog(context, client).load().stops.single().key)
            assertEquals(1, requests)
            assertEquals(json, file.readText())
        } finally { file.delete() }
    }
}
