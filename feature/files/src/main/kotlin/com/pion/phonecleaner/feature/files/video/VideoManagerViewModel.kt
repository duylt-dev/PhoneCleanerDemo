package com.pion.phonecleaner.feature.files.video

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.LoadVideosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.MimeTypeUseCase
import com.pion.phonecleaner.feature.files.component.MediaAccess
import com.pion.phonecleaner.feature.files.component.cleanupSummaryFor
import com.pion.phonecleaner.feature.files.component.deleteConfirmSpec
import com.pion.phonecleaner.feature.files.component.restoreSelection
import com.pion.phonecleaner.feature.files.component.selectableFiles
import com.pion.phonecleaner.feature.files.component.storeSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * `video` (`docs/screens/14-file-tools-and-app-manager.md` §3.2).
 *
 * **`init` observes nothing.** The load starts from `PermissionResolved(Granted | Partial)`, never
 * from the constructor — MVI §3, *"work that needs a permission starts from an intent"*. The
 * competitor gates the screen from outside and never starts the Activity when the check fails, which
 * is why it has no permission state at all.
 */
class VideoManagerViewModel(
    private val savedState: SavedStateHandle,
    private val loadVideos: LoadVideosUseCase,
    private val deleteFiles: DeleteFilesUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val mimeTypeOf: MimeTypeUseCase,
    private val analytics: AnalyticsRepository,
    private val permissions: PermissionRepository,
    log: AppLogger,
) : MviViewModel<VideoManagerState, VideoManagerIntent, VideoManagerEffect>(
    VideoManagerState(),
    log,
) {

    /** ONE load job, cancel-and-replace. Never a field the code cancels by hand elsewhere. */
    private var loadJob: Job? = null

    /** What the user asked to delete, so a consent round trip re-issues exactly that. */
    private var pendingDeleteIds: Set<String> = emptySet()

    override fun onIntent(intent: VideoManagerIntent) {
        when (intent) {
            VideoManagerIntent.ScreenStarted ->
                launchSafely { markFeatureUsed(FeatureId.VideoManager) }

            is VideoManagerIntent.PermissionResolved -> onAccess(intent.access)
            VideoManagerIntent.GrantMorePressed ->
                sendEffect(VideoManagerEffect.RequestMediaPermission)

            is VideoManagerIntent.FolderSelected -> setState { copy(selectedFolderPath = intent.folderPath) }
            is VideoManagerIntent.SortSelected -> setState { withSort(intent.sort) }
            is VideoManagerIntent.RowToggled ->
                saveSelection { copy(files = files.toggle(intent.id)) }

            VideoManagerIntent.SelectAllToggled -> saveSelection {
                copy(files = if (files.isAllSelected) files.clearSelection() else files.selectAll())
            }

            is VideoManagerIntent.RowOpened -> openRow(intent.id)
            VideoManagerIntent.DeletePressed -> onDeletePressed()
            VideoManagerIntent.DeleteConfirmed -> runDelete(currentState.files.selectedIds)
            VideoManagerIntent.DeleteDismissed -> setState { copy(confirm = null) }
            VideoManagerIntent.CompletionAnimationFinished ->
                setState { copy(phase = ToolPhase.Ready) }

            is VideoManagerIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            VideoManagerIntent.BackPressed -> {
                loadJob?.cancel()
                sendEffect(VideoManagerEffect.NavigateBack)
            }
        }
    }

    /**
     * Arrives on every `ON_START`, including the return from the system dialog and from Settings —
     * which no app is given a result for. `Denied` does NOT re-prompt on its own: the screen renders
     * its permission state and the user presses *Allow access*. Re-raising the dialog here would
     * loop against a permanent denial, which the platform answers by never showing it again.
     */
    private fun onAccess(access: MediaAccess) {
        val changed = access != currentState.access
        setState { copy(access = access) }
        if (!access.canLoad) {
            loadJob?.cancel()
            setState { copy(phase = ToolPhase.Ready, files = selectableFiles()) }
            return
        }
        if (changed || currentState.files.items.isEmpty()) load()
    }

    /**
     * `withTimeoutOrNull`, never `withTimeout`: the expiry lands in the same `when` as the success
     * and the user is TOLD. The competitor truncates at 10 s and reports success.
     */
    private fun load() {
        loadJob?.cancel()
        setState { copy(phase = ToolPhase.Scanning, scanTruncated = false, error = null) }
        loadJob = launchSafely(onError = ::onFailure) {
            when (val result = withTimeoutOrNull(QueryTimeout) { loadVideos() }) {
                null -> setState { copy(phase = ToolPhase.Ready, scanTruncated = true) }
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success ->
                    setState { withLoaded(result.value, savedState.restoreSelection()) }
            }
        }
    }

    private fun saveSelection(reducer: VideoManagerState.() -> VideoManagerState) {
        setState(reducer)
        savedState.storeSelection(currentState.files.selectedIds)
    }
    private fun openRow(id: String) {
        val file = currentState.files.items.firstOrNull { it.id == id } ?: return
        val uri = (file.origin as? FileOrigin.MediaStoreEntry)?.contentUri ?: file.path
        sendEffect(VideoManagerEffect.OpenFile(uri, mimeTypeOf(file)))
    }

    private fun onDeletePressed() {
        if (currentState.canDelete) {
            val trashEligible = permissions.isGranted(AppPermission.AllFiles)
            setState { copy(confirm = deleteConfirmSpec(selectedCount, trashEligible), trashEligible = trashEligible) }
        }
    }

    private fun runDelete(ids: Set<String>) {
        val requireTrash = currentState.trashEligible
        val targets = currentState.files.items.filter { it.id in ids }
        if (targets.isEmpty()) {
            setState { copy(confirm = null) }
            return
        }
        pendingDeleteIds = ids
        analytics.track(AnalyticsEvent.CleanRequested(targets.sumOf { it.sizeBytes }, targets.size))
        setState { copy(confirm = null, phase = ToolPhase.Deleting, failedCount = 0) }
        launchSafely(onError = ::onFailure) {
            when (val result = deleteFiles(targets, FeatureId.VideoManager, requireTrash = requireTrash)) {
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
                    VideoManagerEffect.NavigateToCleanResult(
                        cleanupSummaryFor(FeatureId.VideoManager, outcome),
                    ),
                )
            }

            // The normal path in the default branch, NOT an error (§0.2).
            is DeleteOutcome.PendingConsent -> {
                pendingDeleteIds = outcome.ids.toSet()
                setState { copy(phase = ToolPhase.Ready) }
                sendEffect(VideoManagerEffect.RequestDeleteConsent(outcome.request))
            }

            // A first-class outcome, not silence: the competitor's video delete returns without
            // invoking its callback, stranding its busy flag forever.
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

    /** Lowers `phase` AND clears `confirm` — both are re-entry guards. */
    private fun onFailure(error: AppError) {
        setState { copy(phase = ToolPhase.Ready, confirm = null, error = error) }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }

    private companion object { val QueryTimeout = 30.seconds }
}
