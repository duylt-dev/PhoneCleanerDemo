package com.pion.phonecleaner.domain.model.trash

import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlin.time.Instant

/**
 * One thing in the bin.
 *
 * No `@Immutable`: `:domain` is compiled without the Compose plugin (`LLM.md` §2), so the annotation
 * would not resolve. `compose-stability.conf` already declares `com.pion.phonecleaner.domain.model.*`
 * stable, which makes every field below a promise — all `val`, no collection, nothing mutated after
 * construction. `ComposeStabilityReportTest` is what keeps that honest.
 *
 * **[id] is minted, not inherited.** `ScannedFile.id` is an absolute path for a walked file and a
 * `content://` string for a MediaStore row, and BOTH go stale the instant the file moves — the path
 * changes and the media row is removed. The restore target is [originalPath]; the caller's own id is
 * returned separately, in `TrashMoveOutcome.movedIds`, because five ViewModels prune their lists with
 * `item.id in outcome.ids`.
 *
 * **[expiresAt] is stored rather than computed on read.** The DAO has to answer
 * `WHERE expires_at <= :now` — that query is the entire reason this is a Room table and not a
 * DataStore key (Phase 03's `AppDatabase`). It also means changing the retention window later cannot
 * shorten the promise already made to an entry sitting in the bin.
 */
data class TrashEntry(
    /** A minted UUID string. The row key, the `LazyColumn` key and the selection element. */
    val id: String,
    /** Absolute path this restores to. Never a `content://` string — see [TrashEntryKind]. */
    val originalPath: String,
    /** Absolute path it occupies now. Stored, so a change of trash root cannot orphan the row. */
    val trashedPath: String,
    /** What the user reads. The file name, or the directory name for a [TrashEntryKind.Directory]. */
    val displayName: String,
    /** BYTES. For a directory this is the measured total of the tree at the moment it was moved. */
    val sizeBytes: Long,
    /** 1 for a file; the measured child count for a directory (owner decision D6). */
    val fileCount: Int,
    val kind: TrashEntryKind,
    /** `MediaStore`'s mime type where there was one. Passed to the media scanner on restore. */
    val mimeType: String? = null,
    /** Which tool put it here. Rendered as the row's second line; never used for logic. */
    val source: FeatureId,
    /** Original rows and ZIP-batch rows share the same screen, but are acted on independently. */
    val type: TrashEntryType = TrashEntryType.Original,
    /** Rows created by the same user delete action share this value. */
    val batchId: String? = null,
    val trashedAt: Instant,
    val expiresAt: Instant,
)

/**
 * **A directory is one entry** (owner decision D6): the junk cleaner selects folders, and one row per
 * file would turn a single "clear app residue" tap into hundreds of rows nobody can act on. The cost
 * is stated rather than hidden: restoring one file out of a directory entry is not supported, and the
 * screen never offers it.
 */
enum class TrashEntryKind { File, Directory }

enum class TrashEntryType { Original, Zip }
