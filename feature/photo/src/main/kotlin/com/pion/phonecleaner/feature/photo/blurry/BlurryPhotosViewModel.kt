package com.pion.phonecleaner.feature.photo.blurry

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.photo.BlurScanProgress
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.PhotoSession
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.BlurryPhotoSessionStore
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.DeletePhotosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ScanBlurryPhotosUseCase
import com.pion.phonecleaner.feature.photo.toggle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job

/**
 * The blurry-photo grid.
 *
 * `BlurryPhotoSessionStore` is the single source of truth for the groups and the selection: the
 * pager writes to it and this grid observes it, which is the same arrangement `similar` uses and for
 * the same reason — the competitor synchronises its pager back to its grid by mutating
 * `Likesat.isSelected` on instances both screens happen to share (§2.5).
 *
 * So there is **no `SavedStateHandle` here**: a second copy of the selection would be a second
 * source of truth, and the store is in-memory, so after process death the scan is re-run and the
 * pre-selection re-applied anyway. The pre-selection rule itself lives in
 * `InMemoryBlurryPhotoSessionStore.put`, never written onto the model inside the scan pipeline.
 */
class BlurryPhotosViewModel(
    private val scanBlurry: ScanBlurryPhotosUseCase,
    private val deletePhotos: DeletePhotosUseCase,
    private val session: BlurryPhotoSessionStore,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    private val permissions: PermissionRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<BlurryPhotosState, BlurryPhotosIntent, BlurryPhotosEffect>(
    BlurryPhotosState(),
    log,
) {

    /** One scan job, cancel-and-replace. Its scoring children are structural (MVI §3). */
    private var scanJob: Job? = null

    init {
        session.session.collectSafely { onSession(it) }
    }

    override fun onIntent(intent: BlurryPhotosIntent) {
        when (intent) {
            BlurryPhotosIntent.ScreenStarted -> onScreenStarted()
            is BlurryPhotosIntent.PhotoToggled ->
                session.select(currentState.selectedIds.toggle(intent.id))
            is BlurryPhotosIntent.TierToggled ->
                currentState.selectionAfterTierToggle(intent.groupKey)?.let(session::select)
            BlurryPhotosIntent.SelectAllToggled -> session.select(currentState.selectionAfterSelectAll())
            BlurryPhotosIntent.DeletePressed -> setState {
                copy(
                    isDeleteConfirmVisible = true,
                    trashEligible = permissions.isGranted(AppPermission.AllFiles),
                )
            }
            BlurryPhotosIntent.DeleteDismissed -> setState { copy(isDeleteConfirmVisible = false) }
            BlurryPhotosIntent.DeleteConfirmed -> onDeleteConfirmed()
            BlurryPhotosIntent.CompletionAnimationFinished -> setState { copy(phase = ToolPhase.Ready) }
            is BlurryPhotosIntent.PhotoOpened -> onPhotoOpened(intent.id)
            is BlurryPhotosIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            BlurryPhotosIntent.BackPressed -> onBackPressed()
        }
    }

    private fun onSession(current: PhotoSession?) = setState {
        copy(
            groups = current?.groups ?: persistentListOf(),
            selectedIds = current?.selectedIds ?: persistentSetOf(),
            skipped = current?.skipped ?: skipped,
        )
    }

    /**
     * First entry scans; a **retry after a failed scan** scans again; anything else is ignored.
     *
     * The last clause stops a returning `LaunchedEffect` restarting a finished scan. The middle one
     * exists because `BlurScanProgress.Failed` leaves the screen at [ToolPhase.Ready] and
     * `ErrorCard`'s retry raises [BlurryPhotosIntent.ScreenStarted]: guarding on `phase != Idle`
     * alone swallows it, and the retry button is then drawn, tappable and inert (`LLM.md` §11 row 11).
     *
     * The open/used pair is tracked only on the **first** entry — a retry is the same visit.
     */
    private fun onScreenStarted() {
        val state = currentState
        when {
            state.phase == ToolPhase.Idle -> {
                analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.BlurryPhotos))
                launchSafely { markFeatureUsed(FeatureId.BlurryPhotos) }
                scan()
            }

            state.phase == ToolPhase.Ready && state.error != null -> scan()
            else -> Unit
        }
    }

    private fun scan() {
        setState { copy(phase = ToolPhase.Scanning, error = null, scored = 0, toScore = 0) }
        scanJob?.cancel()
        scanJob = scanBlurry().collectSafely(onError = ::onFailure) { progress ->
            when (progress) {
                is BlurScanProgress.Scoring ->
                    setState { copy(scored = progress.done, toScore = progress.total) }

                is BlurScanProgress.Done -> {
                    // The store applies the pre-selection and feeds it back through `onSession`.
                    session.put(progress.groups, progress.skipped)
                    setState { copy(skipped = progress.skipped, phase = ToolPhase.Completing) }
                }

                is BlurScanProgress.Failed -> setState {
                    copy(phase = ToolPhase.Ready, error = progress.error)
                }
            }
        }
    }

    private fun onPhotoOpened(id: PhotoId) {
        val (groupKey, startIndex) = currentState.previewTarget(id) ?: return
        sendEffect(BlurryPhotosEffect.OpenPreview(groupKey, startIndex))
    }

    private fun onDeleteConfirmed() {
        val requireTrash = currentState.trashEligible
        val ids = currentState.selectedIds.toList()
        setState { copy(isDeleteConfirmVisible = false) }
        if (ids.isEmpty()) return
        setState { copy(phase = ToolPhase.Deleting, consentDeclined = false, failedCount = 0) }
        launchSafely(onError = ::onFailure) {
            when (val result = deletePhotos(ids, FeatureId.BlurryPhotos, requireTrash = requireTrash)) {
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success -> onOutcome(result.value)
            }
        }
    }

    private fun onOutcome(outcome: DeleteOutcome) = when (outcome) {
        // The ordinary API 30+ path: the system, not this app, asks the user. Unreachable on the
        // trash path — no delete request is ever built there.
        is DeleteOutcome.PendingConsent -> {
            setState { copy(pendingConsentUris = outcome.ids.toImmutableSet()) }
            sendEffect(BlurryPhotosEffect.RequestDeleteConsent(outcome.request))
        }

        is DeleteOutcome.Deleted ->
            prune(outcome.ids.toSet(), outcome.freedBytes, outcome.failedPaths.size, outcome.recoverable)
        DeleteOutcome.NothingResolved -> setState { copy(phase = ToolPhase.Ready) }
    }

    private fun onConsentResult(granted: Boolean) {
        val pending = currentState.pendingConsentUris
        if (!granted) {
            setState {
                copy(phase = ToolPhase.Ready, pendingConsentUris = persistentSetOf(), consentDeclined = true)
            }
            return
        }
        // The system has already removed the rows permanently; this path is only reached on the
        // no-trash branch. The freed bytes are what this screen still holds.
        prune(pending, currentState.bytesForUris(pending), failedCount = 0, recoverable = false)
    }

    /**
     * The rows leave the store **before** the navigation Effect, so the empty state is immediate. A
     * tier emptied by the delete goes with them; a tier that falls to **one** member stays — where
     * `InMemoryBlurryPhotoSessionStore.remove` parts company with the similar store's.
     */
    private fun prune(removedUris: Set<String>, freedBytes: Long, failedCount: Int, recoverable: Boolean) {
        val removedIds = currentState.idsForUris(removedUris)
        session.remove(removedIds)
        setState {
            copy(pendingConsentUris = persistentSetOf(), phase = ToolPhase.Ready, failedCount = failedCount)
        }
        if (removedIds.isEmpty()) return
        analytics.track(
            AnalyticsEvent.CleanRequested(selectedBytes = freedBytes, selectedCount = removedIds.size),
        )
        sendEffect(
            BlurryPhotosEffect.NavigateToCleanResult(
                blurryCleanupSummary(freedBytes, removedIds.size, recoverable),
            ),
        )
    }

    private fun onBackPressed() {
        scanJob?.cancel()
        sendEffect(BlurryPhotosEffect.NavigateBack)
    }

    private fun onFailure(error: AppError) = setState {
        copy(phase = ToolPhase.Ready, pendingConsentUris = persistentSetOf(), error = error)
    }
}
