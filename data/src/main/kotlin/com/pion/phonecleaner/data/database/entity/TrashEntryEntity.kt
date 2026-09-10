package com.pion.phonecleaner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `trash_entries` — the third table. `AppDatabase`'s own KDoc carries the stated reason `LLM.md` §4
 * requires before a third table is legal; this class only holds the columns.
 *
 * Convention is `HiddenNotificationEntity`'s: snake_case table and every `@ColumnInfo`, epoch millis
 * as `Long` (no `Instant`), **no domain import** — the entity <-> model mapping lives in
 * `data/trash/TrashEntryMapping.kt`, so `:data` builds even if `:domain`'s trash package changes shape.
 *
 * [state] is a raw `String` rather than a Room-native enum column on purpose: every write site in
 * `data/trash/` passes [TrashRowState.name] explicitly, so a query that filters by state is one grep
 * away from its literal and never depends on how a given Room version binds an enum query parameter.
 * [TrashRowState] itself is the in-memory vocabulary `data/trash/` reasons about.
 */
@Entity(tableName = "trash_entries", indices = [Index(value = ["state", "expires_at"])])
data class TrashEntryEntity(
    /** Minted UUID string — see `TrashEntry.id`'s own KDoc for why it is minted, not inherited. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    /** Absolute path this restores to. */
    @ColumnInfo(name = "original_path")
    val originalPath: String,
    /** Absolute path it occupies now. Stored so a change of trash root cannot orphan the row. */
    @ColumnInfo(name = "trashed_path")
    val trashedPath: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    /** BYTES. For a directory this is the measured total at the moment it was moved. */
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    /** 1 for a file; the measured child count for a directory (owner decision D6). */
    @ColumnInfo(name = "file_count")
    val fileCount: Int,
    @ColumnInfo(name = "is_directory")
    val isDirectory: Boolean,
    /** `MediaStore`'s mime type where there was one. Passed to the media scanner on restore. */
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
    /** `FeatureId.name` — which tool put it here. Rendered as the row's second line, never for logic. */
    @ColumnInfo(name = "source_feature")
    val sourceFeature: String,
    /** The media row this came from, if any. `content://…` string, parsed back at the one call site. */
    @ColumnInfo(name = "origin_content_uri")
    val originContentUri: String?,
    /** `false` when the stale-row delete failed (`data/trash/TrashMover.clearMediaRow`). */
    @ColumnInfo(name = "media_row_cleared")
    val mediaRowCleared: Boolean,
    /** [TrashRowState.name]. */
    @ColumnInfo(name = "state")
    val state: String,
    /** [TrashEntryTypeRow.name]. */
    @ColumnInfo(name = "entry_type")
    val entryType: String = TrashEntryTypeRow.ORIGINAL.name,
    /** Delete-action group id. */
    @ColumnInfo(name = "batch_id")
    val batchId: String? = null,
    /** Small JSON payload for type-specific metadata, currently ZIP restore manifest. */
    @ColumnInfo(name = "metadata_json")
    val metadataJson: String? = null,
    /** Epoch millis. */
    @ColumnInfo(name = "trashed_at")
    val trashedAtEpochMillis: Long,
    /** Epoch millis. `TrashRetention.expiresAt(trashedAt)` at insert — stored, never recomputed. */
    @ColumnInfo(name = "expires_at")
    val expiresAtEpochMillis: Long,
)

/**
 * The two-phase-commit lifecycle (`TrashRepository`'s own KDoc): `PENDING` (row written) ->
 * physical move -> `TRASHED`, or the row is removed if the move failed. `RESTORING`/`PURGING` are the
 * same shape for the two reverse operations. [data.trash.TrashReconciler] is what a new process runs
 * to settle whatever an old one left on anything but `TRASHED`.
 */
enum class TrashRowState { PENDING, TRASHED, RESTORING, PURGING }

enum class TrashEntryTypeRow { ORIGINAL, ZIP }
