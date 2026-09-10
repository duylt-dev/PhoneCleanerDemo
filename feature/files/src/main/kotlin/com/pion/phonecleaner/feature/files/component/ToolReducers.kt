package com.pion.phonecleaner.feature.files.component

import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile

/**
 * The pieces every one of the six tools builds identically. One copy, so the confirm wording and
 * the summary shape cannot drift between six screens — which is exactly what happened to the
 * competitor's two `when (goTag)` blocks.
 *
 * [trashEligible] picks the body: the bin's own strings when a move is plausible, "cannot be undone"
 * otherwise (plan `260908-0801-trash-bin`, Phase 07 step 7). The caller passes
 * `permissions.isGranted(AppPermission.AllFiles)`. The same choice is passed to the use case;
 * if the move cannot be made, the original stays intact and the operation reports failure.
 */
internal fun deleteConfirmSpec(count: Int, trashEligible: Boolean): ConfirmSpec = ConfirmSpec(
    titleRes = com.pion.phonecleaner.feature.files.R.string.files_confirm_delete_title,
    bodyRes = if (trashEligible) {
        com.pion.phonecleaner.feature.files.R.plurals.files_confirm_trash_body
    } else {
        com.pion.phonecleaner.feature.files.R.plurals.files_confirm_delete_body
    },
    count = count,
    confirmRes = com.pion.phonecleaner.core.ui.R.string.action_delete,
)

/**
 * `freedBytes` is a raw `Long` the whole way to the result screen. The competitor formats it, hands
 * over a display string and re-parses it there — losing about 5 % to `DecimalFormat("###.0")` and
 * defaulting an unrecognised unit to MB (`docs/system-architecture.md` §4.2).
 *
 * `MovedToTrash` takes priority over `Cleaned` only once something actually moved: an empty result is
 * `NothingFound` on either branch (plan `260908-0801-trash-bin`, Phase 07 step 6).
 */
internal fun cleanupSummaryFor(
    feature: FeatureId,
    outcome: DeleteOutcome.Deleted,
): CleanupSummary = CleanupSummary(
    feature = feature,
    freedBytes = outcome.freedBytes,
    itemCount = outcome.ids.size,
    outcome = when {
        outcome.ids.isEmpty() -> CleanupOutcome.NothingFound
        outcome.recoverable -> CleanupOutcome.MovedToTrash
        else -> CleanupOutcome.Cleaned
    },
)

/**
 * What an "Open" action hands to an external viewer: the `content://` URI the scan resolved where
 * there is one, and the absolute path otherwise.
 *
 * The branch is here rather than in a ViewModel because both walk branches produce rows now — the
 * duplicate finder yields `PlainFile` when it walked a volume and `MediaStoreEntry` when it queried
 * a collection, and a screen that assumed either one would open nothing on half of all devices.
 */
internal fun previewUriOf(file: ScannedFile): String =
    (file.origin as? FileOrigin.MediaStoreEntry)?.contentUri ?: file.path
