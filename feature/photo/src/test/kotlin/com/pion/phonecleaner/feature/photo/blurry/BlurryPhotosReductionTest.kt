package com.pion.phonecleaner.feature.photo.blurry

import com.pion.phonecleaner.domain.model.photo.BlurTier
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.testing.group
import com.pion.phonecleaner.feature.photo.testing.photo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure half of the screen: plain functions over a state value, no fakes and no dispatcher
 * (`LLM.md` §9). These are the cases the ViewModel test would need a whole coroutine harness to
 * reach.
 */
class BlurryPhotosReductionTest {

    private val a = photo(1, sizeBytes = 500L)
    private val b = photo(2, sizeBytes = 400L)
    private val c = photo(3, sizeBytes = 300L)

    private val state = BlurryPhotosState(
        groups = persistentListOf(
            group(BlurTier.VeryBlurry.name, a, b),
            group(BlurTier.SlightlyBlurry.name, c),
        ),
    )

    @Test
    fun `select-all takes everything when the selection is partial`() {
        val partial = state.copy(selectedIds = persistentSetOf(PhotoId(1)))

        assertEquals(setOf(PhotoId(1), PhotoId(2), PhotoId(3)), partial.selectionAfterSelectAll())
    }

    @Test
    fun `select-all clears when everything is already selected`() {
        val all = state.copy(selectedIds = listOf(a, b, c).map { it.id }.toImmutableSet())

        assertEquals(emptySet<PhotoId>(), all.selectionAfterSelectAll())
    }

    @Test
    fun `a tier toggle takes its own tier and leaves the other alone`() {
        val none = state.copy(selectedIds = persistentSetOf())

        assertEquals(
            setOf(PhotoId(1), PhotoId(2)),
            none.selectionAfterTierToggle(BlurTier.VeryBlurry.name),
        )
    }

    @Test
    fun `a fully selected tier is cleared by its own header`() {
        val all = state.copy(selectedIds = listOf(a, b, c).map { it.id }.toImmutableSet())

        assertEquals(setOf(PhotoId(3)), all.selectionAfterTierToggle(BlurTier.VeryBlurry.name))
    }

    /**
     * `null`, not an empty set: an unknown key must leave the selection alone. Returning `emptySet()`
     * would silently clear everything the user had ticked.
     */
    @Test
    fun `a tier key that is not on screen changes nothing`() {
        assertNull(state.selectionAfterTierToggle("not-a-tier"))
    }

    @Test
    fun `a preview target is the group key and the index within that group`() {
        assertEquals(BlurTier.VeryBlurry.name to 1, state.previewTarget(PhotoId(2)))
        assertEquals(BlurTier.SlightlyBlurry.name to 0, state.previewTarget(PhotoId(3)))
    }

    @Test
    fun `a preview target for a photo in no group is null`() {
        assertNull(state.previewTarget(PhotoId(99)))
    }

    @Test
    fun `ids and bytes are resolved from content uris, which is what a delete speaks in`() {
        assertEquals(setOf(PhotoId(1), PhotoId(3)), state.idsForUris(setOf(a.contentUri, c.contentUri)))
        assertEquals(800L, state.bytesForUris(setOf(a.contentUri, c.contentUri)))
    }

    @Test
    fun `a cleanup that freed nothing is NothingFound, not Cleaned`() {
        assertEquals(
            com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome.NothingFound,
            blurryCleanupSummary(freedBytes = 0L, itemCount = 0).outcome,
        )
    }
}
