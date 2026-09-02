package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * The only junk port a ViewModel talks to (`docs/screens/12-junk-cleaning.md` §1.3).
 *
 * `AppResult` / `AppError` per `LLM.md` §4 — an exception never crosses this boundary. The
 * competitor's entire error strategy in this cluster is `catch (Exception) { printStackTrace(); }`
 * at five sites, and **no error ever reaches the user**.
 *
 * DECLARED IN `junkDataModule`.
 */
interface JunkRepository {

    /**
     * A visible scan. Conflated here, not downsampled in the UI: `wc.e.B()` drops between 15 and 80
     * samples per magnitude bucket because it is feeding a `TextView` from a background thread,
     * while `conflate()` plus `collectAsStateWithLifecycle` drops only frames the UI could not have
     * drawn (§3.2). `ScanProgress.Finished` is terminal and carries the whole result, so conflation
     * can cost an early category reveal and never data.
     */
    fun scan(): Flow<ScanProgress>

    /** One emission per path, then one `CleanProgress.Finished`. */
    fun clean(paths: Set<String>): Flow<CleanProgress>

    /**
     * The last measured total. `null` means "never measured" or "invalidated" — which is what lets
     * the Home badge say "we have not looked" instead of "no junk found"
     * (`docs/screens/11-home.md` §1.1, Delta E6). Backed by DataStore, so the badge is correct after
     * a clean performed from anywhere.
     */
    fun cachedJunkBytes(): Flow<Long?>

    /** Single-flighted and TTL'd (§6). Concurrent callers share one walk and all receive its value. */
    suspend fun refreshEstimate(force: Boolean = false): AppResult<Long>

    /** Drops the cached total, so the badge renders "not measured" rather than a stale number. */
    suspend fun invalidateEstimate()
}
