package com.pion.phonecleaner.feature.photo.preview

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.PhotoSessionSource
import com.pion.phonecleaner.feature.photo.testing.FakeBlurryPhotoSessionStore
import com.pion.phonecleaner.feature.photo.testing.FakeSimilarPhotoSessionStore
import com.pion.phonecleaner.feature.photo.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.photo.testing.group
import com.pion.phonecleaner.feature.photo.testing.photo
import com.pion.phonecleaner.feature.photo.testing.runVmTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** `docs/screens/13-photo-and-media.md` §2.2 and the four deltas of §2.5. */
class PhotoPreviewViewModelTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val opener = photo(1)
    private val second = photo(2)
    private val third = photo(3)
    private val session = FakeSimilarPhotoSessionStore()
    private val blurry = FakeBlurryPhotoSessionStore()

    private fun viewModel(
        groupKey: String = "g1",
        startIndex: Int = 1,
        source: PhotoSessionSource = PhotoSessionSource.Similar,
    ) = PhotoPreviewViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                PhotoPreviewViewModel.GROUP_KEY_ARG to groupKey,
                PhotoPreviewViewModel.START_INDEX_ARG to startIndex,
                PhotoPreviewViewModel.SOURCE_ARG to source,
            ),
        ),
        similar = session,
        blurry = blurry,
    )

    private fun scanned() = session.put(persistentListOf(group("g1", opener, second, third)))

    @Test
    fun `the two route scalars position the pager over the group the store holds`() =
        main.runVmTest {
            scanned()

            val state = viewModel().state.value

            assertEquals(listOf(opener, second, third), state.photos)
            assertEquals(1, state.index)
            assertEquals(second, state.current)
            assertEquals(2, state.counterPosition)
            assertEquals(3, state.counterTotal)
            // The opener is the member "keep one" keeps; page 1 is not it.
            assertFalse(state.isCurrentKept)
            assertTrue(state.isCurrentSelected)
        }

    @Test
    fun `a start index past the end is clamped rather than paging onto nothing`() =
        main.runVmTest {
            scanned()

            assertEquals(2, viewModel(startIndex = 99).state.value.index)
        }

    @Test
    fun `toggling writes through the store, which is what the grid reads back`() =
        main.runVmTest {
            scanned()
            val vm = viewModel()

            vm.onIntent(PhotoPreviewIntent.SelectionToggled)

            assertFalse(vm.state.value.isCurrentSelected)
            // The store is the single source of truth: the competitor mutates `isSelected` on the
            // shared model instances instead, which works only because both screens hold them (§2.5).
            assertEquals(setOf(PhotoId(3)), session.session.value?.selectedIds)

            vm.onIntent(PhotoPreviewIntent.SelectionToggled)
            assertTrue(vm.state.value.isCurrentSelected)
            assertEquals(setOf(PhotoId(2), PhotoId(3)), session.session.value?.selectedIds)
        }

    @Test
    fun `a page turn moves only the index, and a later store emission does not undo it`() =
        main.runVmTest {
            scanned()
            val vm = viewModel()

            vm.onIntent(PhotoPreviewIntent.PageChanged(2))
            assertEquals(third, vm.state.value.current)

            session.select(setOf(PhotoId(3)))

            // The route argument positions the pager once. It must not throw the reader back.
            assertEquals(2, vm.state.value.index)
        }

    @Test
    fun `an empty store is the session-lost branch every session-store route owes`() =
        main.runVmTest {
            val vm = viewModel()

            assertTrue(vm.state.value.sessionLost)
            assertTrue(vm.state.value.photos.isEmpty())
            vm.effects.test {
                assertEquals(PhotoPreviewEffect.NavigateToGrid, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a group key the store does not hold is session-lost, raised once`() = main.runVmTest {
        scanned()
        val vm = viewModel(groupKey = "not-a-group")

        assertTrue(vm.state.value.sessionLost)
        vm.effects.test {
            assertEquals(PhotoPreviewEffect.NavigateToGrid, awaitItem())
            // A second store emission must not raise a second hop.
            session.select(setOf(PhotoId(2)))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the source argument decides which store the pager reads`() = main.runVmTest {
        // Only the BLURRY store holds a scan; the similar one is empty.
        blurry.put(persistentListOf(group("tier", opener, second, third)))

        val vm = viewModel(groupKey = "tier", source = PhotoSessionSource.Blurry)

        assertFalse(vm.state.value.sessionLost)
        assertEquals(listOf(opener, second, third), vm.state.value.photos)
        // Every row of a blur tier is pre-selected, where a similar group holds back its opener.
        assertEquals(setOf(PhotoId(1), PhotoId(2), PhotoId(3)), vm.state.value.selectedIds)
    }

    @Test
    fun `a blur tier badges no member as kept, because it keeps none`() = main.runVmTest {
        blurry.put(persistentListOf(group("tier", opener, second, third)))

        // Page 0 is the opener — the page `similar` badges "Keeping".
        val vm = viewModel(groupKey = "tier", startIndex = 0, source = PhotoSessionSource.Blurry)

        assertFalse(vm.state.value.marksKeptPhoto)
        // Badging it would tell the reader a photo is safe that is in fact ticked for deletion.
        assertFalse(vm.state.value.isCurrentKept)
        assertTrue(vm.state.value.isCurrentSelected)
    }

    @Test
    fun `a similar group still badges its opener as kept`() = main.runVmTest {
        scanned()

        val vm = viewModel(startIndex = 0)

        assertTrue(vm.state.value.marksKeptPhoto)
        assertTrue(vm.state.value.isCurrentKept)
    }

    @Test
    fun `toggling in the blurry pager writes to the blurry store and not the similar one`() =
        main.runVmTest {
            scanned()
            blurry.put(persistentListOf(group("tier", opener, second, third)))
            val vm = viewModel(groupKey = "tier", source = PhotoSessionSource.Blurry)

            vm.onIntent(PhotoPreviewIntent.SelectionToggled)

            assertEquals(setOf(PhotoId(1), PhotoId(3)), blurry.session.value?.selectedIds)
            // The similar session is untouched: the two grids keep separate stores.
            assertEquals(setOf(PhotoId(2), PhotoId(3)), session.session.value?.selectedIds)
        }

    @Test
    fun `close is an Effect` () = main.runVmTest {
        scanned()
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(PhotoPreviewIntent.ClosePressed)
            assertEquals(PhotoPreviewEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
