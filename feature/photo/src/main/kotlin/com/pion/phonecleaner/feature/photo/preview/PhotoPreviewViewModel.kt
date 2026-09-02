package com.pion.phonecleaner.feature.photo.preview

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.photo.SimilarPhotoSession
import com.pion.phonecleaner.domain.repository.SimilarPhotoSessionStore
import com.pion.phonecleaner.feature.photo.toggle
import kotlinx.collections.immutable.persistentListOf

/**
 * `docs/screens/13-photo-and-media.md` §2.2 — the smallest ViewModel in the module.
 *
 * No jobs, no dispatcher, no repository call. `SelectionToggled` writes through to the store, which
 * the similar grid observes; the competitor's synchronisation back to the grid is mutating
 * `Likesat.isSelected` on the shared instances, which works only because both screens hold the same
 * objects (§2.5).
 */
class PhotoPreviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val session: SimilarPhotoSessionStore,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<PhotoPreviewState, PhotoPreviewIntent, PhotoPreviewEffect>(PhotoPreviewState(), log) {

    private val groupKey: String = savedStateHandle[GROUP_KEY_ARG] ?: ""
    private val startIndex: Int = savedStateHandle[START_INDEX_ARG] ?: 0

    /** §2.2 requires the session-lost hop to be raised once, not once per re-emission. */
    private var hasReportedSessionLost = false
    private var hasAppliedStartIndex = false

    init {
        session.session.collectSafely { current -> onSession(current) }
    }

    override fun onIntent(intent: PhotoPreviewIntent) {
        when (intent) {
            PhotoPreviewIntent.ScreenStarted -> Unit // the store feed in `init` is the whole load
            is PhotoPreviewIntent.PageChanged -> setState { copy(index = intent.index) }
            PhotoPreviewIntent.SelectionToggled -> onSelectionToggled()
            PhotoPreviewIntent.ClosePressed -> sendEffect(PhotoPreviewEffect.NavigateBack)
        }
    }

    private fun onSession(current: SimilarPhotoSession?) {
        val group = current?.groups?.firstOrNull { it.key == groupKey }
        if (group == null) {
            setState { copy(photos = persistentListOf(), sessionLost = true) }
            if (!hasReportedSessionLost) {
                hasReportedSessionLost = true
                sendEffect(PhotoPreviewEffect.NavigateToSimilar)
            }
            return
        }
        setState {
            val photos = group.photos
            copy(
                photos = photos,
                selectedIds = current.selectedIds,
                // The route argument positions the pager once. A later emission — the grid removed a
                // deleted row — must not throw the reader back to where they started.
                index = if (hasAppliedStartIndex) index.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
                else startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
                sessionLost = false,
            )
        }
        hasAppliedStartIndex = true
    }

    private fun onSelectionToggled() {
        val id = currentState.current?.id ?: return
        val next = currentState.selectedIds.toggle(id)
        // The store is the single source of truth; the state comes back through the feed above.
        session.select(next)
    }

    companion object {
        /**
         * `SavedStateHandle` keys. They must equal the property names of the `@Serializable` route
         * `PhotoPreview(groupKey: String, startIndex: Int)` that `:app` declares — reported in
         * `routesNeeded`, because `:app/navigation/Routes.kt` is not this cluster's file.
         */
        const val GROUP_KEY_ARG: String = "groupKey"
        const val START_INDEX_ARG: String = "startIndex"
    }
}
