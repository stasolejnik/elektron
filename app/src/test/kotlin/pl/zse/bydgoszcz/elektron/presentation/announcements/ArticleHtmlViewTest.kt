package pl.zse.bydgoszcz.elektron.presentation.announcements

import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArticleHtmlViewTest {
    private fun request(url: String, gesture: Boolean = true) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = gesture
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    @Test fun savedArticleDoesNotExecuteScriptsReadFilesOrLoadExternalResources() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val view = createArticleWebView(app)
        try {
            assertFalse(view.settings.javaScriptEnabled)
            assertFalse(view.settings.domStorageEnabled)
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            assertTrue(view.settings.blockNetworkLoads)
            val response = view.webViewClient.shouldInterceptRequest(view, request("https://example.com/image.png"))!!
            assertEquals(-1, response.data.read())
        } finally { view.destroy() }
    }

    @Test fun onlyTappedWebLinksAreOpenedInBrowser() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val view = createArticleWebView(app)
        try {
            view.webViewClient.shouldOverrideUrlLoading(view, request("https://zse.bydgoszcz.pl/a", gesture = false))
            assertNull(shadowOf(app).nextStartedActivity)
            view.webViewClient.shouldOverrideUrlLoading(view, request("file:///private.txt"))
            assertNull(shadowOf(app).nextStartedActivity)
            view.webViewClient.shouldOverrideUrlLoading(view, request("https://zse.bydgoszcz.pl/dokument.pdf"))
            assertEquals("https://zse.bydgoszcz.pl/dokument.pdf", shadowOf(app).nextStartedActivity.dataString)
        } finally { view.destroy() }
    }
}
