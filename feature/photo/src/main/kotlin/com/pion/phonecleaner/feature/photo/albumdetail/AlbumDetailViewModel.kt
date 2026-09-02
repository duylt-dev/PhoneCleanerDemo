package com.pion.phonecleaner.feature.photo.albumdetail

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.usecase.DeletePhotosUseCase
import com.pion.phonecleaner.domain.usecase.LoadAlbumPhotosUseCase
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job

/**
 * `docs/screens/13-photo-and-media.md` §7.2.
 *
 * The album is a **route argument plus a query** — the competitor mutates a single process-wide
 * `ce.b` created at class load, which cannot survive process death and cannot be typed (§7.5).
 *
 * [DeletePhotosUseCase] is the same use case `similar` uses, over the same `FileDeleter`. There is
 * no second delete path in this module (§0.3).
 */
class AlbumDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val loadAlbumPhotos: LoadAlbumPhotosUseCase,
    private val deletePhotos: DeletePhotosUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<AlbumDetailState, AlbumDetailIntent, AlbumDetailEffect>(
    AlbumDetailState(folderName = savedStateHandle[FOLDER_NAME_ARG] ?: ""),
    log,
) {

    /** One load job, cancel-and-replace. Never a field cancelled by hand elsewhere (MVI §3). */
    private var loadJob: Job? = null

    override fun onIntent(intent: AlbumDetailIntent) {
        when (intent) {
            AlbumDetailIntent.ScreenStarted -> onScreenStarted()
            is AlbumDetailIntent.PhotoToggled -> toggle(intent.id)
            AlbumDetailIntent.SelectAllToggled -> toggleAll()
            AlbumDetailIntent.DeletePressed -> setState { copy(isDeleteConfirmVisible = true) }
            AlbumDetailIntent.DeleteDismissed -> setState { copy(isDeleteConfirmVisible = false) }
            AlbumDetailIntent.DeleteConfirmed -> onDeleteConfirmed()
            is AlbumDetailIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            AlbumDetailIntent.BackPressed -> sendEffect(AlbumDetailEffect.NavigateBack)
        }
    }

    private fun onScreenStarted() {
        if (currentState.phase == ToolPhase.Deleting) return
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.ImageManager))
        load()
    }

    private fun load() {
        setState { copy(phase = ToolPhase.Scanning, error = null) }
        loadJob?.cancel()
        loadJob = launchSafely(onError = ::onFailure) {
            when (val result = loadAlbumPhotos(currentState.folderName)) {
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success -> setState {
                    copy(
                        photos = result.value,
                        // A row deleted elsewhere must not stay selected.
                        selectedIds = selectedIds
                            .filterTo(mutableSetOf()) { id -> result.value.any { it.id == id } }
                            .toImmutableSet(),
                        phase = ToolPhase.Ready,
                        error = null,
                    )
                }
            }
        }
    }

    private fun toggle(id: PhotoId) = setState {
        copy(selectedIds = (if (id in selectedIds) selectedIds - id else selectedIds + id).toImmutableSet())
    }

    private fun toggleAll() = setState {
        copy(
            selectedIds = if (isAllSelected) persistentSetOf() else photos.map { it.id }.toImmutableSet(),
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
            sendEffect(AlbumDetailEffect.RequestDeleteConsent(outcome.request))
        }

        is DeleteOutcome.Deleted -> {
            val removed = outcome.ids.toSet()
            prune(removed, outcome.freedBytes, outcome.failedPaths.size)
        }

        DeleteOutcome.NothingResolved -> setState { copy(phase = ToolPhase.Ready) }
    }

    /**
     * The consent dialog's answer.
     *
     * On `true` the **system** has already removed the rows, so the freed bytes are summed from the
     * rows this screen still holds rather than re-asking. On `false` the phase comes back down and
     * the screen says so — the competitor leaves the selection intact and shows nothing (§7.5).
     */
    private fun onConsentResult(granted: Boolean) {
        val pending = currentState.pendingConsentUris
        if (!granted) {
            setState {
                copy(phase = ToolPhase.Ready, pendingConsentUris = persistentSetOf(), consentDeclined = true)
            }
            return
        }
        val freed = currentState.photos.filter { it.contentUri in pending }.sumOf { it.sizeBytes }
        prune(pending, freed, failedCount = 0)
    }

    /**
     * Prunes in place **before** the navigation Effect, so the list is already correct if the user
     * comes back. The competitor never refreshes its adapter; it leaves the screen.
     */
    private fun prune(removedUris: Set<String>, freedBytes: Long, failedCount: Int) {
        val remaining = currentState.photos.filterNot { it.contentUri in removedUris }
        val removedIds = currentState.photos.filter { it.contentUri in removedUris }.map { it.id }.toSet()
        setState {
            copy(
                photos = remaining.toImmutableList(),
                selectedIds = (selectedIds - removedIds).toImmutableSet(),
                pendingConsentUris = persistentSetOf(),
                phase = ToolPhase.Ready,
                failedCount = failedCount,
            )
        }
        if (removedUris.isEmpty()) return
        sendEffect(
            AlbumDetailEffect.NavigateToCleanResult(
                CleanupSummary(
                    feature = FeatureId.ImageManager,
                    freedBytes = freedBytes,
                    itemCount = removedUris.size,
                    outcome = if (freedBytes > 0L) CleanupOutcome.Cleaned else CleanupOutcome.NothingFound,
                ),
            ),
        )
    }

    private fun onFailure(error: AppError) {
        setState { copy(phase = ToolPhase.Ready, pendingConsentUris = persistentSetOf(), error = error) }
    }

    companion object {
        /**
         * The `SavedStateHandle` key. It must equal the property name of the `@Serializable` route
         * `AlbumDetail(folderName: String)` that `:app` declares — reported in `routesNeeded`,
         * because `:app/navigation/Routes.kt` is not this cluster's file (`LLM.md` §4).
         */
        const val FOLDER_NAME_ARG: String = "folderName"
    }
}
