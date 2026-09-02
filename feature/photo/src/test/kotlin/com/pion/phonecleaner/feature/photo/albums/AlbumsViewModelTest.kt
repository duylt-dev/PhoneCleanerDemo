package com.pion.phonecleaner.feature.photo.albums

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.usecase.LoadAlbumsUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.feature.photo.testing.FakePhotoRepository
import com.pion.phonecleaner.feature.photo.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.photo.testing.RecordingAnalytics
import com.pion.phonecleaner.feature.photo.testing.RecordingFeatureUsage
import com.pion.phonecleaner.feature.photo.testing.album
import com.pion.phonecleaner.feature.photo.testing.runVmTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** `docs/screens/13-photo-and-media.md` §6.2 and the five deltas of §6.5. */
class AlbumsViewModelTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val repository = FakePhotoRepository()
    private val analytics = RecordingAnalytics()

    private fun viewModel() = AlbumsViewModel(
        loadAlbums = LoadAlbumsUseCase(repository),
        markFeatureUsed = MarkFeatureUsedUseCase(RecordingFeatureUsage()),
        analytics = analytics,
    )

    /** The completion sweep plays, then the list is live. */
    private fun ready() = viewModel().also { it.onIntent(AlbumsIntent.CompletionAnimationFinished) }

    @Test
    fun `the index is ordered by count descending and two same-named folders stay two albums`() =
        main.runVmTest {
            repository.albums.value = AppResult.Success(
                persistentListOf(
                    album("/storage/emulated/0/Pictures/Camera", count = 2, totalBytes = 200L),
                    album("/storage/emulated/0/DCIM/Camera", count = 9, totalBytes = 900L),
                    album("/storage/emulated/0/Download", count = 5, totalBytes = 500L),
                ),
            )

            val state = ready().state.value

            assertEquals(listOf(9, 5, 2), state.albums.map { it.count })
            // Keyed by the full parent path: the competitor merges these two on the last segment.
            assertEquals(
                listOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Download", "/storage/emulated/0/Pictures/Camera"),
                state.albums.map { it.folderName },
            )
            assertEquals(listOf("Camera", "Download", "Camera"), state.albums.map { it.label })
            assertEquals(1_600L, state.totalBytes)
            assertEquals(ToolPhase.Ready, state.phase)
            assertFalse(state.showEmptyState)
        }

    @Test
    fun `an empty library reaches the empty state, not a spinner that never ends`() =
        main.runVmTest {
            repository.albums.value = AppResult.Success(persistentListOf())

            assertTrue(ready().state.value.showEmptyState)
        }

    @Test
    fun `a later emission from the feed does not send a ready screen back through the sweep`() =
        main.runVmTest {
            repository.albums.value = AppResult.Success(persistentListOf(album("/DCIM", count = 1)))
            val vm = ready()

            repository.albums.value = AppResult.Success(persistentListOf(album("/DCIM", count = 4)))

            // A photo added elsewhere updates the row without a re-entry, and without replaying the
            // completion animation (§6.2).
            assertEquals(ToolPhase.Ready, vm.state.value.phase)
            assertEquals(4, vm.state.value.albums.single().count)
        }

    @Test
    fun `opening an album is an Effect, never a flag on state`() = main.runVmTest {
        repository.albums.value = AppResult.Success(persistentListOf(album("/DCIM/Camera", count = 1)))
        val vm = ready()

        vm.effects.test {
            vm.onIntent(AlbumsIntent.AlbumOpened("/DCIM/Camera"))
            assertEquals(AlbumsEffect.OpenAlbum("/DCIM/Camera"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a denied read is an error the screen renders, not a crash out of an unhandled scope`() =
        main.runVmTest {
            repository.albums.value = AppResult.Failure(AppError.PermissionDenied())

            val state = viewModel().state.value

            assertEquals(AppError.PermissionDenied(), state.error)
            assertEquals(ToolPhase.Ready, state.phase)
        }

    @Test
    fun `back is an Effect, and it is never blocked by a scan in flight`() = main.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(AlbumsIntent.BackPressed)
            assertEquals(AlbumsEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
