package com.pion.phonecleaner.feature.files.component

import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome

/**
 * The two pieces every one of the six tools builds identically. One copy, so the confirm wording and
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
