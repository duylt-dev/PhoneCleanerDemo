package com.pion.phonecleaner.data.photo

import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.PhotoSession
import com.pion.phonecleaner.domain.repository.BlurryPhotoSessionStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The blurry-photo scan hand-off between the grid and the pager.
 *
 * A sibling of [InMemorySimilarPhotoSessionStore] and **not a subclass of it**: the two differ in
 * exactly the two rules below, and both are inversions rather than refinements. Written out, each
 * store is one screen's rules in one readable place; folded into a base class with two overridable
 * hooks, the reader has to hold two files in their head to learn what either screen does. The shared
 * remainder is the `MutableStateFlow` plumbing, which is not the part anyone gets wrong.
 */
internal class InMemoryBlurryPhotoSessionStore : BlurryPhotoSessionStore {

    private val _session = MutableStateFlow<PhotoSession?>(null)
    override val session: StateFlow<PhotoSession?> = _session.asStateFlow()

    /**
     * **Every row is pre-selected** — the owner's decision of 2026-09-06, taken with the cost on the
     * table: a photo blurred on purpose scores as blurry and therefore arrives ticked for deletion.
     *
     * This is the inversion of the similar store's rule, which holds back each group's opener
     * because a similar group is a set of rivals and one of them is meant to survive. A blur tier is
     * not a set of rivals — no member of it is the "best copy" of any other — so there is no
     * candidate to hold back, and holding back an arbitrary one would be a rule with no reason.
     *
     * Pre-selection is applied **here, once**, not written onto the model inside the scan pipeline:
     * the competitor sets `isSelected` on the shared instances during its scan and then deselects
     * one afterwards, which is why its "keep the best" is literally "keep the first".
     */
    override fun put(groups: ImmutableList<PhotoGroup>, skipped: Int) {
        _session.value = PhotoSession(
            groups = groups,
            selectedIds = groups.asSequence()
                .flatMap { group -> group.photos.asSequence() }
                .map { it.id }
                .toImmutableSet(),
            skipped = skipped,
        )
    }

    override fun select(ids: Set<PhotoId>) {
        _session.update { current -> current?.copy(selectedIds = ids.toImmutableSet()) }
    }

    /**
     * Drops rows that are gone and the selection entries that pointed at them, then drops any tier
     * that has been **emptied** — not any tier that has fallen below two.
     *
     * That threshold is the second inversion. `InMemorySimilarPhotoSessionStore.remove` discards a
     * group of one because a group of one is not a similarity; one remaining blurry photo is still a
     * blurry photo, and discarding it would make a row the user never acted on disappear from a
     * screen that is still open.
     */
    override fun remove(ids: Set<PhotoId>) {
        if (ids.isEmpty()) return
        _session.update { current ->
            current ?: return@update null
            val groups = current.groups
                .map { group -> group.copy(photos = group.photos.filterNot { it.id in ids }.toImmutableList()) }
                .filter { it.photos.isNotEmpty() }
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
