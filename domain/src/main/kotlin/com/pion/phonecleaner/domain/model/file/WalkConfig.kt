package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Duration

/**
 * The bounds a directory walk runs under. Passed to `StorageScanner.walk`.
 *
 * The competitor has **none** of these: `xc.c.a` (`java/xc/c.java:15-35`) is an unbounded recursive
 * sum with no depth cap, no visited-inode set, no time cap and no symlink guard, and `xc.j.a()` fans
 * out one `async` per sub-directory (`docs/system-architecture.md` §4.5).
 *
 * PLACEMENT NOTE: `LLM.md` §3.6 lists `WalkConfig.kt` under `:data/storage/`. It cannot live there —
 * it is a parameter of a `:domain` repository interface, and `:domain` may not see `:data`
 * (`LLM.md` §2). It sits in the package that already owns the walk's other vocabulary. The `:data`
 * tree row is documentation drift, not a design the code can follow.
 */
data class WalkConfig(
    /** Absolute roots to walk. Supplied by `StorageRootProvider`; a scanner never picks its own. */
    val roots: ImmutableList<String>,
    /** Required, no default — the bound the competitor has no equivalent of at all. */
    val maxDepth: Int,
    /** Absolute prefixes never descended into. The competitor's 20-path skip list becomes data. */
    val excludedRoots: ImmutableList<String> = persistentListOf(),
    /**
     * Wall-clock cap on the whole walk. `null` = no cap, and a caller that passes `null` is stating
     * that its own `withTimeoutOrNull` is the bound.
     */
    val timeLimit: Duration? = null,
    /**
     * Follow symbolic links. Off by default; the `(device, inode)` visited set that makes following
     * them safe is the scanner implementation's obligation either way.
     */
    val followSymlinks: Boolean = false,
)
