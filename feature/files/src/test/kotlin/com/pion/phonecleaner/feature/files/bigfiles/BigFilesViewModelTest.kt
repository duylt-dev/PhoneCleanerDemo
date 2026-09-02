package com.pion.phonecleaner.feature.files.bigfiles

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.MimeTypeUseCase
import com.pion.phonecleaner.domain.usecase.ScanBigFilesUseCase
import com.pion.phonecleaner.feature.files.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.files.testing.FakeCleanupLedger
import com.pion.phonecleaner.feature.files.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.files.testing.FakeFileDeleter
import com.pion.phonecleaner.feature.files.testing.FakeMediaStoreRepository
import com.pion.phonecleaner.feature.files.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.files.testing.FakeStorageRootProvider
import com.pion.phonecleaner.feature.files.testing.FakeStorageScanner
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class BigFilesViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val mediaStore = FakeMediaStoreRepository()
    private val scanner = FakeStorageScanner()
    private val roots = FakeStorageRootProvider()
    private val permissions = FakePermissionRepository()
    private val deleter = FakeFileDeleter()
    private val analytics = FakeAnalyticsRepository()

    private fun viewModel() = BigFilesViewModel(
        savedState = SavedStateHandle(),
        scanBigFiles = ScanBigFilesUseCase(scanner, mediaStore, roots, permissions),
        deleteFiles = DeleteFilesUseCase(deleter, FakeCleanupLedger()),
        markFeatureUsed = MarkFeatureUsedUseCase(FakeFeatureUsageRepository()),
        mimeTypeOf = MimeTypeUseCase(),
        analytics = analytics,
        log = AppLogger.NoOp,
    )

    /**
     * Coverage is mandatory in the default storage branch: a scan that could not see everything
     * says so (§0.2, §8.4). Without all-files access it is partial, and the screen renders that.
     */
    @Test
    fun `a scan without all-files access reports partial coverage`() = mainDispatcher.runVmTest {
        mediaStore.videos = AppResult.Success(persistentListOf(big("v1")))
        roots.surfaces = listOf("primary").toImmutableList()
        val vm = viewModel()

        vm.onIntent(BigFilesIntent.ScreenStarted)
        settle()

        assertTrue(vm.state.value.coverage.isPartial)
        assertEquals(1, vm.state.value.coverage.surfaceCount)
        assertEquals(1, vm.state.value.files.items.size)
    }

    /** The threshold applies to whatever corpus the branch could read; small rows never appear. */
    @Test
    fun `rows under the threshold are not listed`() = mainDispatcher.runVmTest {
        permissions.granted.value = persistentSetOf(AppPermission.AllFiles)
        mediaStore.images = AppResult.Success(persistentListOf(big("keep"), small("drop")))
        val vm = viewModel()

        vm.onIntent(BigFilesIntent.ScreenStarted)
        settle()

        assertEquals(listOf("keep"), vm.state.value.files.items.map(ScannedFile::id))
        assertFalse(vm.state.value.coverage.isPartial)
    }

    /** A failure to remove is reported, not swallowed — `File.delete()` fails under scoped storage. */
    @Test
    fun `paths the deleter could not remove reach the state`() = mainDispatcher.runVmTest {
        mediaStore.videos = AppResult.Success(persistentListOf(big("v1"), big("v2")))
        deleter.outcomes = mutableListOf(
            AppResult.Success(
                DeleteOutcome.Deleted(
                    ids = persistentListOf("v1"),
                    freedBytes = BigBytes,
                    failedPaths = persistentListOf("/storage/v2.mp4"),
                ),
            ),
        )
        val vm = viewModel()
        vm.onIntent(BigFilesIntent.ScreenStarted)
        settle()
        vm.onIntent(BigFilesIntent.CompletionAnimationFinished)
        vm.onIntent(BigFilesIntent.SelectAllToggled)

        vm.effects.test {
            vm.onIntent(BigFilesIntent.DeletePressed)
            vm.onIntent(BigFilesIntent.DeleteConfirmed)
            settle()
            assertTrue(awaitItem() is BigFilesEffect.NavigateToCleanResult)
        }

        assertEquals(1, vm.state.value.failedCount)
        assertEquals(listOf("v2"), vm.state.value.files.items.map(ScannedFile::id))
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    /** Back abandons the scan. The competitor blocks Back with two toasts. */
    @Test
    fun `back navigates away instead of blocking`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(BigFilesIntent.BackPressed)
            settle()
            assertEquals(BigFilesEffect.NavigateBack, awaitItem())
        }
    }

    private companion object {
        const val BigBytes = 20L * 1024L * 1024L

        fun big(id: String) = ScannedFile(
            id = id,
            path = "/storage/$id.mp4",
            name = "$id.mp4",
            sizeBytes = BigBytes,
            kind = FileKind.Video,
            origin = FileOrigin.PlainFile,
        )

        fun small(id: String) = big(id).copy(sizeBytes = 1_024L)
    }
}
