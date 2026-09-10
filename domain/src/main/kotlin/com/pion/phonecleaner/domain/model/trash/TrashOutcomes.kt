package com.pion.phonecleaner.domain.model.trash

import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import kotlinx.collections.immutable.ImmutableList

/**
 * What a move achieved.
 *
 * [movedBytes] is deliberately **not** called `freedBytes`. Nothing was freed: the bytes are still on
 * the volume, in the bin. The whole of Phase 07 exists to keep that distinction out of the UI's copy
 * and out of `CleanupLedger`.
 */
data class TrashMoveOutcome(
    /** The CALLER's ids — `ScannedFile.id`, or the junk path for a directory. Not `TrashEntry.id`. */
    val movedIds: ImmutableList<String>,
    val movedBytes: Long,
    /** Paths the move could not take. Reported, never swallowed. */
    val failedPaths: ImmutableList<String>,
    /** Measured child count, for the directory case. Zero for a file batch. */
    val fileCount: Int = 0,
)

/**
 * The two-line bridge from the bin's outcome to the [DeleteOutcome] every delete screen already
 * reduces (plan `260908-0801-trash-bin`, Phase 07, key insight 2).
 *
 * [TrashMoveOutcome.failedPaths] carries over untouched, and that is load-bearing, not incidental: a
 * path this could not move is on disk exactly where it was, with no trash row for it. It is reported
 * through [DeleteOutcome.Deleted.failedPaths] the same way a path [com.pion.phonecleaner.domain.repository.FileDeleter]
 * could not remove is today — surfaced to the screen, and **never** a reason for a caller to fall
 * back to permanently deleting that path. [DeleteOutcome.Deleted.recoverable] is unconditionally
 * `true`: every byte in [TrashMoveOutcome.movedBytes] is bytes that moved into the bin, not bytes
 * that left the device.
 */
fun TrashMoveOutcome.asDeleteOutcome(): DeleteOutcome.Deleted = DeleteOutcome.Deleted(
    ids = movedIds,
    freedBytes = movedBytes,
    failedPaths = failedPaths,
    recoverable = true,
)

/**
 * What a restore achieved.
 *
 * `TrashRepository.restore` never overwrites the original path; [renamedCount] is how many landed
 * beside their original under a suffixed name instead, because the path was occupied.
 */
data class TrashRestoreOutcome(
    val restoredIds: ImmutableList<String>,
    val failedIds: ImmutableList<String>,
    /** How many landed beside their original under a suffixed name, because the path was occupied. */
    val renamedCount: Int,
)

/** [freedBytes] here IS free: the bytes leave the device at this moment and nowhere else. */
data class TrashPurgeOutcome(
    val purgedIds: ImmutableList<String>,
    val freedBytes: Long,
    val failedIds: ImmutableList<String>,
)

/**
 * A request to trash a whole directory tree as one [TrashEntry] (owner decision D6).
 *
 * The size is absent on purpose: the mover walks the tree to count files anyway, so it measures. A
 * caller-supplied size would be a second measurement free to disagree with the first.
 */
data class TrashDirectoryRequest(val path: String, val source: FeatureId)
