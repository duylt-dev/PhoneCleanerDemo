package com.pion.phonecleaner.domain.model.applock

/**
 * The two App Lock preferences, on ONE upstream (`docs/screens/16-app-lock.md` §4.2).
 *
 * They travel together because the settings screen renders both switches from one collector: two
 * separate `Flow`s can be a frame out of step, and the App Lock home also reads
 * [lockNewlyInstalled] from the same source.
 *
 * Both default to `true` for parity with the competitor's `od.d0` defaults
 * (`docs/screens/16-app-lock.md` §4.5).
 *
 * ### [isAppLockEnabled] gates *every* App Lock behaviour
 *
 * The competitor's master switch does not: `HosptweigReceiver.g()` never reads `app_lock_enable`, so
 * turning App Lock off still shows the newly-installed-app prompt, although the row's own subtitle
 * says it does not (`docs/screens/16-app-lock.md` §4.5). Here the flag is the single gate that
 * `ForegroundAppMonitor` observes, and there is no second place to turn the feature on.
 */
data class AppLockSettings(
    /** The master switch. `false` stops the monitor and suppresses every prompt. */
    val isAppLockEnabled: Boolean = true,
    /**
     * Offer to lock an app the moment it is installed.
     *
     * PENDING OWNER DECISION (4) — how assertive the app is outside itself. The competitor draws
     * this offer as a `WindowManager` overlay from a `PACKAGE_ADDED` receiver
     * (`docs/screens/16-app-lock.md` §6 item 4). Nothing in this cluster implements a delivery
     * mechanism for it; the flag is persisted and read, and what reads it is not decided here.
     */
    val lockNewlyInstalled: Boolean = true,
)
