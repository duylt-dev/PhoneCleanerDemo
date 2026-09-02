package com.pion.phonecleaner.feature.photo.albumdetail

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.feature.photo.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.photo.testing.runVmTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `docs/screens/13-photo-and-media.md` §7.2 and the deltas of §7.5. The three system-consent arms
 * live in [AlbumDetailConsentTest].
 */
class AlbumDetailViewModelTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val fixture = AlbumDetailFixture()

    @Test
    fun `the album is a route argument plus a query, never a process-wide static`() =
        main.runVmTest {
            val state = fixture.loaded().state.value

            assertEquals(fixture.folder, state.folderName)
            // The row in the neighbouring folder is not here: the query is keyed on the full path.
            assertEquals(listOf(fixture.first, fixture.second), state.photos)
            assertEquals(ToolPhase.Ready, state.phase)
            assertFalse(state.showEmptyState)
            assertEquals(
                listOf(AnalyticsEvent.FeatureOpened(FeatureId.ImageManager)),
                fixture.analytics.events,
            )
        }

    @Test
    fun `select all is set arithmetic over the loaded rows, and toggles back off`() =
        main.runVmTest {
            val vm = fixture.loaded()

            vm.onIntent(AlbumDetailIntent.SelectAllToggled)
            assertEquals(setOf(PhotoId(1), PhotoId(2)), vm.state.value.selectedIds)
            assertEquals(800L, vm.state.value.selectedBytes)
            assertTrue(vm.state.value.canDelete)

            vm.onIntent(AlbumDetailIntent.SelectAllToggled)
            assertTrue(vm.state.value.selectedIds.isEmpty())
            assertFalse(vm.state.value.canDelete)
        }

    @Test
    fun `the confirm is state, and dismissing it deletes nothing`() = main.runVmTest {
        val vm = fixture.selectedFirst()

        vm.onIntent(AlbumDetailIntent.DeletePressed)
        assertTrue(vm.state.value.isDeleteConfirmVisible)

        vm.onIntent(AlbumDetailIntent.DeleteDismissed)
        assertFalse(vm.state.value.isDeleteConfirmVisible)
        assertTrue(fixture.repository.deletedIds.isEmpty())
    }

    @Test
    fun `a completed delete prunes in place before it navigates`() = main.runVmTest {
        fixture.repository.nextDelete = AppResult.Success(
            DeleteOutcome.Deleted(
                ids = persistentListOf(fixture.first.contentUri),
                freedBytes = 500L,
                failedPaths = persistentListOf("/some/path.jpg"),
            ),
        )
        val vm = fixture.selectedFirst()

        vm.effects.test {
            vm.onIntent(AlbumDetailIntent.DeleteConfirmed)

            // The list is already correct when the Effect leaves — the competitor never refreshes.
            assertEquals(listOf(fixture.second), vm.state.value.photos)
            assertTrue(vm.state.value.selectedIds.isEmpty())
            // Rows the deleter could not remove are reported, never swallowed.
            assertEquals(1, vm.state.value.failedCount)
            assertEquals(ToolPhase.Ready, vm.state.value.phase)
            assertEquals(
                AlbumDetailEffect.NavigateToCleanResult(
                    CleanupSummary(
                        feature = FeatureId.ImageManager,
                        freedBytes = 500L,
                        itemCount = 1,
                        outcome = CleanupOutcome.Cleaned,
                    ),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing resolved lowers the phase instead of stranding a busy flag`() = main.runVmTest {
        fixture.repository.nextDelete = AppResult.Success(DeleteOutcome.NothingResolved)
        val vm = fixture.selectedFirst()

        vm.onIntent(AlbumDetailIntent.DeleteConfirmed)

        assertEquals(ToolPhase.Ready, vm.state.value.phase)
        assertEquals(listOf(fixture.first, fixture.second), vm.state.value.photos)
    }

    @Test
    fun `a failed delete is an error on state, and the phase comes back down`() = main.runVmTest {
        fixture.repository.nextDelete = AppResult.Failure(AppError.Storage(path = fixture.folder))
        val vm = fixture.selectedFirst()

        vm.onIntent(AlbumDetailIntent.DeleteConfirmed)

        assertEquals(AppError.Storage(path = fixture.folder), vm.state.value.error)
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    @Test
    fun `back is one intent, so the toolbar and the system key cannot disagree`() = main.runVmTest {
        val vm = fixture.loaded()

        vm.effects.test {
            vm.onIntent(AlbumDetailIntent.BackPressed)
            assertEquals(AlbumDetailEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
