package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList

/**
 * What a bounded file scan reports while it runs.
 *
 * The competitor covers its scan with a **4 000 ms floor** (`od.q0.a`) and shows no progress at all;
 * the floor exists to fill an ad-preload window, and it is the reason a 200 ms scan still costs four
 * seconds (`docs/screens/14-file-tools-and-app-manager.md` §1.4). [Scanning] is the replacement:
 * real progress, emitted by the walk itself.
 *
 * [Finished] is emitted exactly once, and it is emitted **even when the scan hit its time budget** —
 * with [truncated] set. The competitor's audio engine arms a 10 s watchdog, stops the cursor loop
 * mid-way and reports success, which is a silently wrong answer rather than a slow one (§4).
 */
sealed interface FileScanProgress {

    /** How many files the walk has looked at so far. Not how many it kept. */
    data class Scanning(val scannedCount: Int) : FileScanProgress

    data class Finished(
        val files: ImmutableList<ScannedFile>,
        val coverage: ScanCoverage,
        /** The budget expired before the walk ended; [files] is what completed, not a failure. */
        val truncated: Boolean = false,
    ) : FileScanProgress
}
