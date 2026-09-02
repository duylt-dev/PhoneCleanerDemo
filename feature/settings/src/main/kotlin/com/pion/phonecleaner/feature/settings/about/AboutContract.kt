package com.pion.phonecleaner.feature.settings.about

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.settings.LegalDocument

/**
 * `about` (`docs/screens/20-settings-language-and-push.md` §3.1). Replaces `AuddulgActivity`.
 *
 * The competitor's one Activity field, `secretClickCount`, does **not** become state: the gesture it
 * drives is deleted outright (§3.4 delta 1). Its two invisible 50 dp corner hit boxes are deleted
 * too — undiscoverable, undocumented, and live only once production logging has been switched on.
 */
@Immutable
data class AboutState(
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    /**
     * Debug tooling is visible in a debug build and **nowhere else** — never behind a tap gesture.
     * It comes from `AppInfoProvider.isDebugBuild`, an injected property, so the release behaviour is
     * a unit test rather than a `BuildConfig` constant the reducer cannot be asked about (§3.2).
     */
    val isDeveloperSectionVisible: Boolean = false,
) : UiState

sealed interface AboutIntent : UiIntent {
    data class LegalDocumentTapped(val document: LegalDocument) : AboutIntent
    data object DeveloperRowTapped : AboutIntent
    data object BackPressed : AboutIntent
}

sealed interface AboutEffect : UiEffect {
    data class NavigateToLegalDocument(val document: LegalDocument) : AboutEffect
    data object NavigateToDeveloperTools : AboutEffect
    data object NavigateBack : AboutEffect
}
