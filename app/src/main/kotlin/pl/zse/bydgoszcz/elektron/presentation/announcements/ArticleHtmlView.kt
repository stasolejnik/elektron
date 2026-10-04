package pl.zse.bydgoszcz.elektron.presentation.announcements

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

@Composable
internal fun ArticleHtmlView(document: String, baseUrl: String, modifier: Modifier = Modifier) {
    val holder = remember { arrayOfNulls<WebView>(1) }
    val textZoom = (LocalDensity.current.fontScale * 100).roundToInt()
    DisposableEffect(Unit) {
        onDispose { holder[0]?.apply { stopLoading(); destroy() }; holder[0] = null }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            createArticleWebView(context).also { holder[0] = it }
        },
        update = { view ->
            view.settings.textZoom = textZoom
            if (view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL(baseUrl, document, "text/html", "UTF-8", null)
            }
        }
    )
}

internal fun createArticleWebView(context: Context): WebView =
WebView(context).apply {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    settings.apply {
        javaScriptEnabled = false
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        blockNetworkLoads = true
        loadsImagesAutomatically = false
        defaultTextEncodingName = "UTF-8"
    }
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.isForMainFrame && request.hasGesture()) SafeUrls.open(context, request.url.toString())
            return true
        }
    }
}
