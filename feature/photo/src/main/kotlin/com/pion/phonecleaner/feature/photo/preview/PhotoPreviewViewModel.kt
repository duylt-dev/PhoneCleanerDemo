package com.pion.phonecleaner.feature.photo.preview

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.photo.PhotoSession
import com.pion.phonecleaner.domain.model.photo.PhotoSessionSource
import com.pion.phonecleaner.domain.repository.BlurryPhotoSessionStore
import com.pion.phonecleaner.domain.repository.PhotoSessionStore
import com.pion.phonecleaner.domain.repository.SimilarPhotoSessionStore
import com.pion.phonecleaner.feature.photo.toggle
import kotlinx.collections.immutable.persistentListOf

/**
 * `docs/screens/13-photo-and-media.md` §2.2 — the smallest ViewModel in the module.
 *
 * No jobs, no dispatcher, no repository call. `SelectionToggled` writes through to the store, which
 * the grid that opened this pager observes; the competitor's synchronisation back to the grid is
 * mutating `Likesat.isSelected` on the shared instances, which works only because both screens hold
 * the same objects (§2.5).
 *
 * **It serves two grids.** `similar` and `blurry` keep separate `PhotoSessionStore` singles, because
 * a delete on one must not prune the other's groups. Both are injected and the route argument picks
 * one — see [session]. That is the whole cost of not owning a second copy of this screen, and it is
 * paid here rather than in a `when` at every use site: below this line nothing knows which grid
 * opened it.
 */
class PhotoPreviewViewModel(
    savedStateHandle: SavedStateHandle,
    similar: SimilarPhotoSessionStore,
    blurry: BlurryPhotoSessionStore,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<PhotoPreviewState, PhotoPreviewIntent, PhotoPreviewEffect>(PhotoPreviewState(), log) {

    private val groupKey: String = savedStateHandle[GROUP_KEY_ARG] ?: ""
    private val startIndex: Int = savedStateHandle[START_INDEX_ARG] ?: 0

    /**
     * Which grid opened this pager, read the way every other enum route argument in this repository
     * is read — `SplashArgs.launchSource`, `PermissionManagerArguments.permissionTab`,
     * `JunkScanViewModel`'s mode.
     *
     * `get<PhotoSessionSource>(…)` alone would compile to a `CHECKCAST` at this line, so a value of
     * the wrong shape is a `ClassCastException` in the constructor — the pager crashes as it opens.
     * Navigation stores the enum instance today, but the `String` arm is what makes that an
     * implementation detail rather than a load-bearing assumption, and it costs one line.
     *
     * The fallback is [PhotoSessionSource.Similar]. It is safe only because the two stores' group
     * keys cannot collide — `blurry` writes tier names, `similar` writes a numeric id — so a wrong
     * guess finds no group and lands in the `sessionLost` branch, which pops. That invariant is
     * stated here because it is the thing that would break if either store's keys changed.
     */
    private val source: PhotoSessionSource = when (val raw = savedStateHandle.get<Any?>(SOURCE_ARG)) {
        is PhotoSessionSource -> raw
        is String -> PhotoSessionSource.entries.firstOrNull { it.name == raw }
            ?: PhotoSessionSource.Similar
        else -> PhotoSessionSource.Similar
    }

    private val session: PhotoSessionStore = when (source) {
        PhotoSessionSource.Similar -> similar
        PhotoSessionSource.Blurry -> blurry
    }

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

    private fun onSession(current: PhotoSession?) {
        val group = current?.groups?.firstOrNull { it.key == groupKey }
        if (group == null) {
            setState { copy(photos = persistentListOf(), sessionLost = true) }
            if (!hasReportedSessionLost) {
                hasReportedSessionLost = true
                sendEffect(PhotoPreviewEffect.NavigateToGrid)
            }
            return
        }
        setState {
            val photos = group.photos
            copy(
                photos = photos,
                selectedIds = current.selectedIds,
                // A blur tier has no kept member: every row in it is pre-selected.
                marksKeptPhoto = source == PhotoSessionSource.Similar,
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
         * `PhotoPreview(groupKey, startIndex, source)` that `:app` declares — reported in
         * `routesNeeded`, because `:app/navigation/Routes.kt` is not this cluster's file.
         */
        const val GROUP_KEY_ARG: String = "groupKey"
        const val START_INDEX_ARG: String = "startIndex"
        const val SOURCE_ARG: String = "source"
    }
}
