package com.pion.phonecleaner.data.applock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.datastore.AppLockPrefs
import com.pion.phonecleaner.domain.model.applock.LockableApp
import com.pion.phonecleaner.domain.repository.AppLockRepository
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The lock list, reconciled against what is actually installed.
 *
 * DECLARED IN `appLockDataModule`. It **composes** [InstalledAppsRepository] rather than
 * enumerating packages itself: that port is declared once in `coreDataModule` and named by four
 * clusters — files, app-lock, notification and device — and a second enumeration here is exactly the
 * duplicate-`single` collision `LLM.md` §6.4 measures (`docs/screens/16-app-lock.md` §1.4).
 *
 * ### The three competitor defects it closes
 *
 * 1. **Membership is a set test.** `od.e0.h(pkg)` asks `rawJson.contains("\"$pkg\"")` against the
 *    serialised `app_lock_list` (`java/od/e0.java:130`) — a substring match on JSON, so a quoted
 *    token containing the query matches. Here the store *is* a `Set<String>`.
 * 2. **The list is pruned, and the pruned list is written back.** `od.o0.h()` counts installed
 *    packages over a store that is never pruned (`java/od/o0.java:116-128`), so the stored list and
 *    the visible count disagree silently and forever. [observeLockableApps] drops entries whose
 *    package is gone **and persists that**, so the count and the store cannot drift.
 * 3. **The enumeration is not a one-shot.** `Chaennia.d()` runs once per screen open, so an app
 *    installed while the screen is open never appears
 *    (`docs/reverse-engineering/16-app-lock.md` §4.4). This is a `Flow` that re-derives on every
 *    change to the stored set.
 *
 * The enumeration runs on `dispatchers.default` and writes on `dispatchers.io`, both chosen here and
 * never by a caller (`LLM.md` §6.5).
 */
internal class DataStoreAppLockRepository(
    private val dataStore: DataStore<Preferences>,
    private val installedApps: InstalledAppsRepository,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : AppLockRepository {

    override fun observeLockableApps(): Flow<ImmutableList<LockableApp>> =
        storedPackages()
            .map { stored -> reconcile(stored) }
            .flowOn(dispatchers.default)

    override fun lockedPackages(): Flow<ImmutableSet<String>> =
        storedPackages().map { it.toImmutableSet() }

    override suspend fun setLocked(packageName: String, locked: Boolean): AppResult<Unit> =
        write("setLocked") { current ->
            if (locked) current + packageName else current - packageName
        }

    override suspend fun clearLockList(): AppResult<Unit> = write("clearLockList") { emptySet() }

    /** The raw stored set. `distinctUntilChanged` so an unrelated preference write is not a re-scan. */
    private fun storedPackages(): Flow<Set<String>> =
        dataStore.data
            .map { it[AppLockPrefs.LOCKED_PACKAGES].orEmpty() }
            .distinctUntilChanged()

    /**
     * Builds the visible list and prunes the store in the same pass.
     *
     * The write-back cannot loop: it runs only when [stored] actually contains a package that is no
     * longer installed, and after the write the flow re-emits a set for which the condition is
     * false.
     */
    private suspend fun reconcile(stored: Set<String>): ImmutableList<LockableApp> {
        val installed = installedApps.installedApps().let { result ->
            when (result) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> {
                    // An enumeration failure is not a reason to report "no apps are locked": the
                    // screen shows an empty list and its own error, and the store is left alone.
                    log.e { "App Lock could not enumerate installed apps: ${result.error}" }
                    return persistentListOf()
                }
            }
        }
        val installedNames = installed.mapTo(mutableSetOf()) { it.packageName }
        val pruned = stored.intersect(installedNames)
        if (pruned.size != stored.size) prune(pruned)
        return installed
            .map { app -> LockableApp(app.packageName, app.label, app.packageName in pruned) }
            .sortedBy { it.label.lowercase() }
            .toImmutableList()
    }

    private suspend fun prune(pruned: Set<String>) {
        runCatching { dataStore.edit { it[AppLockPrefs.LOCKED_PACKAGES] = pruned } }
            .onFailure { log.e(it) { "App Lock could not prune the lock list" } }
    }

    private suspend fun write(what: String, transform: (Set<String>) -> Set<String>): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[AppLockPrefs.LOCKED_PACKAGES] =
                        transform(prefs[AppLockPrefs.LOCKED_PACKAGES].orEmpty())
                }
                Unit
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = {
                    log.e(it) { "App Lock $what failed" }
                    AppResult.Failure(AppError.Unexpected(it.message))
                },
            )
        }
}
