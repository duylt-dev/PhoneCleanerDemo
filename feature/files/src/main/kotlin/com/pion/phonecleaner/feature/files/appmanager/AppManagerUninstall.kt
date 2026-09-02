package com.pion.phonecleaner.feature.files.appmanager

import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.app.ManagedApp
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlinx.collections.immutable.toImmutableList
import com.pion.phonecleaner.feature.files.R
import kotlinx.collections.immutable.toImmutableSet

/**
 * The serial uninstall queue (`docs/screens/14-file-tools-and-app-manager.md` §5.2), pure.
 *
 * It is a **state machine advanced by user intents, not a job**: process death simply loses the
 * queue, which is the correct outcome — the apps Android already removed stay removed, and nothing
 * resumes asking about the rest behind the user's back.
 */
internal fun AppManagerState.withQueueStarted(): AppManagerState {
    val queue = apps
        .filter { it.packageName in selectedPackages }
        .map(ManagedApp::packageName)
        .sorted()
    if (queue.isEmpty()) return copy(confirm = null)
    return copy(
        confirm = null,
        uninstalling = UninstallProgress(
            queue = queue.drop(1).toImmutableList(),
            current = queue.first(),
        ),
    )
}

/**
 * One round trip came back. `stillInstalled` is the answer from a **re-query**, never a result code
 * and never a `PACKAGE_REMOVED` broadcast: an app updating in the background fires that broadcast
 * with `EXTRA_REPLACING=true` and advances the competitor's counter (§5.5).
 *
 * `declined` is first class, so cancelling one dialog still finishes the run.
 */
internal fun AppManagerState.withUninstallReturned(
    packageName: String,
    stillInstalled: Boolean,
): AppManagerState {
    val progress = uninstalling ?: return this
    val settled = if (stillInstalled) {
        progress.copy(declined = (progress.declined + packageName).toImmutableSet())
    } else {
        progress.copy(removed = (progress.removed + packageName).toImmutableSet())
    }
    return copy(
        uninstalling = settled.copy(
            queue = settled.queue.drop(1).toImmutableList(),
            current = settled.queue.firstOrNull(),
        ),
    )
}

/** Rows Android confirmed are gone leave the list; the competitor never re-scans (§5.5). */
internal fun AppManagerState.withQueueFinished(): AppManagerState {
    val removed = uninstalling?.removed.orEmpty()
    return copy(
        phase = ToolPhase.Ready,
        apps = apps.filterNot { it.packageName in removed }.toImmutableList(),
        selectedPackages = selectedPackages
            .filterTo(mutableSetOf()) { it !in removed }
            .toImmutableSet(),
        uninstalling = null,
    )
}

/**
 * Bytes are reported from the rows Android actually removed. `totalBytes` is `0` for an app whose
 * size query was refused, so this under-reports rather than inventing a number.
 */
internal fun AppManagerState.uninstallSummary(): CleanupSummary {
    val removed = uninstalling?.removed.orEmpty()
    return CleanupSummary(
        feature = FeatureId.AppManager,
        freedBytes = apps.sumOf { if (it.packageName in removed) it.totalBytes else 0L },
        itemCount = removed.size,
        outcome = if (removed.isEmpty()) CleanupOutcome.NothingFound else CleanupOutcome.Cleaned,
    )
}

/**
 * The copy says what actually happens — Android asks about each app in turn — instead of promising
 * a single bulk removal the platform will not perform. The count goes through a `<plurals>`.
 */
internal fun uninstallConfirmSpec(count: Int): ConfirmSpec = ConfirmSpec(
    titleRes = R.string.app_manager_confirm_title,
    bodyRes = R.plurals.app_manager_confirm_body,
    count = count,
    confirmRes = R.string.app_manager_uninstall_action,
)
