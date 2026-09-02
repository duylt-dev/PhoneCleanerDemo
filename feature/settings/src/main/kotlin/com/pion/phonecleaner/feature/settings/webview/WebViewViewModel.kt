package com.pion.phonecleaner.feature.settings.webview

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.repository.LegalDocumentUrls

/**
 * `webview` (`docs/screens/20-settings-language-and-push.md` §4.2).
 *
 * **No coroutines at all**, so nothing to cancel: the page is loaded by the `WebView` and every
 * transition arrives as an intent from its client. The initial state is built from the route
 * argument and the URL table, in the constructor — there is no `setState` in `init` for either
 * (MVI §5).
 */
class WebViewViewModel(
    savedStateHandle: SavedStateHandle,
    urls: LegalDocumentUrls,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<WebViewState, WebViewIntent, WebViewEffect>(
    savedStateHandle.legalDocument().let { document ->
        WebViewState(
            document = document,
            url = urls.of(document),
            // A screen that has nothing to load is not "loading": it goes straight to its
            // unavailable copy, rather than spinning forever the way the competitor's modal does
            // when its URL extra is null (§4.4 delta 3).
            isLoading = urls.of(document).isNotBlank(),
        )
    },
    log,
) {

    override fun onIntent(intent: WebViewIntent) {
        when (intent) {
            WebViewIntent.PageStarted -> setState { copy(isLoading = true, error = null) }
            WebViewIntent.PageFinished -> setState { copy(isLoading = false) }
            is WebViewIntent.PageFailed -> setState { copy(isLoading = false, error = intent.error) }

            WebViewIntent.RetryTapped -> {
                if (currentState.isUnavailable) return
                setState { copy(isLoading = true, error = null) }
                sendEffect(WebViewEffect.ReloadPage)
            }

            // Handed to the browser rather than swallowed: third-party content's correct home is not
            // a WebView inside this app (§4.4 delta 2).
            is WebViewIntent.ExternalLinkTapped ->
                sendEffect(WebViewEffect.OpenInBrowser(intent.url))

            WebViewIntent.BackPressed -> sendEffect(WebViewEffect.NavigateBack)
        }
    }
}
