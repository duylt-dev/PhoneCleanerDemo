package com.pion.phonecleaner.feature.settings.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (`LLM.md` §7.1).
 *
 * The `WebView`'s own back stack is handled **here**, not in the ViewModel: `canGoBack()` is a
 * property of a `View`, and the competitor replaces it with a hand-maintained
 * `ArrayList<Activity>` (`zd.c`) that a second hand-rolled stack (`od.h0`) also tracks
 * (`docs/screens/20-settings-language-and-push.md` §4.4 delta 7).
 */
@Composable
fun WebViewRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebViewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    // Platform state living in the composable and reporting upward (MVI §4): the ViewModel names a
    // reload, the Route owns the instance that performs one.
    var webView by remember { mutableStateOf<WebView?>(null) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            WebViewEffect.ReloadPage -> webView?.reload()
            is WebViewEffect.OpenInBrowser -> openInBrowser(context, effect.url)
            WebViewEffect.NavigateBack -> onNavigateBack()
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onIntent(WebViewIntent.BackPressed)
    }

    WebViewScreen(
        state = state,
        onIntent = onIntent,
        onWebViewAttached = { webView = it },
        modifier = modifier,
    )
}

/**
 * Hands a tapped link to whatever the user's device opens links with.
 *
 * UNKNOWN — the appendix asks for a **Custom Tab** (§4.4 delta 2), which is `androidx.browser`.
 * That artifact is not in `gradle/libs.versions.toml` and the catalogue is not this cluster's file to
 * edit (`LLM.md` §10.3 fixes the catalogue as the single place a version is written). A plain
 * `ACTION_VIEW` is the conservative substitute: it reaches the same browser, it just does not get the
 * in-app chrome. Adding `androidx-browser` to the catalogue is the whole change.
 *
 * `ActivityNotFoundException` is caught rather than allowed to escape: a device with no browser is
 * unusual but not impossible, and the screen the user is on must survive it.
 */
private fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (missing: ActivityNotFoundException) {
        // Nothing on this device can open a link. Staying on the page is the correct outcome.
    }
}
