package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.domain.model.file.WalkConfig
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Duration.Companion.minutes

/**
 * The bounds every junk walk runs under, in one place.
 *
 * `WalkConfig.maxDepth` has no default — a caller must state it (`LLM.md`, `WalkConfig` KDoc) — so
 * these values have to exist somewhere. **UNKNOWN — no source fixes any of these numbers.** Looked
 * for, and not found: a bounds table in `docs/screens/12-junk-cleaning.md` §1.2 (which states that
 * the walk *is* bounded and names `WalkConfig`, `ensureActive()` and the visited-inode set, but no
 * figure), in `docs/system-architecture.md` §4.5, and anywhere in
 * `docs/reverse-engineering/12-junk-cleaning.md` — the competitor has **none** of these bounds to
 * read a number off. Each is therefore named, given a reason, and left trivially changeable.
 *
 * The competitor's equivalents: `xc.c.a` is an unbounded recursive sum with no depth cap, no time
 * cap, no symlink guard and no file-count cap, and `xc.j.a()` fans out one `async` per
 * sub-directory with nothing limiting it (`docs/reverse-engineering/12-junk-cleaning.md` §12
 * finding 20).
 */
internal object JunkWalkBounds {

    /**
     * A rule names a folder; its contents are what we are sizing. Deep enough for a real cache tree,
     * shallow enough that a symlink loop that slipped the guard still terminates.
     */
    const val RULE_DIRECTORY_MAX_DEPTH: Int = 12

    /** Whole-volume junk-file passes share the cap that has to hold on a full device. */
    const val FILE_WALK_MAX_DEPTH: Int = 16

    /**
     * Items kept per category. A cap belongs here as well as on the ViewModel: `LLM.md` §8 caps
     * *rendering*, and this caps what a scan retains at all, which is what a 200 000-directory device
     * would otherwise hand to the session store.
     */
    const val MAX_ITEMS_PER_CATEGORY: Int = 2_000

    /** `ScanProgress.Candidate.total` when the pass genuinely cannot know it (§1.2 point 2). */
    const val TOTAL_UNKNOWN: Int = -1

    /**
     * Wall-clock cap on whole-volume file walks. It exists so a device with a pathological tree
     * finishes with a partial answer instead of never finishing; the two determinate passes are
     * bounded by their candidate count instead.
     */
    private val FILE_WALK_TIME_LIMIT = 3.minutes

    /** Sizing one rule's directory. */
    fun ruleDirectory(path: String): WalkConfig = WalkConfig(
        roots = persistentListOf(path),
        maxDepth = RULE_DIRECTORY_MAX_DEPTH,
    )

    /** The `.apk` sweep over every root the grant state currently makes readable. */
    fun apkWalk(roots: List<String>): WalkConfig = WalkConfig(
        roots = roots.toImmutableList(),
        maxDepth = FILE_WALK_MAX_DEPTH,
        timeLimit = FILE_WALK_TIME_LIMIT,
    )

    /** The loose `.tmp` and `.log` sweep over every root the grant state currently makes readable. */
    fun temporaryFileWalk(roots: List<String>): WalkConfig = WalkConfig(
        roots = roots.toImmutableList(),
        maxDepth = FILE_WALK_MAX_DEPTH,
        timeLimit = FILE_WALK_TIME_LIMIT,
    )
}
