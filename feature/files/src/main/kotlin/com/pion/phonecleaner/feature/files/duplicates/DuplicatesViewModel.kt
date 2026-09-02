package com.pion.phonecleaner.feature.files.duplicates

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.FindDuplicatesUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.MimeTypeUseCase
import com.pion.phonecleaner.feature.files.component.cleanupSummaryFor
import com.pion.phonecleaner.feature.files.component.deleteConfirmSpec
import com.pion.phonecleaner.feature.files.component.restoreSelection
import com.pion.phonecleaner.feature.files.component.storeSelection
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * `duplicates` (`docs/screens/14-file-tools-and-app-manager.md` §2.2). The five-stage pipeline —
 * collect, dedupe by path, sort newest first, group by exact size, digest — lives in
 * `FindDuplicatesUseCase` behind `DuplicateFinder`: one place, one algorithm, and no dispatcher
 * named here, because the digest's bounded parallelism belongs to the finder.
 *
 * The bounded wait is `withTimeoutOrNull`, and its expiry lands with `scanTruncated` set and the
 * groups that completed still published — a `withTimeoutOrNull(4000)` that reports success is the
 * defect being removed (§2.2).
 */
class DuplicatesViewModel(
    private val savedState: SavedStateHandle,
    private val findDuplicates: FindDuplicatesUseCase,
    private val deleteFiles: DeleteFilesUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val mimeTypeOf: MimeTypeUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<DuplicatesState, DuplicatesIntent, DuplicatesEffect>(DuplicatesState(), log) {

    /** ONE scan job, cancel-and-replace. The per-file digests are its structural children. */
    private var scanJob: Job? = null

    /** What the user asked to delete, so a consent round trip re-issues exactly that. */
    private var pendingDeleteIds: Set<String> = emptySet()

    override fun onIntent(intent: DuplicatesIntent) {
        when (intent) {
            DuplicatesIntent.ScreenStarted -> onScreenStarted()
            is DuplicatesIntent.RowToggled -> saveSelection { withToggled(intent.id) }
            DuplicatesIntent.DeselectAllPressed ->
                saveSelection { copy(selectedIds = persistentSetOf()) }
            DuplicatesIntent.SelectAllOlderPressed ->
                saveSelection { copy(selectedIds = groups.olderIds()) }

            is DuplicatesIntent.RowTapped -> setState { copy(previewingId = intent.id) }
            DuplicatesIntent.PreviewDismissed -> setState { copy(previewingId = null) }
            DuplicatesIntent.PreviewConfirmed -> openPreviewed()
            DuplicatesIntent.DeletePressed -> onDeletePressed()
            DuplicatesIntent.DeleteConfirmed -> runDelete(currentState.selectedIds)
            DuplicatesIntent.DeleteDismissed -> setState { copy(confirm = null) }
            DuplicatesIntent.CompletionAnimationFinished ->
                setState { copy(phase = ToolPhase.Ready) }

            is DuplicatesIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            DuplicatesIntent.BackPressed -> onBackPressed()
        }
    }

    /** Idempotent: it arrives on every `ON_START`, so `init` observes and does not act (MVI §3). */
    private fun onScreenStarted() {
        launchSafely { markFeatureUsed(FeatureId.DuplicateFiles) }
        if (!currentState.isBusy) startScan()
    }

    private fun startScan() {
        scanJob?.cancel()
        setState {
            copy(
                phase = ToolPhase.Scanning,
                hashed = 0,
                candidates = 0,
                scanTruncated = false,
                failedCount = 0,
                error = null,
            )
        }
        scanJob = launchSafely(onError = ::onFailure) {
            val finished = withTimeoutOrNull(ScanTimeout) {
                findDuplicates().collect(::reduceScan)
                true
            }
            if (finished == null) setState { copy(phase = ToolPhase.Ready, scanTruncated = true) }
        }
    }

    private fun reduceScan(progress: DuplicateScanProgress) = when (progress) {
        is DuplicateScanProgress.Hashing ->
            setState { copy(hashed = progress.hashed, candidates = progress.candidates) }

        is DuplicateScanProgress.Finished -> setState {
            withScanFinished(progress.groups, progress.truncated, savedState.restoreSelection())
        }
    }

    private fun saveSelection(reducer: DuplicatesState.() -> DuplicatesState) {
        setState(reducer)
        savedState.storeSelection(currentState.selectedIds)
    }

    /** The MIME type is the `MediaStore` column the row carries, not a guess from the extension. */
    private fun openPreviewed() {
        val file = currentState.previewing ?: return
        val uri = (file.origin as? FileOrigin.MediaStoreEntry)?.contentUri ?: file.path
        setState { copy(previewingId = null) }
        sendEffect(DuplicatesEffect.OpenExternally(uri, mimeTypeOf(file)))
    }

    private fun onDeletePressed() {
        if (currentState.canDelete) setState { copy(confirm = deleteConfirmSpec(selectedCount)) }
    }

    private fun runDelete(ids: Set<String>) {
        val targets = currentState.groups.flatMap { it.files }.filter { it.id in ids }
        if (targets.isEmpty()) {
            setState { copy(confirm = null) }
            return
        }
        pendingDeleteIds = ids
        analytics.track(AnalyticsEvent.CleanRequested(targets.sumOf { it.sizeBytes }, targets.size))
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
                    DuplicatesEffect.NavigateToCleanResult(
                        cleanupSummaryFor(FeatureId.DuplicateFiles, outcome),
                    ),
                )
            }

            // The normal path in the default storage branch, and NOT an error (§0.2).
            is DeleteOutcome.PendingConsent -> {
                pendingDeleteIds = outcome.ids.toSet()
                setState { copy(phase = ToolPhase.Ready) }
                sendEffect(DuplicatesEffect.RequestDeleteConsent(outcome.request))
            }

            // A first-class outcome, not silence: the competitor strands its busy flag here.
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

    /** A scan the user wants to abandon is abandonable; the competitor blocks Back with two toasts. */
    private fun onBackPressed() {
        scanJob?.cancel()
        sendEffect(DuplicatesEffect.NavigateBack)
    }

    /** Lowers `phase` AND clears `confirm` — both are re-entry guards. */
    private fun onFailure(error: AppError) =
        setState { copy(phase = ToolPhase.Ready, confirm = null, error = error) }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }

    /** A bound, not a floor: the digest stops being useful long before this. */
    private companion object { val ScanTimeout = 90.seconds }
}
