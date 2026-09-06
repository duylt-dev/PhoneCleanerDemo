package com.pion.phonecleaner.feature.photo.similar

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.PhotoSession
import com.pion.phonecleaner.domain.model.photo.SimilarScanProgress
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.SimilarPhotoSessionStore
import com.pion.phonecleaner.domain.usecase.DeletePhotosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ScanSimilarPhotosUseCase
import com.pion.phonecleaner.feature.photo.allIds
import com.pion.phonecleaner.feature.photo.toggle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job

/**
 * `docs/screens/13-photo-and-media.md` §1.2.
 *
 * `SimilarPhotoSessionStore` is the single source of truth for the groups and the selection:
 * `preview` writes to it and this grid observes it, which is the whole fix for the competitor's
 * synchronisation — mutating `Likesat.isSelected` on instances both screens happen to share (§2.5).
 * So there is **no `SavedStateHandle` here**: a second copy of the selection would be a second
 * source of truth, and the store is in-memory, so after process death the scan is re-run and the
 * pre-selection re-applied anyway. That rule — keep each group's opener — lives in
 * `SimilarPhotoSessionStore.put`, not written onto the model inside the scan pipeline.
 */
class SimilarPhotosViewModel(
    private val scanSimilar: ScanSimilarPhotosUseCase,
    private val deletePhotos: DeletePhotosUseCase,
    private val session: SimilarPhotoSessionStore,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<SimilarPhotosState, SimilarPhotosIntent, SimilarPhotosEffect>(
    SimilarPhotosState(),
    log,
) {

    /** One scan job, cancel-and-replace. Its hashing children are structural (MVI §3). */
    private var scanJob: Job? = null

    init {
        session.session.collectSafely { onSession(it) }
    }

    override fun onIntent(intent: SimilarPhotosIntent) {
        when (intent) {
            SimilarPhotosIntent.ScreenStarted -> onScreenStarted()
            is SimilarPhotosIntent.PhotoToggled ->
                session.select(currentState.selectedIds.toggle(intent.id))
            is SimilarPhotosIntent.GroupCleanupPressed -> onGroupCleanup(intent.groupKey)
            SimilarPhotosIntent.SelectAllToggled -> onSelectAllToggled()
            SimilarPhotosIntent.DeletePressed -> setState { copy(isDeleteConfirmVisible = true) }
            SimilarPhotosIntent.DeleteDismissed -> setState { copy(isDeleteConfirmVisible = false) }
            SimilarPhotosIntent.DeleteConfirmed -> onDeleteConfirmed()
            SimilarPhotosIntent.CompletionAnimationFinished -> setState { copy(phase = ToolPhase.Ready) }
            is SimilarPhotosIntent.PhotoOpened -> onPhotoOpened(intent.id)
            is SimilarPhotosIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            SimilarPhotosIntent.BackPressed -> onBackPressed()
        }
    }

    private fun onSession(current: PhotoSession?) = setState {
        copy(
            groups = current?.groups ?: persistentListOf(),
            selectedIds = current?.selectedIds ?: persistentSetOf(),
            skipped = current?.skipped ?: skipped,
        )
    }

    private fun onScreenStarted() {
        if (currentState.phase != ToolPhase.Idle) return
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.SimilarPhotos))
        launchSafely { markFeatureUsed(FeatureId.SimilarPhotos) }
        scan()
    }

    private fun scan() {
        setState { copy(phase = ToolPhase.Scanning, error = null, hashed = 0, toHash = 0) }
        scanJob?.cancel()
        scanJob = scanSimilar().collectSafely(onError = ::onFailure) { progress ->
            when (progress) {
                // The real count the 4 000 ms Lottie was covering for (§0.5).
                is SimilarScanProgress.Hashing ->
                    setState { copy(hashed = progress.done, toHash = progress.total) }

                is SimilarScanProgress.Done -> {
                    // The store applies the pre-selection and feeds it back through `onSession`.
                    session.put(progress.groups, progress.skipped)
                    setState { copy(skipped = progress.skipped, phase = ToolPhase.Completing) }
                }

                is SimilarScanProgress.Failed ->
                    setState { copy(phase = ToolPhase.Ready, error = progress.error) }
            }
        }
    }

    private fun onSelectAllToggled() {
        val everything = allIds(currentState.groups)
        session.select(if (currentState.selectedIds.size == everything.size) emptySet() else everything)
    }

    /** "Keep one": every member but the opener joins the selection. It deletes nothing on its own. */
    private fun onGroupCleanup(groupKey: String) {
        val group = currentState.groups.firstOrNull { it.key == groupKey } ?: return
        val kept = group.keptId
        val others = group.photos.map { it.id }.filterNot { it == kept }
        session.select(currentState.selectedIds + others)
    }

    private fun onPhotoOpened(id: PhotoId) {
        val group = currentState.groups.firstOrNull { g -> g.photos.any { it.id == id } } ?: return
        sendEffect(
            SimilarPhotosEffect.OpenPreview(
                groupKey = group.key,
                startIndex = group.photos.indexOfFirst { it.id == id }.coerceAtLeast(0),
            ),
        )
    }

    private fun onDeleteConfirmed() {
        val ids = currentState.selectedIds.toList()
        setState { copy(isDeleteConfirmVisible = false) }
        if (ids.isEmpty()) return
        setState { copy(phase = ToolPhase.Deleting, consentDeclined = false, failedCount = 0) }
        launchSafely(onError = ::onFailure) {
            when (val result = deletePhotos(ids)) {
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success -> onOutcome(result.value)
            }
        }
    }

    private fun onOutcome(outcome: DeleteOutcome) = when (outcome) {
        // The ordinary API 30+ path: the system, not this app, asks the user.
        is DeleteOutcome.PendingConsent -> {
            setState { copy(pendingConsentUris = outcome.ids.toImmutableSet()) }
            sendEffect(SimilarPhotosEffect.RequestDeleteConsent(outcome.request))
        }

        is DeleteOutcome.Deleted ->
            prune(outcome.ids.toSet(), outcome.freedBytes, outcome.failedPaths.size)

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
        // The system has already removed the rows; the freed bytes are what this screen still holds.
        prune(pending, currentState.bytesForUris(pending), failedCount = 0)
    }

    /**
     * The rows leave the store **before** the navigation Effect, so the empty state is immediate and
     * groups that fall to one member go with them — the competitor's `L0()` never re-runs its reveal.
     */
    private fun prune(removedUris: Set<String>, freedBytes: Long, failedCount: Int) {
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
            SimilarPhotosEffect.NavigateToCleanResult(
                similarCleanupSummary(freedBytes, removedIds.size),
            ),
        )
    }

    private fun onBackPressed() {
        scanJob?.cancel()
        sendEffect(SimilarPhotosEffect.NavigateBack)
    }

    private fun onFailure(error: AppError) = setState {
        copy(phase = ToolPhase.Ready, pendingConsentUris = persistentSetOf(), error = error)
    }
}
