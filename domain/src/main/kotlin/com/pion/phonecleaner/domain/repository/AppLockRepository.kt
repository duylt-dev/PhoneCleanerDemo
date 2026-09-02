package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.applock.LockableApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.Flow

/**
 * The lock list — which launcher apps are behind the PIN. Replaces the list half of the competitor's
 * `od.o0` (`docs/screens/16-app-lock.md` §1.4).
 *
 * DECLARED IN `appLockDataModule`, once. It does **not** enumerate packages itself: it composes
 * [InstalledAppsRepository], which is declared in `coreDataModule` and named by four clusters —
 * files, app-lock, notification and device. A second enumeration port here is the exact collision
 * `LLM.md` §6.4 measures.
 *
 * ### The three defects this interface exists to close
 *
 * 1. **Membership is a set test, not a substring test.** `od.e0.h(pkg)` asks
 *    `rawJson.contains("\"$pkg\"")` against the serialised `app_lock_list`
 *    (`java/od/e0.java:130`), so any quoted token that contains the query matches.
 * 2. **The list is reconciled against `PackageManager` on every read, and the pruned list is written
 *    back.** `od.o0.h()` counts installed packages over a store that is never pruned, so the stored
 *    list and the visible count disagree silently and forever
 *    (`docs/screens/16-app-lock.md` §1.5).
 * 3. **Writes suspend.** Every competitor write is `commit()` on the calling thread
 *    (`java/od/d0.java:78-81`) — a synchronous fsync on the UI thread per toggle.
 *
 * The enumeration runs on `dispatchers.default` and the write on `dispatchers.io`, **inside the
 * implementation**: a caller never names a dispatcher (`LLM.md` §6.5).
 */
interface AppLockRepository {

    /**
     * Every launcher app, label-sorted, each carrying its current [LockableApp.isLocked].
     *
     * Re-emits after every [setLocked], because that is what makes the home screen's list the
     * change notification — the competitor needs a third `LiveData` (`applockChangeObser`) to say
     * that one row moved (`docs/screens/16-app-lock.md` §1.1).
     */
    fun observeLockableApps(): Flow<ImmutableList<LockableApp>>

    /**
     * The locked package names alone, without the enumeration cost.
     *
     * `ForegroundAppMonitor` reads this and nothing else: it must not pay for a
     * `queryIntentActivities` walk to answer "is this package locked?".
     */
    fun lockedPackages(): Flow<ImmutableSet<String>>

    /** Adds or removes one package. Suspends until the write is durable. */
    suspend fun setLocked(packageName: String, locked: Boolean): AppResult<Unit>

    /**
     * Empties the lock list.
     *
     * Called only by `ClearAppLockUseCase`, together with the PIN wipe — the two must go in one
     * operation, and the confirmation must say so before it runs
     * (`docs/screens/16-app-lock.md` §4.5).
     */
    suspend fun clearLockList(): AppResult<Unit>
}
