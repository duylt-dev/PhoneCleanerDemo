package com.pion.phonecleaner.domain.model.notification

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * One row of the hiding-settings list (`docs/screens/17-notification-and-permissions.md` §0.1).
 *
 * [isHidingEnabled] is a field on the immutable row, and the DataStore `Flow` is its only writer. The
 * competitor reads the flag back out of `SharedPreferences` on **every tap** (§2.1), and its list is
 * bound with `addAll` rather than replace, so a second emission duplicates every row (§2.5).
 *
 * No icon: `AppIconLoader` resolves one from [packageName] per visible row. `vd.d.a()` calls
 * `loadIcon` for every launchable app before anything renders (§2.5).
 */
data class NotificationHidingApp(
    val packageName: String,
    val label: String,
    val isHidingEnabled: Boolean,
)

/**
 * The master switch and the per-app rows in **one** value.
 *
 * One upstream carrying both halves is the point: the master switch and the rows can never be a frame
 * out of step, which is what lets `hidingsettings` open exactly one collector (§2.2).
 *
 * [isMasterEnabled] defaults to `true` for parity with the competitor's default (§2.1). [Disabled] is
 * the value `NotificationInterceptorService` starts from before its first emission arrives — hiding
 * nothing is the only safe answer while the settings are still unknown (§5).
 */
data class NotificationHidingSettings(
    val isMasterEnabled: Boolean,
    val apps: ImmutableList<NotificationHidingApp>,
) {
    /** Apps the user has opted in. Read by the interception policy, never recomputed at a call site. */
    fun isHidingEnabledFor(packageName: String): Boolean =
        isMasterEnabled && apps.any { it.packageName == packageName && it.isHidingEnabled }

    companion object {
        /** Hides nothing. The service's starting value, before the store has emitted. */
        val Disabled: NotificationHidingSettings =
            NotificationHidingSettings(isMasterEnabled = false, apps = persistentListOf())
    }
}
