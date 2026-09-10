package com.pion.phonecleaner.data.trash

import com.pion.phonecleaner.data.database.TrashSummaryRow
import com.pion.phonecleaner.data.database.entity.TrashEntryEntity
import com.pion.phonecleaner.data.database.entity.TrashEntryTypeRow
import com.pion.phonecleaner.data.database.entity.TrashRowState
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashEntryKind
import com.pion.phonecleaner.domain.model.trash.TrashEntryType
import com.pion.phonecleaner.domain.model.trash.TrashSummary
import com.pion.phonecleaner.domain.policy.TrashRetention
import kotlin.time.Instant

/** Entity -> model, both directions live here so a column and a field can never drift apart unseen. */
internal fun TrashEntryEntity.toModel(): TrashEntry = TrashEntry(
    id = id,
    originalPath = originalPath,
    trashedPath = trashedPath,
    displayName = displayName,
    sizeBytes = sizeBytes,
    fileCount = fileCount,
    kind = if (isDirectory) TrashEntryKind.Directory else TrashEntryKind.File,
    mimeType = mimeType,
    // A row's source_feature that no longer matches a FeatureId constant (a removed/renamed feature)
    // falls back to Trash itself — the row is still valid and restorable; only the "trashed by" line
    // is imprecise, which is strictly better than refusing to show a file the user can still recover.
    source = FeatureId.entries.firstOrNull { it.name == sourceFeature } ?: FeatureId.Trash,
    type = if (entryType == TrashEntryTypeRow.ZIP.name) TrashEntryType.Zip else TrashEntryType.Original,
    batchId = batchId,
    trashedAt = Instant.fromEpochMilliseconds(trashedAtEpochMillis),
    expiresAt = Instant.fromEpochMilliseconds(expiresAtEpochMillis),
)

internal fun TrashSummaryRow.toModel(): TrashSummary = TrashSummary(entryCount, totalBytes)

/**
 * A freshly minted `PENDING` row, at the instant a move is attempted. [TrashCommit] is the only
 * caller: every row starts `PENDING` and is either promoted to `TRASHED` or deleted, per the
 * two-phase-commit rule on `TrashRepository`'s own KDoc.
 */
internal fun newPendingTrashEntry(
    id: String,
    originalPath: String,
    trashedPath: String,
    displayName: String,
    sizeBytes: Long,
    fileCount: Int,
    isDirectory: Boolean,
    mimeType: String?,
    source: FeatureId,
    originContentUri: String?,
    entryType: TrashEntryTypeRow = TrashEntryTypeRow.ORIGINAL,
    batchId: String? = null,
    metadataJson: String? = null,
    trashedAt: Instant,
): TrashEntryEntity = TrashEntryEntity(
    id = id,
    originalPath = originalPath,
    trashedPath = trashedPath,
    displayName = displayName,
    sizeBytes = sizeBytes,
    fileCount = fileCount,
    isDirectory = isDirectory,
    mimeType = mimeType,
    sourceFeature = source.name,
    originContentUri = originContentUri,
    // No media row to clear yet — corrected to false only if a post-move clear actually fails.
    mediaRowCleared = true,
    state = TrashRowState.PENDING.name,
    entryType = entryType.name,
    batchId = batchId,
    metadataJson = metadataJson,
    trashedAtEpochMillis = trashedAt.toEpochMilliseconds(),
    expiresAtEpochMillis = TrashRetention.expiresAt(trashedAt).toEpochMilliseconds(),
)
