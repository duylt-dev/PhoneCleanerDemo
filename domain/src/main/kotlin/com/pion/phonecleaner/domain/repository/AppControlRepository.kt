package com.pion.phonecleaner.domain.repository

/**
 * The two questions this app may ask the platform *about another app* without an Activity
 * (`docs/screens/14-file-tools-and-app-manager.md` §0.1, replacing `vd.c.g`).
 *
 * **Removing an app is not here, and cannot be.** `Intent.ACTION_DELETE` needs an Activity to launch
 * it and an activity result to hear the answer, so it is an `Effect` the Route performs — the same
 * shape as `DeleteOutcome.PendingConsent`. What *is* here is the verification afterwards: the
 * competitor counts completions from every `PACKAGE_REMOVED` broadcast without inspecting
 * `EXTRA_REPLACING`, so an app updating in the background advances its counter and a re-install fires
 * the broadcast it treats as a removal (§5.5).
 *
 * DECLARED IN `filesDataModule`.
 */
interface AppControlRepository {

    /**
     * A live `PackageManager` re-query — the answer to *"did the system dialog actually remove it?"*
     * and to *"is WhatsApp installed at all?"*, which the competitor's cleaner never asks before
     * rendering six zero-byte tiles (§6.5).
     */
    suspend fun isInstalled(packageName: String): Boolean
}
