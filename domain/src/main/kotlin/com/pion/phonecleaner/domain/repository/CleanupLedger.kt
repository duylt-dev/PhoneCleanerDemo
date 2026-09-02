package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The lifetime "bytes freed" counter. One binding, in `coreDataModule`; two clusters declared it
 * (`docs/system-architecture.md` §4.1, §5.4).
 *
 * **The ledger is fed at the source, not at the result screen**: the use case that actually freed the
 * bytes calls [record] with a raw `Long`. The competitor accumulates its lifetime counter by
 * re-parsing the formatted display string the previous screen produced — `md.g4.e()`
 * (`java/md/g4.java:41-109`) loses about 5 % to `DecimalFormat("###.0")` and defaults an
 * unrecognised unit to MB. **`parseBytes` does not exist anywhere in this app**
 * (`docs/system-architecture.md` §4.2, `docs/screens/14-file-tools-and-app-manager.md:926`).
 */
interface CleanupLedger {

    /** Adds [freedBytes] to the lifetime total. Raw bytes, never a formatted string. */
    suspend fun record(freedBytes: Long): AppResult<Unit>

    /**
     * The lifetime total, for the home footer's `lifetimeSavedBytes`
     * (`docs/screens/11-home.md:115`, the competitor's `md.g4.c()` over
     * `flux_sp_key_total_saved_bytes`).
     *
     * UNKNOWN — no appendix names the read accessor; only the write half (`record`) is stated. The
     * name is chosen here, and the shape is a `Flow` because home renders it in state that must
     * update after a clean without the screen re-entering.
     */
    fun observeLifetimeFreedBytes(): Flow<Long>
}
