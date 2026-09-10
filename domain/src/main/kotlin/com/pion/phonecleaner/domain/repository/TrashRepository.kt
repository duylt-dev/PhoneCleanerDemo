package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.trash.TrashDirectoryRequest
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashMoveOutcome
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.domain.model.trash.TrashSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The bin. A **new port**, not a decorator around `FileDeleter` (engineer decision E3): `FileDeleter`
 * keeps its job — permanent deletion — because the trash screen's own "delete forever" needs it, and
 * because `RemoveFindingUseCase` must bypass the bin entirely (owner decision D5). An exception that
 * has to be visible has to be visible **at the call site**, not hidden inside a Koin binding.
 *
 * **Two-phase commit, by construction, not by caller discipline.** A trashed row's lifecycle is
 * PENDING (row written) -> physical move -> TRASHED (row updated), or the row is removed if the move
 * failed (see the plan's risk table, "A process kill between the move and the ledger write"). Every
 * method below that can create a row — [trashFiles], [trashDirectory] — owns that whole sequence
 * internally and returns only once it is settled one way or the other; there is no "begin" half a
 * caller could invoke without the matching "commit" half, and nothing in this interface exposes a
 * PENDING row for a caller to observe or act on mid-move. The only way an unsettled row can exist at
 * all is a process kill between this port's own internal steps, and [reconcile] — never a caller
 * replaying a half-finished call — is what a **new** process runs to settle whatever the old one left
 * behind.
 *
 * ### What can fail, and as what
 *
 * | Arm | When |
 * |---|---|
 * | `AppError.PermissionDenied` | all-files access was revoked between the check and the move |
 * | `AppError.Storage(path = ...)` | no writable root on that volume, `rename(2)` refused, or the row write failed |
 * | `AppError.NotFound(what = id)` | a restore or a delete named a row that is not there |
 * | `AppError.Unexpected` | anything else, wrapped rather than thrown |
 *
 * `AppError.NoNetwork` is impossible here and no caller handles it.
 */
interface TrashRepository {

    /**
     * Whether a move can be attempted **right now**: `AppPermission.AllFiles` is held and a root on
     * the file's volume is writable.
     *
     * `false` is not an error — it is engineer decision E2. Without the grant we cannot `rename(2)`
     * another app's file, and the only remaining route is copy-then-delete, which doubles peak disk
     * use on a device the user is trying to free space on. So the delete is permanent and the confirm
     * dialog says so, which is the branch `StorageAccessGate` already puts junk and duplicates in.
     *
     * A `suspend fun`, not a `Flow`: availability changes only when the grant changes, and every
     * permission-dependent screen already re-checks on resume (`LLM.md` §7.4). A `Flow` here would be
     * a second source of truth for a fact `PermissionRepository` already publishes.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Move each file, **one trash row per file, committed around each physical move** (never a
     * `CleanupLedger` row — a trashed file has not freed a byte). A cancel lands between items and
     * never inside one.
     *
     * A file whose `path` is not absolute is refused into `failedPaths` without being touched: a SAF
     * document carries its `content://` URI in `ScannedFile.path`, and there is no filesystem path to
     * rename.
     */
    suspend fun trashFiles(files: List<ScannedFile>, source: FeatureId): AppResult<TrashMoveOutcome>

    /** D6 — the whole tree is renamed once and becomes ONE entry. */
    suspend fun trashDirectory(request: TrashDirectoryRequest): AppResult<TrashMoveOutcome>

    /** Newest first, capped by the DAO. Only rows in the settled `TRASHED` state. */
    fun observeEntries(): Flow<ImmutableList<TrashEntry>>

    /** For the Settings row and the home tile badge, without loading [observeEntries]'s full list. */
    fun observeSummary(): Flow<TrashSummary>

    /** Never overwrites: an occupied original path gets a suffixed name beside it. */
    suspend fun restore(ids: List<String>): AppResult<TrashRestoreOutcome>

    suspend fun deleteForever(ids: List<String>): AppResult<TrashPurgeOutcome>

    /** All settled entries, including those beyond the display cap. Never derives ids from UI state. */
    suspend fun deleteAllForever(): AppResult<TrashPurgeOutcome>

    /** ZIP tab action: extract every file in a ZIP batch back to its original location. */
    suspend fun restoreZip(ids: List<String>): AppResult<TrashRestoreOutcome>

    /** Everything whose stored `expiresAt` has passed. Never "everything older than N" measured now. */
    suspend fun purgeExpired(): AppResult<TrashPurgeOutcome>

    /**
     * Settles rows a process kill left half-written, by asking the filesystem which side of the move
     * actually happened. Returns how many rows it resolved. Safe to call repeatedly.
     */
    suspend fun reconcile(): AppResult<Int>
}
