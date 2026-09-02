package com.pion.phonecleaner.feature.files.audio

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.LoadAudioUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.MimeTypeUseCase
import com.pion.phonecleaner.feature.files.component.MediaAccess
import com.pion.phonecleaner.feature.files.component.MediaSort
import com.pion.phonecleaner.feature.files.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.files.testing.FakeCleanupLedger
import com.pion.phonecleaner.feature.files.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.files.testing.FakeFileDeleter
import com.pion.phonecleaner.feature.files.testing.FakeMediaStoreRepository
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class AudioManagerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val mediaStore = FakeMediaStoreRepository()
    private val deleter = FakeFileDeleter()

    private fun viewModel() = AudioManagerViewModel(
        savedState = SavedStateHandle(),
        loadAudio = LoadAudioUseCase(mediaStore),
        deleteFiles = DeleteFilesUseCase(deleter, FakeCleanupLedger()),
        markFeatureUsed = MarkFeatureUsedUseCase(FakeFeatureUsageRepository()),
        mimeTypeOf = MimeTypeUseCase(),
        analytics = FakeAnalyticsRepository(),
        log = AppLogger.NoOp,
    )

    /** The load starts from the permission intent, never from `init` (MVI §3). */
    @Test
    fun `nothing is queried until the permission is resolved`() = mainDispatcher.runVmTest {
        mediaStore.audio = AppResult.Success(persistentListOf(track("a", 100L, 10L)))
        val vm = viewModel()

        vm.onIntent(AudioManagerIntent.ScreenStarted)
        settle()
        assertTrue(vm.state.value.files.items.isEmpty())

        vm.onIntent(AudioManagerIntent.PermissionResolved(MediaAccess.Granted))
        settle()
        assertEquals(1, vm.state.value.files.items.size)
    }

    /** A denial renders a permission state; it does not re-prompt against a permanent no. */
    @Test
    fun `a denied grant clears the list and asks nothing on its own`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(AudioManagerIntent.PermissionResolved(MediaAccess.Denied))
            settle()

            expectNoEvents()
        }
        assertTrue(vm.state.value.showPermissionState)
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    /** The sort is a pure reducer over rows already held; the competitor has no sort at all. */
    @Test
    fun `sorting by size reorders without a second query`() = mainDispatcher.runVmTest {
        mediaStore.audio = AppResult.Success(
            persistentListOf(track("small", 10L, 300L), track("large", 900L, 100L)),
        )
        val vm = viewModel()
        vm.onIntent(AudioManagerIntent.PermissionResolved(MediaAccess.Granted))
        settle()

        vm.onIntent(AudioManagerIntent.SortSelected(MediaSort.LargestFirst))

        assertEquals(
            listOf("large", "small"),
            vm.state.value.files.items.map(ScannedFile::id),
        )
    }

    private companion object {
        fun track(id: String, sizeBytes: Long, modifiedAt: Long) = ScannedFile(
            id = id,
            path = "/music/$id.mp3",
            name = "$id.mp3",
            sizeBytes = sizeBytes,
            kind = FileKind.Audio,
            origin = FileOrigin.MediaStoreEntry("content://audio/$id"),
            lastModifiedAtMillis = modifiedAt,
        )
    }
}
