package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.device.StorageInfo
import kotlinx.coroutines.flow.Flow

/**
 * Device storage totals. Replaces `od.p0.n`/`o`/`q`; declared once in `storageDataModule` and read by
 * home, splash/onboarding and the device cluster (`docs/system-architecture.md` §5.6).
 *
 * [observe] is **pure** — it reads, it does not write. That is deliberate: the competitor's
 * `od.p0.p()` is a getter that `commit()`s inside a read
 * (`docs/screens/11-home.md:308`, delta 7).
 *
 * The day-roll counterpart of that split — `rollOverIfNewDay()` — is **not here**. The flag it rolls
 * is the "scanned today" flag, which `docs/screens/11-home.md:308` assigns to `CleanStatsRepository`
 * in `backgroundModule`. `docs/system-architecture.md` §5.6 attaches the same note to this row; the
 * two disagree, and putting a day-roll on a storage-size port would be the wrong half of `od.p0` in
 * the wrong place. Flagged, not resolved here.
 */
interface StorageInfoRepository {

    fun observe(): Flow<StorageInfo>

    /** A one-shot read, for the onboarding device check, which runs a scripted sequence, not a feed. */
    suspend fun current(): AppResult<StorageInfo>
}
