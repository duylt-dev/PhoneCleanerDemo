package com.pion.phonecleaner.feature.photo.compressor

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.usecase.EstimateCompressionUseCase
import com.pion.phonecleaner.domain.usecase.LoadCompressiblePhotosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
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
 * `docs/screens/13-photo-and-media.md` §3.2.
 *
 * One `scanJob`, cancel-and-replace, whose children are structural — never a field cancelled by
 * hand (MVI §3). `onCleared` cancels the `viewModelScope` and it with it.
 */
class PhotoCompressorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val loadCompressiblePhotos: LoadCompressiblePhotosUseCase,
    private val estimateSavings: EstimateCompressionUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<PhotoCompressorState, PhotoCompressorIntent, PhotoCompressorEffect>(
    PhotoCompressorState(selectedIds = savedStateHandle.readSelection(SELECTION_KEY)),
    log,
) {

    private var scanJob: Job? = null

    override fun onIntent(intent: PhotoCompressorIntent) {
        when (intent) {
            PhotoCompressorIntent.ScreenStarted -> onScreenStarted()
            PhotoCompressorIntent.StartPressed -> onStartPressed()
            is PhotoCompressorIntent.PhotoToggled -> select { it.toggle(intent.id) }
            is PhotoCompressorIntent.MonthToggled -> onMonthToggled(intent.key)
            PhotoCompressorIntent.SelectAllToggled -> onSelectAllToggled()
            PhotoCompressorIntent.CompletionAnimationFinished ->
                setState { copy(phase = ToolPhase.Ready) }

            PhotoCompressorIntent.ContinuePressed -> onContinuePressed()
            PhotoCompressorIntent.BackPressed -> onBackPressed()
        }
    }

    /**
     * Opening the screen is not scanning it. The competitor fires its "scan started" event inside
     * `z()` before the user has pressed anything (§3.2); the only thing recorded here is that the
     * feature was opened, which is what `FeatureOpened` means.
     */
    private fun onScreenStarted() {
        if (currentState.phase != ToolPhase.Idle) return
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.PhotoCompressor))
        launchSafely { markFeatureUsed(FeatureId.PhotoCompressor) }
    }

    private fun onStartPressed() {
        if (currentState.phase == ToolPhase.Scanning) return
        setState { copy(introVisible = false) }
        scan()
    }

    private fun scan() {
        setState { copy(phase = ToolPhase.Scanning, error = null) }
        scanJob?.cancel()
        scanJob = launchSafely(onError = ::onFailure) {
            when (val result = loadCompressiblePhotos()) {
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success -> onScanned(result.value)
            }
        }
    }

    private suspend fun onScanned(groups: List<PhotoGroup>) {
        val known = groups.flatMapTo(mutableSetOf()) { group -> group.photos.map { it.id } }
        setState {
            copy(
                groups = groups.toImmutableList(),
                // A restored selection can name a photo the new scan no longer offers. Keeping it
                // would put a count on the bar that no visible cell explains.
                selectedIds = selectedIds.filterTo(mutableSetOf()) { it in known }.toImmutableSet(),
                phase = ToolPhase.Completing,
                error = null,
            )
        }
        savedStateHandle.writeSelection(SELECTION_KEY, currentState.selectedIds)
        measure(known)
    }

    /**
     * The figure is measured over the **candidates**, not over the selection: it describes what this
     * library re-encodes to, and re-measuring on every tap would decode bitmaps inside a tap.
     * `EstimateCompressionUseCase` samples at most `PhotoCompressor.DEFAULT_ESTIMATE_SAMPLE` of them.
     *
     * A failed estimate is left as `null` and the panel simply omits the row. It is a nicety, and a
     * nicety must not turn a working list into an error screen.
     */
    private suspend fun measure(candidates: Set<PhotoId>) {
        if (candidates.isEmpty()) return
        when (val estimate = estimateSavings(candidates.toList())) {
            is AppResult.Failure -> log.d { "Compression estimate unavailable: ${estimate.error}" }
            is AppResult.Success -> setState { copy(estimate = estimate.value) }
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

    /** Set arithmetic in the reducer. Nothing walks the library per tap (§3.5). */
    private fun select(transform: (ImmutableSet<PhotoId>) -> ImmutableSet<PhotoId>) {
        setState { copy(selectedIds = transform(selectedIds)) }
        savedStateHandle.writeSelection(SELECTION_KEY, currentState.selectedIds)
    }

    private fun onContinuePressed() {
        if (!currentState.canContinue) return
        sendEffect(
            PhotoCompressorEffect.OpenCompressRun(
                currentState.selectedIds.map { it.value }.toImmutableList(),
            ),
        )
    }

    /** Back while scanning cancels the scan and leaves, rather than blocking with a toast (§3.2). */
    private fun onBackPressed() {
        scanJob?.cancel()
        sendEffect(PhotoCompressorEffect.NavigateBack)
    }

    private fun onFailure(error: AppError) {
        setState { copy(phase = ToolPhase.Ready, error = error) }
    }

    companion object {
        /**
         * The selection survives process death; the candidate list is re-scanned, because it is
         * derived data and the selection is user input (§0.5).
         *
         * [PhotoCompressorState.introVisible] is deliberately **not** saved: after process death
         * there are no groups to show, so the landing panel is the correct thing to be looking at.
         */
        const val SELECTION_KEY: String = "photoCompressorSelection"
    }
}
