package com.pion.phonecleaner.feature.files.audio

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.LoadAudioUseCase
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
 * `audio` (`docs/screens/14-file-tools-and-app-manager.md` §4).
 *
 * **The engine's `minDurationMs` parameter is deleted outright.** A screen that finishes in 200 ms
 * finishes in 200 ms; the competitor's `3000L` exists so an ad has time to load, and the same engine
 * passes `0L` for its two other callers.
 */
class AudioManagerViewModel(
    private val savedState: SavedStateHandle,
    private val loadAudio: LoadAudioUseCase,
    private val deleteFiles: DeleteFilesUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val mimeTypeOf: MimeTypeUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<AudioManagerState, AudioManagerIntent, AudioManagerEffect>(
    AudioManagerState(),
    log,
) {

    private var loadJob: Job? = null

    /** What the user asked to delete, so a consent round trip re-issues exactly that. */
    private var pendingDeleteIds: Set<String> = emptySet()

    override fun onIntent(intent: AudioManagerIntent) {
        when (intent) {
            AudioManagerIntent.ScreenStarted ->
                launchSafely { markFeatureUsed(FeatureId.AudioManager) }

            is AudioManagerIntent.PermissionResolved -> onAccess(intent.access)
            AudioManagerIntent.GrantPressed -> sendEffect(AudioManagerEffect.RequestMediaPermission)
            is AudioManagerIntent.SortSelected -> setState { withSort(intent.sort) }
            is AudioManagerIntent.RowToggled ->
                saveSelection { copy(files = files.toggle(intent.id)) }

            AudioManagerIntent.SelectAllToggled -> saveSelection {
                copy(files = if (files.isAllSelected) files.clearSelection() else files.selectAll())
            }
            is AudioManagerIntent.RowOpened -> openRow(intent.id)
            AudioManagerIntent.DeletePressed -> onDeletePressed()
            AudioManagerIntent.DeleteConfirmed -> runDelete(currentState.files.selectedIds)
            AudioManagerIntent.DeleteDismissed -> setState { copy(confirm = null) }
            AudioManagerIntent.CompletionAnimationFinished ->
                setState { copy(phase = ToolPhase.Ready) }

            is AudioManagerIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            AudioManagerIntent.BackPressed -> onBackPressed()
        }
    }

    private fun onAccess(access: MediaAccess) {
        val changed = access != currentState.access
        setState { copy(access = access) }
        if (!access.audioCanLoad()) {
            loadJob?.cancel()
            setState { copy(phase = ToolPhase.Ready, files = selectableFiles()) }
            return
        }
        if (changed || currentState.files.items.isEmpty()) load()
    }

    /**
     * `withTimeoutOrNull`, never `withTimeout`: the expiry lands in the same `when` as the success
     * and the user is TOLD. The competitor's watchdog stops the cursor loop and reports success.
     */
    private fun load() {
        loadJob?.cancel()
        setState { copy(phase = ToolPhase.Scanning, scanTruncated = false, error = null) }
        loadJob = launchSafely(onError = ::onFailure) {
            when (val result = withTimeoutOrNull(QueryTimeout) { loadAudio() }) {
                null -> setState { copy(phase = ToolPhase.Ready, scanTruncated = true) }
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success ->
                    setState { withLoaded(result.value, savedState.restoreSelection()) }
            }
        }
    }

    private fun saveSelection(reducer: AudioManagerState.() -> AudioManagerState) {
        setState(reducer)
        savedState.storeSelection(currentState.files.selectedIds)
    }

    private fun openRow(id: String) {
        val file = currentState.files.items.firstOrNull { it.id == id } ?: return
        val uri = (file.origin as? FileOrigin.MediaStoreEntry)?.contentUri ?: file.path
        sendEffect(AudioManagerEffect.OpenFile(uri, mimeTypeOf(file)))
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
                    AudioManagerEffect.NavigateToCleanResult(
                        cleanupSummaryFor(FeatureId.AudioManager, outcome),
                    ),
                )
            }

            // The normal path in the default branch, NOT an error (§0.2).
            is DeleteOutcome.PendingConsent -> {
                pendingDeleteIds = outcome.ids.toSet()
                setState { copy(phase = ToolPhase.Ready) }
                sendEffect(AudioManagerEffect.RequestDeleteConsent(outcome.request))
            }

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

    private fun onBackPressed() {
        loadJob?.cancel()
        sendEffect(AudioManagerEffect.NavigateBack)
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
