package com.pion.phonecleaner.data.photo

import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.PhotoSession
import com.pion.phonecleaner.domain.repository.SimilarPhotoSessionStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The scan hand-off between the similar grid and the pager, as one `single` with an owner and an
 * explicit [clear] (`docs/screens/13-photo-and-media.md` §2.2, §2.4).
 *
 * It replaces the first of the cluster's three static hand-offs — two fields on the receiving
 * Activity, with the `Intent` carrying no extras at all — and the mutation of `Likesat.isSelected`
 * that serves as its synchronisation back to the grid (§2.5). Here the session is an immutable value
 * and both screens observe it; neither owns it.
 */
internal class InMemorySimilarPhotoSessionStore : SimilarPhotoSessionStore {

    private val _session = MutableStateFlow<PhotoSession?>(null)
    override val session: StateFlow<PhotoSession?> = _session.asStateFlow()

    /**
     * Pre-selection is applied **here, once**: every member of every group except the first, which is
     * the newest (§1.2). The competitor writes `isSelected` onto the model inside the scan pipeline,
     * then deselects the opener afterwards — so "keep the best" is literally "keep the first".
     */
    override fun put(groups: ImmutableList<PhotoGroup>, skipped: Int) {
        _session.value = PhotoSession(
            groups = groups,
            selectedIds = groups.asSequence()
                .flatMap { group -> group.photos.asSequence().drop(1) }
                .map { it.id }
                .toImmutableSet(),
            skipped = skipped,
        )
    }

    override fun select(ids: Set<PhotoId>) {
        _session.update { current -> current?.copy(selectedIds = ids.toImmutableSet()) }
    }

    /**
     * Drops rows that are gone and the selection entries that pointed at them, then drops any group
     * that has fallen below two members — a group of one is not a similarity.
     *
     * The competitor's `L0()` rebinds without re-running its reveal, so when the last group
     * disappears the empty state never appears (§1.2).
     */
    override fun remove(ids: Set<PhotoId>) {
        if (ids.isEmpty()) return
        _session.update { current ->
            current ?: return@update null
            val groups = current.groups
                .map { group -> group.copy(photos = group.photos.filterNot { it.id in ids }.toImmutableList()) }
                .filter { it.photos.size > 1 }
                .toImmutableList()
            val alive = groups.asSequence().flatMap { it.photos.asSequence() }.map { it.id }.toSet()
            current.copy(
                groups = groups,
                selectedIds = current.selectedIds.filter { it in alive }.toImmutableSet(),
            )
        }
    }

    override fun clear() {
        _session.value = null
    }
}
