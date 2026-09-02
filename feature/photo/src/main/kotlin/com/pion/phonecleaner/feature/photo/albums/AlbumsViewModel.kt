package com.pion.phonecleaner.feature.photo.albums

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.usecase.LoadAlbumsUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * `docs/screens/13-photo-and-media.md` §6.2.
 *
 * `observeAlbums()` is a feed, collected once in `init` through `collectSafely`, so a photo added or
 * deleted elsewhere updates this screen without a re-entry. The competitor queries once in
 * `onCreate` and never again.
 *
 * The port is reached through `LoadAlbumsUseCase`, which §6.2 names and which does exist in
 * `domain/usecase/`. It needs a `factoryOf(::LoadAlbumsUseCase)` line in `domainModule` —
 * `domain/di/DomainModule.kt` is not this cluster's file, so that line is reported to its owner
 * rather than declared a second time here (`LLM.md` §6.4).
 */
class AlbumsViewModel(
    private val loadAlbums: LoadAlbumsUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<AlbumsState, AlbumsIntent, AlbumsEffect>(AlbumsState(), log) {

    init {
        // `init` observes, it does not act (MVI §3).
        loadAlbums().collectSafely(onError = ::onFailure) { result -> onAlbums(result) }
    }

    override fun onIntent(intent: AlbumsIntent) {
        when (intent) {
            AlbumsIntent.ScreenStarted -> onScreenStarted()
            is AlbumsIntent.AlbumOpened -> sendEffect(AlbumsEffect.OpenAlbum(intent.folderName))
            AlbumsIntent.CompletionAnimationFinished -> setState { copy(phase = ToolPhase.Ready) }
            AlbumsIntent.BackPressed -> sendEffect(AlbumsEffect.NavigateBack)
        }
    }

    /**
     * Idempotent: `ON_START` fires it again after every return from the album screen, and the feed
     * is already running. Only the phase is lifted, and only out of `Idle`.
     */
    private fun onScreenStarted() {
        if (currentState.phase == ToolPhase.Idle) setState { copy(phase = ToolPhase.Scanning) }
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.ImageManager))
        launchSafely { markFeatureUsed(FeatureId.ImageManager) }
    }

    private fun onAlbums(result: AppResult<ImmutableList<PhotoAlbum>>) = when (result) {
        is AppResult.Failure -> setState { copy(phase = ToolPhase.Ready, error = result.error) }
        is AppResult.Success -> setState {
            copy(
                // "by count descending, in the reducer" (§6.2). Sorting is deliberately not in the
                // repository: the fold there has no opinion about how a screen orders its rows.
                albums = result.value.sortedByDescending { it.count }.toImmutableList(),
                // The completion sweep plays once. A later emission from the feed must not send a
                // Ready screen back through it.
                phase = if (phase == ToolPhase.Ready) ToolPhase.Ready else ToolPhase.Completing,
                error = null,
            )
        }
    }

    private fun onFailure(error: AppError) {
        setState { copy(phase = ToolPhase.Ready, error = error) }
    }
}
