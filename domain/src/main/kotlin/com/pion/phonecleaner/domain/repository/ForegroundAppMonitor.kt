package com.pion.phonecleaner.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Watches which app is in the foreground and asks for a lock when it is one of ours.
 *
 * **No screen and no ViewModel** (`docs/screens/16-app-lock.md` §5): `od.e0` plus `od.o0.g()` have
 * no UI, so they become one data-layer component. DECLARED IN `appLockDataModule`; implemented by
 * `UsageStatsForegroundAppMonitor`.
 *
 * ### What it replaces
 *
 * | Competitor | Why it cannot be ported |
 * |---|---|
 * | A 500 ms poll, forever, from `Application.onCreate`, on an inline `CoroutineScope(Dispatchers.IO)` that is a child of nothing (`java/od/e0.java:175`) | Constant wakeups with no doze or screen-off awareness, on a scope that outlives everything |
 * | `queryEvents(now − 1 h, now)` on **every** tick (`java/od/o0.java:104`) | An hour of usage events walked 120 times a minute |
 * | `rawJson.contains("\"$pkg\"")` (`java/od/e0.java:130`) | A substring match on serialised JSON |
 * | `od.o0.g()` has no `try`/`catch` | A `SecurityException` after runtime revocation kills the job silently and forever |
 *
 * ### Two pending owner decisions run through this type — neither is resolved here
 *
 * **(3) `PACKAGE_USAGE_STATS`.** The monitor cannot resolve a foreground package without it, and
 * `:data`'s manifest deliberately declares no such permission on a decision's behalf. For App Lock
 * the appendix treats the grant as required and models it as something the user gives from inside
 * the App Lock flow (`docs/screens/16-app-lock.md` §1.1's `hasUsageStatsPermission`); for the
 * running-apps screen the *same* permission is pending decision 3. **That tension is reported, not
 * resolved**: nothing here assumes an outcome, and [start] is a no-op that keeps saying so while the
 * grant is missing.
 *
 * **(4) Reboot survival.** There is no `BootCompletedReceiver` and `RECEIVE_BOOT_COMPLETED` is not
 * declared, so App Lock stops at every process death and after every reboot until the user next
 * opens the app — exactly the competitor's behaviour
 * (`docs/reverse-engineering/16-app-lock.md` §4.1). This is `docs/system-architecture.md` §10.1 P6,
 * an owner fork. The implementation is written so that a boot-time start is **one class away**: a
 * receiver would call [start] and nothing else would change.
 */
interface ForegroundAppMonitor {

    /**
     * Begins watching, if every gate passes: App Lock enabled, a PIN set, a non-empty lock list and
     * usage access granted. Calling it twice is a no-op, not a second loop.
     *
     * The gates are re-evaluated continuously from their `Flow`s rather than once at start —
     * `od.e0.c()` evaluates them once per `d()` call, so a change made anywhere else is invisible
     * until somebody happens to call it again.
     */
    fun start()

    /** Stops watching and releases the screen-state registration. Idempotent. */
    fun stop()

    /**
     * Records that [packageName] was unlocked just now.
     *
     * An **explicit** record, replacing the competitor's accidental semantics: `od.e0` keeps one
     * static `String` of the last package it saw, and "unlocked" is a side effect of that dedup
     * field, so nobody can read the rule off the code and a monitor restart silently re-locks or
     * fails to re-lock (`docs/screens/16-app-lock.md` §3.5).
     */
    fun noteUnlocked(packageName: String)

    /**
     * Packages that must be locked right now. Cold, distinct, never replayed.
     *
     * Never replayed on purpose: a replayed request would re-raise the lock surface over whatever
     * the user moved to next.
     */
    val lockRequests: Flow<String>
}
