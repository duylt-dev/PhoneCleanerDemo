package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.JunkRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Deletes what the review screen selected, and **persists what that freed**.
 *
 * The ViewModel orchestrates; it does not persist (`docs/screens/12-junk-cleaning.md` §5.2, MVI §3
 * rule 7). Two writes belong here and nowhere else:
 *
 *  * `CleanupLedger.record(freedBytes)` — a raw `Long`, at the source. The competitor accumulates
 *    its lifetime counter by re-parsing the formatted display string the previous screen produced
 *    (`md.g4.e()`), losing about 5 % to `DecimalFormat("###.0")` and defaulting an unrecognised unit
 *    to MB. **`parseBytes` does not exist anywhere in this app.**
 *  * `JunkRepository.invalidateEstimate()` — so the Home badge shows "not measured" until something
 *    measures again. The competitor writes `flux_sp_key_home_cleaner_size = 0` unconditionally after
 *    its loop, so the badge reports "0 junk" even when every delete failed, and the re-estimate on
 *    the next line is suppressed by its own 20 s cool-down (Deltas C8, C9).
 *
 * **Both run in a `finally`**, so a clean cancelled mid-loop still records what was actually freed
 * and still invalidates. The competitor's `onDestroy` cancels the loop with no journal: the files
 * stay deleted, `cleanedSize` is lost, and the caches are never reset — its bookkeeping ends up
 * wrong in the direction that flatters it (Delta C10).
 *
 * Registered as `factoryOf(::CleanJunkUseCase)` in `domainModule` — a use case is never a `single`
 * (`LLM.md` §6.3).
 */
class CleanJunkUseCase(
    private val repository: JunkRepository,
    private val ledger: CleanupLedger,
) {

    operator fun invoke(paths: Set<String>): Flow<CleanProgress> = flow {
        var freed = 0L
        try {
            repository.clean(paths).collect { progress ->
                if (progress is CleanProgress.Deleted) freed += progress.freedBytes
                emit(progress)
            }
        } finally {
            // `withContext(NonCancellable)` is load-bearing, not defensive: a `finally` reached by
            // cancellation runs inside an already-cancelled job, where the very first suspension
            // point throws again — so without it the two writes below are exactly the journal the
            // competitor also fails to keep, just spelled in Kotlin.
            withContext(NonCancellable) {
                // `record` ignores a non-positive delta, so a clean that freed nothing writes
                // nothing; `invalidateEstimate` still runs, because paths may be gone whether or not
                // the byte count moved.
                ledger.record(freed)
                repository.invalidateEstimate()
            }
        }
    }
}
