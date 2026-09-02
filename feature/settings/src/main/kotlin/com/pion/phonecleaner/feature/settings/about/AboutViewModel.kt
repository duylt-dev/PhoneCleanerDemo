package com.pion.phonecleaner.feature.settings.about

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.repository.AppInfoProvider

/**
 * `about` (`docs/screens/20-settings-language-and-push.md` §3.2).
 *
 * No IO, no flows, nothing to cancel: `init` is one `setState` from the injected provider, and
 * `onIntent` is three `sendEffect` arms. The competitor reads a hand-typed `"v2.0.0.0"` literal
 * instead (§3.4 delta 3).
 */
class AboutViewModel(
    appInfo: AppInfoProvider,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<AboutState, AboutIntent, AboutEffect>(
    AboutState(
        appName = appInfo.appName,
        versionName = appInfo.versionName,
        versionCode = appInfo.versionCode,
        isDeveloperSectionVisible = appInfo.isDebugBuild,
    ),
    log,
) {

    override fun onIntent(intent: AboutIntent) {
        when (intent) {
            is AboutIntent.LegalDocumentTapped ->
                sendEffect(AboutEffect.NavigateToLegalDocument(intent.document))

            // Guarded, so a synthesised intent cannot reach the debug screen in a release build —
            // where the destination is not even compiled in (§3.2).
            AboutIntent.DeveloperRowTapped ->
                if (currentState.isDeveloperSectionVisible) {
                    sendEffect(AboutEffect.NavigateToDeveloperTools)
                }

            AboutIntent.BackPressed -> sendEffect(AboutEffect.NavigateBack)
        }
    }
}
