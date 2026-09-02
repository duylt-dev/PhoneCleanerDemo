package com.pion.phonecleaner.feature.photo.preview

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.domain.model.photo.PhotoId
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

    private fun viewModel(groupKey: String = "g1", startIndex: Int = 1) = PhotoPreviewViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                PhotoPreviewViewModel.GROUP_KEY_ARG to groupKey,
                PhotoPreviewViewModel.START_INDEX_ARG to startIndex,
            ),
        ),
        session = session,
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
                assertEquals(PhotoPreviewEffect.NavigateToSimilar, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a group key the store does not hold is session-lost, raised once`() = main.runVmTest {
        scanned()
        val vm = viewModel(groupKey = "not-a-group")

        assertTrue(vm.state.value.sessionLost)
        vm.effects.test {
            assertEquals(PhotoPreviewEffect.NavigateToSimilar, awaitItem())
            // A second store emission must not raise a second hop.
            session.select(setOf(PhotoId(2)))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
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
