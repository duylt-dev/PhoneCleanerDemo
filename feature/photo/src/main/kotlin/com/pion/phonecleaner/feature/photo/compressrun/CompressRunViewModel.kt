package com.pion.phonecleaner.feature.photo.compressrun

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.PhotoRepository
import com.pion.phonecleaner.domain.usecase.CompressPhotosUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job

/**
 * `docs/screens/13-photo-and-media.md` §4.2.
 *
 * The route carries **ids**, not rows: the `Photo` list is re-read from [PhotoRepository], so
 * process death re-materialises the screen instead of emptying a static field. An id that no longer
 * resolves is dropped, and an empty result is [CompressRunState.sessionLost] — a state the screen
 * renders, not a crash.
 *
 * One `runJob`, whose per-photo work is a structural child of it. `onCleared` cancels the
 * `viewModelScope` and it with it; a partially compressed set is already persisted photo by photo.
 */
class CompressRunViewModel(
    savedStateHandle: SavedStateHandle,
    private val compressPhotos: CompressPhotosUseCase,
    private val photos: PhotoRepository,
    private val analytics: AnalyticsRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<CompressRunState, CompressRunIntent, CompressRunEffect>(CompressRunState(), log) {

    private val requestedIds: List<PhotoId> =
        savedStateHandle.readPhotoIds(PHOTO_IDS_ARG).map(::PhotoId)

    private var runJob: Job? = null

    override fun onIntent(intent: CompressRunIntent) {
        when (intent) {
            CompressRunIntent.ScreenStarted -> onScreenStarted()
            is CompressRunIntent.PageChanged -> setState { copy(page = intent.index) }
            CompressRunIntent.CompressAllPressed -> onCompressAllPressed()
            CompressRunIntent.CompressConfirmed -> onCompressConfirmed()
            CompressRunIntent.CompressDismissed -> setState { copy(isCompressConfirmVisible = false) }
            CompressRunIntent.CompletionAnimationFinished -> onCompletionAnimationFinished()
            CompressRunIntent.BackPressed -> onBackPressed()
            CompressRunIntent.CancelRunConfirmed -> onCancelRunConfirmed()
            CompressRunIntent.CancelRunDismissed -> setState { copy(isStopConfirmVisible = false) }
        }
    }

    private fun onScreenStarted() {
        if (currentState.photos.isNotEmpty() || currentState.run != null) return
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.PhotoCompressor))
        load()
    }

    private fun load() {
        launchSafely(onError = ::onFailure) {
            when (val result = photos.photos()) {
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success -> onLoaded(result.value)
            }
        }
    }

    /**
     * The route's order is the user's order, so the rows are resolved **through the id list** rather
     * than by filtering the library and keeping its order.
     */
    private fun onLoaded(library: List<Photo>) {
        val byId = library.associateBy { it.id }
        val rows = requestedIds.mapNotNull { byId[it] }
        setState {
            copy(
                photos = rows.toImmutableList(),
                page = page.coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
                sessionLost = rows.isEmpty(),
                error = null,
            )
        }
    }

    private fun onCompressAllPressed() {
        if (!currentState.canCompress) return
        setState { copy(isCompressConfirmVisible = true) }
    }

    private fun onCompressConfirmed() {
        val ids = currentState.photos.map { it.id }
        setState { copy(isCompressConfirmVisible = false) }
        if (ids.isEmpty()) return
        runJob?.cancel()
        runJob = launchSafely(onError = ::onFailure) {
            var progress = CompressProgress(
                done = 0,
                total = ids.size,
                savedBytes = 0L,
                failedCount = 0,
                currentId = null,
            )
            setState { copy(run = progress, error = null) }
            compressPhotos(ids).collect { step ->
                progress = progress.fold(step)
                setState { copy(run = progress) }
            }
            // The flow has ended, so the engine has said everything it will say: an id that never
            // produced a step is one that no longer resolves. Closing the total onto what was
            // actually reported is what makes `isFinished` true — leaving `total` at the requested
            // count would park the bar at 2/3 for ever, which is the competitor's never-reset
            // `compressing` latch by another route (§4.5).
            setState { copy(run = progress.copy(total = progress.done + progress.failedCount)) }
        }
    }

    /**
     * The navigation hop is raised **here**, not the moment the last step lands.
     *
     * That is what [CompressRunIntent.CompletionAnimationFinished] is for: the composable owns the
     * completion animation and reports back, so the result screen never waits on an SDK callback the
     * way the competitor's list waits on an interstitial closing (§0.2).
     */
    private fun onCompletionAnimationFinished() {
        val progress = currentState.run ?: return
        if (!progress.isFinished) return
        sendEffect(
            CompressRunEffect.NavigateToCleanResult(
                CleanupSummary(
                    feature = FeatureId.PhotoCompressor,
                    freedBytes = progress.savedBytes,
                    itemCount = progress.done,
                    outcome = if (progress.savedBytes > 0L) CleanupOutcome.Cleaned
                    else CleanupOutcome.NothingFound,
                ),
            ),
        )
    }

    /** Back while running asks. The competitor blocks back with a toast and never clears its flag. */
    private fun onBackPressed() {
        if (currentState.isRunning) setState { copy(isStopConfirmVisible = true) }
        else sendEffect(CompressRunEffect.NavigateBack)
    }

    private fun onCancelRunConfirmed() {
        runJob?.cancel()
        setState { copy(isStopConfirmVisible = false, run = null) }
        sendEffect(CompressRunEffect.NavigateBack)
    }

    private fun onFailure(error: AppError) {
        setState { copy(run = null, error = error) }
    }

    companion object {
        /**
         * The `SavedStateHandle` key. It must equal the property name of the `@Serializable` route
         * `CompressRun(photoIds: List<Long>)` that `:app` declares — reported in `routesNeeded`,
         * because `:app/navigation/Routes.kt` is not this cluster's file (`LLM.md` §4).
         */
        const val PHOTO_IDS_ARG: String = "photoIds"
    }
}

/**
 * A `List<Long>` route argument does not have one settled representation in a `SavedStateHandle`:
 * a type-safe `@Serializable` route stores the list, an argument-typed one stores a `LongArray`.
 * Both are read here rather than guessing which `:app` will use — a wrong guess is an empty screen
 * with no error, which is exactly the failure this route exists to avoid.
 */
internal fun SavedStateHandle.readPhotoIds(key: String): List<Long> = when (val raw = get<Any?>(key)) {
    is LongArray -> raw.toList()
    is List<*> -> raw.mapNotNull { (it as? Number)?.toLong() }
    else -> emptyList()
}
