package com.pion.phonecleaner.feature.settings.settings

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.settings.AppLanguage

/**
 * `settings` (`docs/screens/20-settings-language-and-push.md` §1.1). Replaces `DonactioActivity`.
 *
 * **There is nothing to fold.** That Activity has zero fields and zero observables, so every field
 * below is new — it is the home nine Activity fields never had. Three of the four rows exist because
 * the chapter found something *missing* rather than something to port.
 */
@Immutable
data class SettingsState(
    /**
     * Rendered as the Language row's trailing text. The competitor shows nothing there, so the user
     * must enter the next screen to learn what is selected (§1.4 delta 4). `null` means the app is
     * following the system language, which is a real answer and not "unknown".
     */
    val currentLanguage: AppLanguage? = null,
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,

    /**
     * PENDING OWNER DECISION 4. The resident status-bar widget ships **opt-in, DEFAULT OFF**; the
     * competitor posts it from `Application.onCreate` and has no off switch anywhere in the app
     * (`java/hc/f.java:79-89`, `java/wd/j.java:113-123`). Since Android 13 a notification the user
     * cannot turn off *inside* the app is one they revoke wholesale, taking every legitimate
     * notification with it (§1.4 delta 5).
     *
     * **It is a `Boolean` on `State`, not an `Effect`**: §4.6's dialog rule generalises — a visible
     * condition that must survive rotation lives in state.
     */
    val isResidentWidgetEnabled: Boolean = false,

    /** How many of the permissions the centre manages are still missing. `0` hides the badge. */
    val missingPermissionCount: Int = 0,
) : UiState

sealed interface SettingsIntent : UiIntent {
    /** Arrives on every resume; `init`'s three collectors already re-emit, so it does nothing. */
    data object ScreenResumed : SettingsIntent
    data object LanguageRowTapped : SettingsIntent
    data object AboutRowTapped : SettingsIntent
    data object PermissionCentreRowTapped : SettingsIntent
    data class ResidentWidgetToggled(val enabled: Boolean) : SettingsIntent
    data object BackPressed : SettingsIntent
}

sealed interface SettingsEffect : UiEffect {
    data object NavigateToLanguage : SettingsEffect
    data object NavigateToAbout : SettingsEffect
    data object NavigateToPermissionCentre : SettingsEffect
    data object NavigateBack : SettingsEffect
}
