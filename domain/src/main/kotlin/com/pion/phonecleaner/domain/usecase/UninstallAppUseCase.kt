package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.repository.AppControlRepository

/**
 * Did the system's uninstall dialog actually remove the package?
 * (`docs/screens/14-file-tools-and-app-manager.md` §5.2, §5.5.)
 *
 * **The removal itself is not here.** `ACTION_DELETE` needs an Activity, so the ViewModel raises
 * `RequestUninstall(packageName)` and the Route launches it — the same shape as
 * `DeleteOutcome.PendingConsent`. This is the half a ViewModel may own: the **verified re-query**
 * after the round trip.
 *
 * That verification is the whole point. The competitor counts completions from every
 * `PACKAGE_REMOVED` broadcast without inspecting `EXTRA_REPLACING`, so an app updating in the
 * background advances its counter and a re-install is counted as a removal — and if the user cancels
 * one dialog its two counters never meet, leaving the screen with no message and no retry.
 */
class UninstallAppUseCase(
    private val appControl: AppControlRepository,
) {
    /** True when the package is **still installed** — i.e. the user declined, or the removal failed. */
    suspend operator fun invoke(packageName: String): Boolean = appControl.isInstalled(packageName)
}
