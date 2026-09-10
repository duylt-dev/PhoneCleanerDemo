package com.pion.phonecleaner.feature.files.video

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.LoadVideosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.MimeTypeUseCase
import com.pion.phonecleaner.feature.files.component.MediaAccess
import com.pion.phonecleaner.feature.files.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.files.testing.FakeCleanupLedger
import com.pion.phonecleaner.feature.files.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.files.testing.FakeFileDeleter
import com.pion.phonecleaner.feature.files.testing.FakeMediaStoreRepository
import com.pion.phonecleaner.feature.files.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.files.testing.FakeTrashRepository
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class VideoManagerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val mediaStore = FakeMediaStoreRepository()
    private val deleter = FakeFileDeleter()

    private fun viewModel() = VideoManagerViewModel(
        savedState = SavedStateHandle(),
        loadVideos = LoadVideosUseCase(mediaStore),
        deleteFiles = DeleteFilesUseCase(deleter, FakeTrashRepository(), FakeCleanupLedger()),
        markFeatureUsed = MarkFeatureUsedUseCase(FakeFeatureUsageRepository()),
        mimeTypeOf = MimeTypeUseCase(),
        analytics = FakeAnalyticsRepository(),
        permissions = FakePermissionRepository(),
        log = AppLogger.NoOp,
    )

    /**
     * Android 14's user-selected access is a first-class state: the list loads AND the screen says
     * more can be added. The competitor gates the whole Activity from outside and cannot express it.
     */
    @Test
    fun `partial access still loads and shows the add-more banner`() = mainDispatcher.runVmTest {
        mediaStore.videos = AppResult.Success(persistentListOf(clip("v1")))
        val vm = viewModel()

        vm.onIntent(VideoManagerIntent.PermissionResolved(MediaAccess.Partial))
        settle()

        assertEquals(1, vm.state.value.files.items.size)
        assertTrue(vm.state.value.showPartialAccessBanner)
    }

    /** The MIME type is the `MediaStore` column the row carries, not a guess from the extension. */
    @Test
    fun `opening a row carries its content uri and its declared mime type`() =
        mainDispatcher.runVmTest {
            mediaStore.videos = AppResult.Success(
                persistentListOf(clip("v1").copy(mimeType = "video/x-matroska")),
            )
            val vm = viewModel()
            vm.onIntent(VideoManagerIntent.PermissionResolved(MediaAccess.Granted))
            settle()

            vm.effects.test {
                vm.onIntent(VideoManagerIntent.RowOpened("v1"))
                settle()

                val effect = awaitItem() as VideoManagerEffect.OpenFile
                assertEquals("content://video/v1", effect.uri)
                assertEquals("video/x-matroska", effect.mimeType)
            }
        }

    /** A query failure is surfaced, and the screen offers a retry rather than an empty list. */
    @Test
    fun `a failed query lands on the error state`() = mainDispatcher.runVmTest {
        mediaStore.videos = AppResult.Failure(
            com.pion.phonecleaner.core.common.error.AppError.PermissionDenied(),
        )
        val vm = viewModel()

        vm.onIntent(VideoManagerIntent.PermissionResolved(MediaAccess.Granted))
        settle()

        assertTrue(vm.state.value.error != null)
    }

    private companion object {
        fun clip(id: String) = ScannedFile(
            id = id,
            path = "/movies/$id.mp4",
            name = "$id.mp4",
            sizeBytes = 5_000L,
            kind = FileKind.Video,
            origin = FileOrigin.MediaStoreEntry("content://video/$id"),
        )
    }
}
