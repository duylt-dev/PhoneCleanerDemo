package com.pion.phonecleaner.feature.settings.webview

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.feature.settings.R

/**
 * `webview` (`docs/screens/20-settings-language-and-push.md` §4.1). Replaces `TribiniActivity`.
 *
 * The competitor's four Activity fields — `bind`, `pageTitle`, `pageUrl`, `loadingDialog` — fold to
 * the four below. **`pageTitle` disappears entirely**: it is passed as a second `Intent` extra and
 * written into the toolbar with no null guard; here it is derived from [document], so it cannot be
 * empty. The `nc.y` modal `AlertDialog` becomes [isLoading], drawn in place — a loading condition is
 * not a dialog (§4.4 delta 4, `LLM.md` §7.4).
 */
@Immutable
data class WebViewState(
    /** The screen takes a [LegalDocument], never a free-form URL (§4.4 delta 1). */
    val document: LegalDocument,
    /**
     * Resolved once from `LegalDocumentUrls`. **May be empty**, and that is a real state rather than
     * a failure: this build configures no legal URLs — see `DefaultLegalDocumentUrls` in `:data` for
     * why. An empty value renders [isUnavailable] copy instead of loading something.
     */
    val url: String,
    val isLoading: Boolean = true,
    val error: AppError? = null,
) : UiState {

    @get:StringRes
    val titleRes: Int get() = when (document) {
        LegalDocument.TermsOfService -> R.string.settings_webview_terms_title
        LegalDocument.PrivacyPolicy -> R.string.settings_webview_privacy_title
    }

    /** No URL is configured for this document in this build. Nothing is loaded, and it says so. */
    val isUnavailable: Boolean get() = url.isBlank()
}

sealed interface WebViewIntent : UiIntent {
    data object PageStarted : WebViewIntent
    data object PageFinished : WebViewIntent
    data class PageFailed(val error: AppError) : WebViewIntent
    data object RetryTapped : WebViewIntent

    /**
     * An in-page link, redirect or form navigation. The competitor returns `true` from
     * `shouldOverrideUrlLoading` unconditionally, so every one of them is silently swallowed and a
     * legal page that links to a sub-policy is a dead end (§4.4 delta 2).
     */
    data class ExternalLinkTapped(val url: String) : WebViewIntent
    data object BackPressed : WebViewIntent
}

sealed interface WebViewEffect : UiEffect {
    data object ReloadPage : WebViewEffect
    data class OpenInBrowser(val url: String) : WebViewEffect
    data object NavigateBack : WebViewEffect
}
