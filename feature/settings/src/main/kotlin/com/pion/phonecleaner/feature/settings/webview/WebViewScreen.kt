package com.pion.phonecleaner.feature.settings.webview

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.settings.R

/**
 * `docs/screens/20-settings-language-and-push.md` §4.3.
 *
 * Loading is drawn **in place** — a determinate-looking bar at the top of the content — rather than
 * as the competitor's modal `AlertDialog`, which is shown before `loadUrl` including when the URL is
 * null and is dismissed only from `onPageFinished` (§4.4 deltas 3 and 4).
 */
@Composable
internal fun WebViewScreen(
    state: WebViewState,
    onIntent: (WebViewIntent) -> Unit,
    onWebViewAttached: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(state.titleRes),
                onBack = { onIntent(WebViewIntent.BackPressed) },
            )
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isUnavailable -> EmptyState(
                        message = stringResource(R.string.settings_webview_unavailable),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> LegalWebView(
                        url = state.url,
                        onIntent = onIntent,
                        onReloadRequested = onWebViewAttached,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (state.isLoading) {
                    LinearProgressIndicator(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    )
                }
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(WebViewIntent.RetryTapped) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(ScreenGutter),
                    )
                }
            }
        }
    }
}
