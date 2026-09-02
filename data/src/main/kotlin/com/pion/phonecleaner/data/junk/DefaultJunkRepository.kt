package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.domain.repository.JunkDeleter
import com.pion.phonecleaner.domain.repository.JunkRepository
import com.pion.phonecleaner.domain.repository.JunkScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach

/**
 * The one junk port a ViewModel talks to (`docs/screens/12-junk-cleaning.md` §1.3).
 *
 * It does three things and no more: conflates the scan, forwards the clean, and keeps the estimate
 * cache honest.
 */
internal class DefaultJunkRepository(
    private val scanner: JunkScanner,
    private val deleter: JunkDeleter,
    private val estimates: JunkEstimateCache,
) : JunkRepository {

    /**
     * `conflate()` is the whole of the throttling, and it lives **here** rather than in the UI.
     *
     * `wc.e` is 513 lines of `Handler` + `ExecutorService` + batch downsampling built to feed one
     * `TextView` from a background thread, and its `B()` drops between 15 and 80 samples per
     * magnitude bucket. `conflate()` plus `collectAsStateWithLifecycle` drops **only** frames the UI
     * could not have drawn (Delta S3).
     *
     * It is safe on this flow because `ScanProgress.Finished` is terminal — it is never the value a
     * later emission supersedes — and it carries the complete category list. A conflated
     * `PassFinished` therefore costs an early section reveal and never data.
     *
     * A visible scan publishes its own total, so the Home badge is correct without a second walk
     * (Delta S4).
     */
    override fun scan(): Flow<ScanProgress> = scanner.scan()
        .onEach { progress -> if (progress is ScanProgress.Finished) estimates.publish(progress.totalBytes) }
        .conflate()

    override fun clean(paths: Set<String>): Flow<CleanProgress> = deleter.delete(paths)

    override fun cachedJunkBytes(): Flow<Long?> = estimates.cachedBytes()

    override suspend fun refreshEstimate(force: Boolean): AppResult<Long> = estimates.estimate(force)

    override suspend fun invalidateEstimate() = estimates.invalidate()
}
