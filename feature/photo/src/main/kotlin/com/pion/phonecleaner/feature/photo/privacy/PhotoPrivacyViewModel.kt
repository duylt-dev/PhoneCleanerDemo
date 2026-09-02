package com.pion.phonecleaner.feature.photo.privacy

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.photo.GeotagScanProgress
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.usecase.LoadGeotaggedPhotosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.StripPhotoLocationUseCase
import com.pion.phonecleaner.feature.photo.allIds
import com.pion.phonecleaner.feature.photo.readSelection
import com.pion.phonecleaner.feature.photo.toggle
import com.pion.phonecleaner.feature.photo.toggleGroup
import com.pion.phonecleaner.feature.photo.writeSelection
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job

/**
 * `docs/screens/13-photo-and-media.md` §5.2.
 *
 * Two jobs, each cancel-and-replace, each a `launchSafely` whose children are structural — never a
 * field cancelled by hand (MVI §3). `onCleared` cancels the `viewModelScope` and both with it.
 */
class PhotoPrivacyViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val loadGeotagged: LoadGeotaggedPhotosUseCase,
    private val stripLocation: StripPhotoLocationUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<PhotoPrivacyState, PhotoPrivacyIntent, PhotoPrivacyEffect>(
    PhotoPrivacyState(selectedIds = savedStateHandle.readSelection(SELECTION_KEY)),
    log,
) {

    private var scanJob: Job? = null
    private var stripJob: Job? = null

    override fun onIntent(intent: PhotoPrivacyIntent) {
        when (intent) {
            PhotoPrivacyIntent.ScreenStarted -> onScreenStarted()
            is PhotoPrivacyIntent.PhotoToggled -> select { it.toggle(intent.id) }
            is PhotoPrivacyIntent.MonthToggled -> onMonthToggled(intent.key)
            PhotoPrivacyIntent.SelectAllToggled -> onSelectAllToggled()
            PhotoPrivacyIntent.ClearPressed -> setState { copy(isClearConfirmVisible = true) }
            PhotoPrivacyIntent.ClearDismissed -> setState { copy(isClearConfirmVisible = false) }
            PhotoPrivacyIntent.ClearConfirmed -> onClearConfirmed()
            PhotoPrivacyIntent.CompletionAnimationFinished -> setState { copy(phase = ToolPhase.Ready) }
            PhotoPrivacyIntent.BackPressed -> onBackPressed()
            PhotoPrivacyIntent.StopConfirmed -> onStopConfirmed()
            PhotoPrivacyIntent.StopDismissed -> setState { copy(isStopConfirmVisible = false) }
        }
    }

    private fun onScreenStarted() {
        if (currentState.phase != ToolPhase.Idle) return
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.PhotoPrivacy))
        launchSafely { markFeatureUsed(FeatureId.PhotoPrivacy) }
        scan()
    }

    private fun scan() {
        setState { copy(phase = ToolPhase.Scanning, error = null, scanned = 0, toScan = 0) }
        scanJob?.cancel()
        scanJob = loadGeotagged().collectSafely(onError = ::onFailure) { progress ->
            when (progress) {
                is GeotagScanProgress.Scanning ->
                    setState { copy(scanned = progress.scanned, toScan = progress.total) }

                is GeotagScanProgress.Done -> setState {
                    copy(
                        groups = progress.groups,
                        // Nothing is pre-selected here: removing a location tag is not reversible,
                        // and §5 nowhere states a default selection for this tool.
                        selectedIds = selectedIds.retaining(
                            progress.groups.flatMapTo(mutableSetOf()) { g -> g.photos.map { it.id } },
                        ),
                        phase = ToolPhase.Completing,
                    )
                }

                is GeotagScanProgress.Failed ->
                    setState { copy(phase = ToolPhase.Ready, error = progress.error) }
            }
        }
    }

    private fun onSelectAllToggled() {
        val everything = allIds(currentState.groups)
        select { if (it.size == everything.size) persistentSetOf() else everything }
    }

    private fun onMonthToggled(key: String) {
        val group = currentState.groups.firstOrNull { it.key == key } ?: return
        select { it.toggleGroup(group) }
    }

    private fun select(transform: (ImmutableSet<PhotoId>) -> ImmutableSet<PhotoId>) {
        setState { copy(selectedIds = transform(selectedIds)) }
        savedStateHandle.writeSelection(SELECTION_KEY, currentState.selectedIds)
    }

    private fun onClearConfirmed() {
        val ids = currentState.selectedIds.toList()
        setState { copy(isClearConfirmVisible = false) }
        if (ids.isEmpty()) return
        stripJob?.cancel()
        stripJob = launchSafely(onError = ::onFailure) {
            var progress = StripProgress(done = 0, total = ids.size, failedCount = 0)
            setState { copy(strip = progress) }
            // Only the rows the engine actually cleared. A row it failed on stays in the list and
            // stays selected, so it can be tried again — the competitor's engine reports success
            // unconditionally and the distinction does not exist there (§5.5).
            val cleared = mutableSetOf<PhotoId>()
            stripLocation(ids).collect { step ->
                if (!step.failed) cleared += step.id
                progress = progress.fold(step)
                setState { copy(strip = progress) }
            }
            // Also the path taken when the engine emits nothing at all — every id had gone away.
            finishStrip(progress, cleared)
        }
    }

    /**
     * The cleared rows leave [PhotoPrivacyState.groups] **before** the navigation Effect, so the
     * list is already correct. The competitor never refreshes its adapter; it leaves the screen.
     */
    private fun finishStrip(progress: StripProgress, cleared: Set<PhotoId>) {
        setState {
            copy(
                groups = groups
                    .map { group -> group.copy(photos = group.photos.filterNot { it.id in cleared }.toImmutableList()) }
                    .filter { it.photos.isNotEmpty() }
                    .toImmutableList(),
                selectedIds = (selectedIds - cleared).toImmutableSet(),
                strip = null,
                phase = ToolPhase.Ready,
            )
        }
        savedStateHandle.writeSelection(SELECTION_KEY, currentState.selectedIds)
        sendEffect(
            PhotoPrivacyEffect.NavigateToCleanResult(
                CleanupSummary(
                    feature = FeatureId.PhotoPrivacy,
                    // Nothing is freed: a tag is removed in place. `DataCleared` is exactly why the
                    // shared result screen renders no size block (§5.2).
                    freedBytes = 0L,
                    itemCount = progress.done,
                    outcome = CleanupOutcome.DataCleared,
                ),
            ),
        )
    }

    private fun onBackPressed() {
        if (currentState.isBusy) setState { copy(isStopConfirmVisible = true) }
        else sendEffect(PhotoPrivacyEffect.NavigateBack)
    }

    private fun onStopConfirmed() {
        scanJob?.cancel()
        stripJob?.cancel()
        setState { copy(isStopConfirmVisible = false, strip = null, phase = ToolPhase.Ready) }
        sendEffect(PhotoPrivacyEffect.NavigateBack)
    }

    private fun onFailure(error: AppError) {
        setState { copy(phase = ToolPhase.Ready, strip = null, error = error) }
    }

    companion object {
        /** Selection survives process death; the geotag scan is re-run (`LLM.md` §8, §0.5). */
        const val SELECTION_KEY: String = "photoPrivacySelection"
    }
}

/**
 * A restored selection can name a photo the new scan no longer sees. Keeping it would put a count on
 * the bar that no visible cell explains, and hand a dead id to the strip engine.
 */
private fun ImmutableSet<PhotoId>.retaining(known: Set<PhotoId>): ImmutableSet<PhotoId> =
    filterTo(mutableSetOf()) { it in known }.toImmutableSet()
