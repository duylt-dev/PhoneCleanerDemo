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
 */
internal fun deleteConfirmSpec(count: Int): ConfirmSpec = ConfirmSpec(
    titleRes = com.pion.phonecleaner.feature.files.R.string.files_confirm_delete_title,
    bodyRes = com.pion.phonecleaner.feature.files.R.plurals.files_confirm_delete_body,
    count = count,
    confirmRes = com.pion.phonecleaner.core.ui.R.string.action_delete,
)

/**
 * `freedBytes` is a raw `Long` the whole way to the result screen. The competitor formats it, hands
 * over a display string and re-parses it there — losing about 5 % to `DecimalFormat("###.0")` and
 * defaulting an unrecognised unit to MB (`docs/system-architecture.md` §4.2).
 */
internal fun cleanupSummaryFor(
    feature: FeatureId,
    outcome: DeleteOutcome.Deleted,
): CleanupSummary = CleanupSummary(
    feature = feature,
    freedBytes = outcome.freedBytes,
    itemCount = outcome.ids.size,
    outcome = if (outcome.ids.isEmpty()) CleanupOutcome.NothingFound else CleanupOutcome.Cleaned,
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
