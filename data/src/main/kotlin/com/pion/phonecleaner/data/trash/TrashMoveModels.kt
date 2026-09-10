package com.pion.phonecleaner.data.trash

/** What [TrashMover.moveIn] achieved. */
internal sealed interface MoveResult {
    data class Moved(val trashedPath: String) : MoveResult

    /** The source is untouched — never deleted, never copied. The caller reports the path as failed. */
    data object Refused : MoveResult
}

/** What [TrashMover.moveOut] achieved. */
internal sealed interface RestoreResult {
    data class Restored(val path: String, val renamed: Boolean) : RestoreResult
    data object Failed : RestoreResult
}

/** Size + child count of a directory tree, measured once at move time (`TrashMover.measure`). */
internal data class Measured(val sizeBytes: Long, val fileCount: Int)

/** A partial directory delete frees bytes too, but must keep its recoverable remainder in the bin. */
internal data class PurgeResult(val freedBytes: Long, val removed: Boolean)
