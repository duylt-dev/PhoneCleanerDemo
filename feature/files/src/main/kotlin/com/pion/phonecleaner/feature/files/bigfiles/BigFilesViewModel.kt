package com.pion.phonecleaner.feature.files.bigfiles

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.FileScanProgress
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.MimeTypeUseCase
import com.pion.phonecleaner.domain.usecase.ScanBigFilesUseCase
import com.pion.phonecleaner.feature.files.component.cleanupSummaryFor
import com.pion.phonecleaner.feature.files.component.deleteConfirmSpec
import com.pion.phonecleaner.feature.files.component.restoreSelection
import com.pion.phonecleaner.feature.files.component.storeSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * `bigfiles` (`docs/screens/14-file-tools-and-app-manager.md` §1.2).
 *
 * **No dispatcher is named here.** `withContext` lives inside `StorageScanner` and
 * `MediaStoreRepository`, both of which take `DispatcherProvider`; it is call-site dispatcher freedom
 * that put one competitor ViewModel on `Default` while its four siblings ran on IO.
 *
 * **There is no 4 000 ms floor.** `scannedCount` is real progress; the floor existed to fill an
 * ad-preload window.
 *
 * The bounded wait is `withTimeoutOrNull`, never `withTimeout`, and its expiry lands in the same
 * place as success with `scanTruncated` set — the competitor's audio engine truncates at 10 s and
 * **reports success**.
 */
class BigFilesViewModel(
    private val savedState: SavedStateHandle,
    private val scanBigFiles: ScanBigFilesUseCase,
    private val deleteFiles: DeleteFilesUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val mimeTypeOf: MimeTypeUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<BigFilesState, BigFilesIntent, BigFilesEffect>(BigFilesState(), log) {

    /** ONE scan job, cancel-and-replace. The source stages are its structural children. */
    private var scanJob: Job? = null

    /** What the user asked to delete, so a consent round trip re-issues exactly that. */
    private var pendingDeleteIds: Set<String> = emptySet()

    override fun onIntent(intent: BigFilesIntent) {
        when (intent) {
            BigFilesIntent.ScreenStarted -> onScreenStarted()
            is BigFilesIntent.RowToggled -> saveSelection { copy(files = files.toggle(intent.id)) }
            BigFilesIntent.SelectAllToggled -> saveSelection {
                copy(files = if (files.isAllSelected) files.clearSelection() else files.selectAll())
            }
            is BigFilesIntent.RowOpened -> openRow(intent.id)
            BigFilesIntent.DeletePressed -> onDeletePressed()
            BigFilesIntent.DeleteConfirmed -> runDelete(currentState.files.selectedIds)
            BigFilesIntent.DeleteDismissed -> setState { copy(confirm = null) }
            BigFilesIntent.CompletionAnimationFinished -> setState { copy(phase = ToolPhase.Ready) }
            is BigFilesIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            BigFilesIntent.GrantMoreAccessPressed -> sendEffect(BigFilesEffect.RequestStorageTree)
            BigFilesIntent.BackPressed -> onBackPressed()
        }
    }

    /**
     * Idempotent: this arrives on every `ON_START` — including the return from the SAF tree picker,
     * which is why the re-scan lives here and not in `init` (MVI §3: `init` observes, it does not
     * act).
     */
    private fun onScreenStarted() {
        launchSafely { markFeatureUsed(FeatureId.BigFiles) }
        if (currentState.isBusy) return
        startScan()
    }

    private fun startScan() {
        scanJob?.cancel()
        setState {
            copy(
                phase = ToolPhase.Scanning,
                scannedCount = 0,
                scanTruncated = false,
                failedCount = 0,
                error = null,
            )
        }
        scanJob = launchSafely(onError = ::onFailure) {
            val finished = withTimeoutOrNull(ScanTimeout) {
                scanBigFiles().collect(::reduceScan)
                true
            }
            if (finished == null) setState { copy(phase = ToolPhase.Ready, scanTruncated = true) }
        }
    }

    private fun reduceScan(progress: FileScanProgress) = when (progress) {
        is FileScanProgress.Scanning -> setState { copy(scannedCount = progress.scannedCount) }
        is FileScanProgress.Finished ->
            setState { withScanFinished(progress, savedState.restoreSelection()) }
    }

    private fun saveSelection(reducer: BigFilesState.() -> BigFilesState) {
        setState(reducer)
        savedState.storeSelection(currentState.files.selectedIds)
    }

    private fun openRow(id: String) {
        val file = currentState.files.items.firstOrNull { it.id == id } ?: return
        val uri = (file.origin as? FileOrigin.MediaStoreEntry)?.contentUri ?: file.path
        sendEffect(BigFilesEffect.OpenFile(uri, mimeTypeOf(file)))
    }

    private fun onDeletePressed() {
        if (currentState.canDelete) setState { copy(confirm = deleteConfirmSpec(selectedCount)) }
    }

    private fun runDelete(ids: Set<String>) {
        val targets = currentState.files.items.filter { it.id in ids }
        if (targets.isEmpty()) {
            setState { copy(confirm = null) }
            return
        }
        pendingDeleteIds = ids
        analytics.track(
            AnalyticsEvent.CleanRequested(targets.sumOf { it.sizeBytes }, targets.size),
        )
        setState { copy(confirm = null, phase = ToolPhase.Deleting, failedCount = 0) }
        launchSafely(onError = ::onFailure) {
            when (val result = deleteFiles(targets)) {
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success -> reduceDelete(result.value, targets.size)
            }
        }
    }

    private fun reduceDelete(outcome: DeleteOutcome, requested: Int) {
        when (outcome) {
            is DeleteOutcome.Deleted -> {
                pendingDeleteIds = emptySet()
                savedState.storeSelection(emptySet())
                setState { withDeleted(outcome) }
                sendEffect(
                    BigFilesEffect.NavigateToCleanResult(
                        cleanupSummaryFor(FeatureId.BigFiles, outcome),
                    ),
                )
            }

            // The normal path in the default storage branch, and NOT an error (§0.2).
            is DeleteOutcome.PendingConsent -> {
                pendingDeleteIds = outcome.ids.toSet()
                setState { copy(phase = ToolPhase.Ready) }
                sendEffect(BigFilesEffect.RequestDeleteConsent(outcome.request))
            }

            // A first-class outcome, not silence: the competitor's delete returns without invoking
            // its callback here, stranding its busy flag forever.
            DeleteOutcome.NothingResolved -> {
                pendingDeleteIds = emptySet()
                setState { copy(phase = ToolPhase.Ready, failedCount = requested) }
            }
        }
    }

    private fun onConsentResult(granted: Boolean) {
        val ids = pendingDeleteIds
        pendingDeleteIds = emptySet()
        if (granted && ids.isNotEmpty()) runDelete(ids) else setState { copy(phase = ToolPhase.Ready) }
    }

    /** A scan the user wants to abandon is abandonable. The competitor blocks Back with two toasts. */
    private fun onBackPressed() {
        scanJob?.cancel()
        sendEffect(BigFilesEffect.NavigateBack)
    }

    /** Lowers `phase` AND clears `confirm` — both are re-entry guards (§1.2). */
    private fun onFailure(error: AppError) {
        setState { copy(phase = ToolPhase.Ready, confirm = null, error = error) }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }

    /** A bound, not a floor: the walk stops being useful long before this. */
    private companion object { val ScanTimeout = 60.seconds }
}
