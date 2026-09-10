package com.pion.phonecleaner.feature.trash

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.DeleteTrashForeverUseCase
import com.pion.phonecleaner.domain.usecase.ObserveTrashSummaryUseCase
import com.pion.phonecleaner.domain.usecase.ObserveTrashUseCase
import com.pion.phonecleaner.domain.usecase.ReconcileTrashUseCase
import com.pion.phonecleaner.domain.usecase.RestoreFromTrashUseCase
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

/**
 * Room is the single source of truth: action results never write entries back into the list.
 * The selection displayed by a confirmation is snapshotted before any asynchronous work starts;
 * a Room emission or queued tap cannot silently change which files that confirmation deletes.
 */
class TrashViewModel(
    observeTrash: ObserveTrashUseCase,
    observeTrashSummary: ObserveTrashSummaryUseCase,
    private val restoreFromTrash: RestoreFromTrashUseCase,
    private val deleteTrashForever: DeleteTrashForeverUseCase,
    private val reconcileTrash: ReconcileTrashUseCase,
    private val permissions: PermissionRepository,
    private val clock: AppClock,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<TrashState, TrashIntent, TrashEffect>(TrashState(), log) {

    init {
        observeTrash().collectSafely(onError = ::onLoadFailure) { entries ->
            setState {
                copy(entries = entries, now = clock.now(),
                    selectedIds = selectedIds.intersect(entries.map { it.id }.toSet()).toImmutableSet())
            }
        }
        observeTrashSummary().collectSafely(onError = ::onLoadFailure) { summary ->
            setState { copy(totalBytes = summary.totalBytes, totalEntries = summary.entryCount) }
        }
    }

    override fun onIntent(intent: TrashIntent) {
        when (intent) {
            TrashIntent.ScreenStarted -> onScreenStarted()
            TrashIntent.ScreenResumed -> onScreenResumed()
            is TrashIntent.EntryToggled -> setState { withToggled(intent.id) }
            TrashIntent.SelectAllToggled -> setState { withAllToggled() }
            TrashIntent.RestorePressed -> confirm(TrashAction.Restore)
            TrashIntent.DeleteForeverPressed -> confirm(TrashAction.DeleteForever)
            TrashIntent.EmptyBinPressed -> confirm(TrashAction.EmptyAll)
            TrashIntent.ConfirmAccepted -> onConfirmAccepted()
            TrashIntent.ConfirmDismissed -> if (currentState.phase != TrashPhase.Working) setState { withoutConfirmation() }
            TrashIntent.AllowAccessPressed -> sendEffect(TrashEffect.RequestAllFilesAccess)
            TrashIntent.BackPressed -> sendEffect(TrashEffect.NavigateBack)
        }
    }

    /** A second ScreenStarted while loading cannot create a second reconcile writer. */
    private fun onScreenStarted() {
        if (currentState.isReconciling || currentState.phase == TrashPhase.Working) return
        if (currentState.phase != TrashPhase.Loading && currentState.error == null) return
        setState { copy(phase = TrashPhase.Loading, isReconciling = true, error = null) }
        launchSafely(onError = ::onLoadFailure) {
            when (val result = timedTrashOperation(RECONCILE_TIMEOUT_MILLIS) { reconcileTrash() }) {
                is AppResult.Success -> setState { copy(phase = TrashPhase.Ready, isReconciling = false, error = null) }
                is AppResult.Failure -> onLoadFailure(result.error)
            }
        }
    }

    /** Reconcile failures are drawn inline, action failures have a list to return to and use effects. */
    private fun onLoadFailure(error: AppError) {
        setState { copy(phase = TrashPhase.Ready, isReconciling = false, error = error) }
    }

    private fun onScreenResumed() {
        val available = permissions.isGranted(AppPermission.AllFiles)
        setState {
            val updated = copy(isTrashAvailable = available, now = clock.now())
            if (!available && phase != TrashPhase.Working) updated.withoutConfirmation() else updated
        }
    }

    private fun confirm(action: TrashAction) {
        if (action == TrashAction.EmptyAll && !currentState.canEmptyBin) return
        if (action != TrashAction.EmptyAll && !currentState.canAct) return
        setState {
            copy(pendingAction = action, pendingIds = selectedIds, confirm = when (action) {
                TrashAction.Restore -> restoreConfirmSpec(selectedCount)
                TrashAction.DeleteForever -> deleteForeverConfirmSpec(selectedCount)
                TrashAction.EmptyAll -> emptyBinConfirmSpec(totalEntries)
            })
        }
    }

    /** Re-check permission at acceptance: a dialog can remain open while the grant is revoked. */
    private fun onConfirmAccepted() {
        if (currentState.phase != TrashPhase.Ready || currentState.confirm == null) return
        val action = currentState.pendingAction ?: return
        if (!permissions.isGranted(AppPermission.AllFiles)) {
            onScreenResumed()
            return
        }
        val ids = currentState.pendingIds.toList()
        if (action != TrashAction.EmptyAll && ids.isEmpty()) {
            setState { withoutConfirmation() }
            return
        }
        setState { copy(phase = TrashPhase.Working, confirm = null) }
        when (action) {
            TrashAction.Restore -> runRestore(ids)
            TrashAction.DeleteForever, TrashAction.EmptyAll -> runDelete(ids, action == TrashAction.EmptyAll)
        }
    }

    private fun runRestore(ids: List<String>) {
        launchSafely(onError = ::onActionFailure) {
            when (val result = timedTrashOperation { restoreFromTrash(ids) }) {
                is AppResult.Success -> onRestored(result.value)
                is AppResult.Failure -> onActionFailure(result.error)
            }
        }
    }

    private fun onRestored(outcome: TrashRestoreOutcome) {
        finishAction()
        sendEffect(TrashEffect.ShowRestored(outcome.restoredIds.size, outcome.renamedCount, outcome.failedIds.size))
    }

    private fun runDelete(ids: List<String>, all: Boolean) {
        launchSafely(onError = ::onActionFailure) {
            // Purge + ledger credit may be NonCancellable: wait for it instead of promising that a
            // timeout interrupts a committed deletion. EmptyAll reads all rows in the repository.
            when (val result = if (all) deleteTrashForever.all() else deleteTrashForever(ids)) {
                is AppResult.Success -> onDeleted(result.value)
                is AppResult.Failure -> onActionFailure(result.error)
            }
        }
    }

    private fun onDeleted(outcome: TrashPurgeOutcome) {
        finishAction()
        if (outcome.failedIds.isNotEmpty()) sendEffect(TrashEffect.ShowDeleteFailures(outcome.failedIds.size))
    }

    private fun finishAction() {
        setState { withoutConfirmation().copy(phase = TrashPhase.Ready, selectedIds = persistentSetOf()) }
    }

    /** Lower every re-entry guard, including when a collaborator throws outside AppResult. */
    private fun onActionFailure(error: AppError) {
        setState { withoutConfirmation().copy(phase = TrashPhase.Ready) }
        sendEffect(TrashEffect.ShowMessage(error))
    }

    internal companion object {
        /** Mirrors TrashEntryDao's visible-row limit; EmptyAll deliberately does not use it. */
        const val MAX_ROWS = 500
    }
}
