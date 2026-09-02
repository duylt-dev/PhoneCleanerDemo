package com.pion.phonecleaner.feature.junk.junkclean

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.JunkSessionStore
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.CleanJunkUseCase
import kotlinx.coroutines.Job

/**
 * The clean screen (`docs/screens/12-junk-cleaning.md` §5.2).
 *
 * **This is the one screen in the app where `init` may act.** The tap on "Clean Now" one screen
 * earlier *is* the decision, and there is no permission question that was not already answered. It
 * still re-reads the selection and re-checks access before the first delete: the competitor starts
 * deleting inside `onCreate` with no confirmation and no summary, and never re-checks the permission
 * at all, so revoking access between the two screens produces a silent total failure reported as a
 * total success (Deltas C1, C2).
 *
 * The ViewModel orchestrates; it does not persist. `CleanJunkUseCase` records the ledger and
 * invalidates the estimate, both in a `finally`, so a cancellation mid-loop still records what was
 * freed (MVI §3 rule 7, Delta C10).
 *
 * ANALYTICS — deliberately absent, for the same reason as `JunkScanViewModel`: §5.5 Delta C13 asks
 * for `CleanFinished(freedBytes, deletedCount, failedCount, durationMs)`, and `AnalyticsEvent` has no
 * such arm. That sealed interface lives in a file this cluster does not own and admits no
 * implementation outside its own module and package, so the arm is reported rather than faked.
 */
class JunkCleanViewModel(
    private val cleanJunk: CleanJunkUseCase,
    private val session: JunkSessionStore,
    private val permissions: PermissionRepository,
    log: AppLogger,
) : MviViewModel<JunkCleanState, JunkCleanIntent, JunkCleanEffect>(JunkCleanState(), log) {

    private var cleanJob: Job? = null

    /** `leaveIfReady()` is reachable from two places; §8.2 requires exactly one navigation. */
    private var hasNavigated = false

    init {
        start()
    }

    override fun onIntent(intent: JunkCleanIntent) {
        when (intent) {
            JunkCleanIntent.BackPressed -> onBackPressed()
            JunkCleanIntent.StopConfirmed -> onStopConfirmed()
            JunkCleanIntent.StopDismissed -> setState { copy(isStopConfirmVisible = false) }
            JunkCleanIntent.FinishAnimationEnded -> {
                setState { copy(finishAnimationEnded = true) }
                leaveIfReady()
            }

            // UNKNOWN — how the screen learns the answer. §5.1's intent set has no
            // `PermissionsResolved`, so unlike `junkscan` there is nothing for the Route's launcher
            // or a `LifecycleResumeEffect` to report back into, and the clean cannot restart itself.
            // Looked for, and not found: a resolution intent in §5.1, §5.2 or §5.5. `LLM.md` §7.4
            // says every permission-dependent screen re-checks on resume; adding the case would
            // satisfy it and is the obvious fix, but the contract here is explicit and a fabricated
            // intent is worse than a stated gap. `PermissionLost` therefore also offers Back, so the
            // user is never trapped.
            JunkCleanIntent.GrantStorageTapped -> sendEffect(JunkCleanEffect.RequestStoragePermission)
        }
    }

    private fun start() {
        val current = session.session.value
        if (current == null || current.selectedPaths.isEmpty()) {
            // The session-store route's process-death branch (§8.1).
            sendEffect(JunkCleanEffect.NavigateBack)
            return
        }
        val selected = current.selectedPaths
        val promised = current.categories.sumOf { category ->
            category.items.sumOf { item -> if (item.path in selected) item.sizeBytes else 0L }
        }
        setState { copy(promisedBytes = promised, total = selected.size) }

        if (!hasStorageAccess()) {
            setState { copy(phase = CleanPhase.PermissionLost) }
            return
        }

        cleanJob = cleanJunk(selected).collectSafely(
            onError = { error -> setState { copy(phase = CleanPhase.Failed, error = error) } },
        ) { progress -> reduce(progress) }
    }

    /**
     * PENDING OWNER DECISION — the storage branch.
     *
     * §5.2 writes this check as `permissions.isGranted(AppPermission.AllFiles)` alone. Gating solely
     * on all-files access would close off the DEFAULT branch, which is `MediaStore` + the Storage
     * Access Framework: `MANAGE_EXTERNAL_STORAGE` is **never assumed grantable**
     * (`docs/system-architecture.md` §8.1), so on the default branch that check is false for every
     * user and every clean would report `PermissionLost` before deleting anything.
     *
     * The check therefore asks the question the screen actually needs answered — *does this app still
     * hold storage access of any kind?* — which is what Delta C2 is about: access revoked between the
     * two screens. Neither branch is decided here, and a path that needs no grant at all (our own
     * cache directories) is unaffected because it was already reachable when the scan listed it.
     */
    private fun hasStorageAccess(): Boolean =
        permissions.isGranted(AppPermission.AllFiles) || permissions.isGranted(AppPermission.Storage)

    private fun reduce(progress: CleanProgress) {
        when (progress) {
            is CleanProgress.Deleted -> setState {
                copy(freedBytes = freedBytes + progress.freedBytes, processed = processed + 1)
            }

            is CleanProgress.Failed -> setState {
                copy(failedCount = failedCount + 1, processed = processed + 1)
            }

            is CleanProgress.Finished -> {
                setState { copy(phase = CleanPhase.Finished) }
                leaveIfReady()
            }
        }
    }

    private fun onBackPressed() {
        if (currentState.isCleaning) {
            setState { copy(isStopConfirmVisible = true) }
        } else {
            sendEffect(JunkCleanEffect.NavigateBack)
        }
    }

    /**
     * Cancelling mid-delete is safe **because the use case emits `Deleted` per path**: whatever was
     * freed is already folded into `freedBytes`, and the use case's `finally` still records it and
     * still invalidates the estimate.
     */
    private fun onStopConfirmed() {
        cleanJob?.cancel()
        cleanJob = null
        setState { copy(isStopConfirmVisible = false) }
        sendEffect(JunkCleanEffect.NavigateBack)
    }

    private fun leaveIfReady() {
        if (hasNavigated || !currentState.canLeave) return
        hasNavigated = true
        // BOTH numbers, always. `if (cleanedSize > 0) cleanedSize else totalSize` reports a total
        // failure as a total success, to the user and to the lifetime ledger (Delta C3).
        sendEffect(
            JunkCleanEffect.NavigateToResult(
                freedBytes = currentState.freedBytes,
                failedCount = currentState.failedCount,
            ),
        )
    }

    override fun onCleared() {
        super.onCleared()
        cleanJob?.cancel()
    }
}
