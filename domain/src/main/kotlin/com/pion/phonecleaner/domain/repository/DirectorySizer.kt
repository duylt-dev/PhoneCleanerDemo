package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.WalkConfig

/**
 * Total bytes under a directory, **bounded**. Replaces `xc.c`
 * (`java/xc/c.java:15-35`) — an unbounded recursive sum with no depth cap, no visited-inode set, no
 * time cap and no symlink guard. Declared once in `storageDataModule` as `BoundedDirectorySizer`
 * (`docs/system-architecture.md` §5.6).
 *
 * The bounds are [WalkConfig]'s, the same ones [StorageScanner] takes, so "how deep does a sum go"
 * has one answer in the app rather than one per caller.
 */
interface DirectorySizer {
    suspend fun sizeOf(config: WalkConfig): AppResult<Long>
}
