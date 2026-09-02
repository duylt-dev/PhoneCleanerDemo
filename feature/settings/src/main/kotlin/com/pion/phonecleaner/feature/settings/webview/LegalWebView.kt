package com.pion.phonecleaner.feature.settings.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.pion.phonecleaner.core.common.error.AppError

/**
 * The `WebView` itself, held by the screen and reported upward as intents
 * (`docs/screens/20-settings-language-and-push.md` §4.3).
 *
 * **Every `WebSettings` value below is written explicitly.** The competitor configures none at all,
 * so JavaScript is off by *default* rather than by decision and a maintainer cannot tell which
 * (§4.4 delta 6). A legal document is static HTML; it needs no script, no file access and no content
 * access, and saying so in code is what keeps a later "just turn this on" honest.
 *
 * The instance is `remember`ed and destroyed in a `DisposableEffect`. The competitor's `onDestroy`
 * calls neither `destroy()` nor a detach, and does not dismiss its loading dialog — which then leaks
 * its window token if the Activity dies first (§4.4 delta 4).
 *
 * @param onReloadRequested handed the instance so the Route's `ReloadPage` effect can drive it
 *   without the screen holding a second reference.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LegalWebView(
    url: String,
    onIntent: (WebViewIntent) -> Unit,
    onReloadRequested: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            isVerticalScrollBarEnabled = true
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = LegalWebViewClient(onIntent)
        onReloadRequested(webView)
        onDispose {
            onReloadRequested(null)
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { view -> if (view.url != url) view.loadUrl(url) },
    )
}

/**
 * Reports every transition as an intent. The competitor registers **no error callbacks at all** and
 * dismisses its spinner only from `onPageFinished`, which is an unrecoverable state when the load
 * fails (§4.4 delta 3).
 */
private class LegalWebViewClient(
    private val onIntent: (WebViewIntent) -> Unit,
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onIntent(WebViewIntent.PageStarted)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onIntent(WebViewIntent.PageFinished)
    }

    /**
     * Only the main frame's failure is the page's failure: a sub-resource that 404s must not blank a
     * document that rendered.
     */
    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame != true) return
        onIntent(WebViewIntent.PageFailed(AppError.NoNetwork))
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        if (request?.isForMainFrame != true) return
        onIntent(WebViewIntent.PageFailed(AppError.NotFound(request.url?.toString())))
    }

    /**
     * Intercepted and handed upward; never swallowed with a bare `return true` (§4.4 delta 2).
     *
     * `hasGesture()` is what separates the two cases the competitor collapses: a link the **user
     * tapped** leaves for the browser, and a server redirect of the document we asked for stays in
     * this view. Returning `true` for a redirect would send the user out of the app for a page they
     * never navigated to.
     */
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val target = request?.url?.toString() ?: return false
        if (!request.hasGesture()) return false
        onIntent(WebViewIntent.ExternalLinkTapped(target))
        return true
    }
}
