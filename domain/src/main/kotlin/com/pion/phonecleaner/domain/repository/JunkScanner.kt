package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.junk.ScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * The junk engine, behind one cold `Flow`.
 *
 * It **composes** the three shared file primitives — [StorageScanner], [DirectorySizer],
 * [StorageRootProvider] — and owns no walk of its own (`docs/system-architecture.md` §4.5). Three
 * bounds the competitor has none of come with them: every walk is depth- and time-capped and
 * symlink-guarded through `WalkConfig`, the candidate list of the two determinate passes is built
 * before their sizing loop so `total` is real, and cancellation is the collector's — the walk stops
 * at its next `ensureActive()`.
 *
 * `xc.c.a` (`java/xc/c.java:15-35`) has no depth cap, no time cap, no symlink guard and no file
 * count cap; `xc.j.a()` fans out one `async` per sub-directory with nothing limiting it
 * (`docs/reverse-engineering/12-junk-cleaning.md` §12 finding 20).
 *
 * DECLARED IN `junkDataModule` (`docs/screens/12-junk-cleaning.md` §7.1). It is named by no other
 * cluster, which is what makes a per-cluster `single` legal for it (`LLM.md` §6.4).
 */
interface JunkScanner {

    /**
     * Cold. `flowOn(dispatchers.io)` inside the implementation, never chosen by the caller
     * (`LLM.md` §6.5). Cancelling the collecting job cancels the walk.
     */
    fun scan(): Flow<ScanProgress>
}
