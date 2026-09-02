package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import kotlinx.collections.immutable.ImmutableList

/**
 * **The only object that knows which roots are readable right now.**
 *
 * That is the whole point of it: a scanner asks this, and a scanner never calls
 * `Environment.getExternalStorageDirectory()` (`docs/system-architecture.md` §8.4). What it returns
 * changes with the storage branch and with which SAF trees the user has granted — and neither a
 * screen, a ViewModel nor a use case ever learns which branch is active (§8.4).
 *
 * The competitor caches its storage root in a static forever and is single-volume (`xc.z`).
 *
 * Persisted SAF grants are re-validated on read: `takePersistableUriPermission` survives a reboot but
 * not an uninstall or a user revocation, so an invalid tree is dropped rather than returned as an
 * empty success (§8.4).
 */
interface StorageRootProvider {

    /** Absolute paths currently walkable. Feeds `WalkConfig.roots`. */
    suspend fun readableRoots(): AppResult<ImmutableList<String>>

    /**
     * Which surfaces the current grant state actually covers, so a scan result can say what it did
     * NOT look at. **The default branch cannot see everything and must say so** — the competitor's
     * file-reputation scan silently degrades to installed packages only and reports nothing about it
     * (`docs/system-architecture.md` §8.4 consequence 1).
     */
    suspend fun coveredSurfaces(): AppResult<ImmutableList<String>>
}
