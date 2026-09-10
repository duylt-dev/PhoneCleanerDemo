package com.pion.phonecleaner.feature.files.zipfiles

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.ZipFileOutcome
import com.pion.phonecleaner.domain.model.file.ZipFileRequest
import com.pion.phonecleaner.domain.model.file.ZipInputFile
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.FileZipper
import com.pion.phonecleaner.domain.usecase.CreateZipFileUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.feature.files.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class ZipFilesViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val zipper = FakeFileZipper()
    private val featureUsage: FeatureUsageRepository = FakeFeatureUsageRepository()

    private fun viewModel() = ZipFilesViewModel(
        createZipFile = CreateZipFileUseCase(zipper),
        markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
    )

    @Test
    fun `FilesPicked keeps only ten files and reports ignored extras`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        val files = (1..12).map { input(it) }.toImmutableList()

        vm.onIntent(ZipFilesIntent.FilesPicked(files))

        assertEquals(10, vm.state.value.pickedFiles.size)
        assertEquals(2, vm.state.value.ignoredCount)
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    @Test
    fun `CreateZipPressed creates zip and clears the picked files`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(ZipFilesIntent.FilesPicked(listOf(input(1), input(2)).toImmutableList()))

        vm.onIntent(ZipFilesIntent.CreateZipPressed)
        settle()

        assertEquals(1, zipper.requests.size)
        assertNotNull(vm.state.value.outcome)
        assertTrue(vm.state.value.pickedFiles.isEmpty())
        assertEquals(ToolPhase.Completing, vm.state.value.phase)
    }

    @Test
    fun `zip failure returns to Ready with an error so retry can run`() = mainDispatcher.runVmTest {
        zipper.next = AppResult.Failure(AppError.Storage("zip"))
        val vm = viewModel()
        vm.onIntent(ZipFilesIntent.FilesPicked(listOf(input(1)).toImmutableList()))

        vm.onIntent(ZipFilesIntent.CreateZipPressed)
        settle()

        assertEquals(ToolPhase.Ready, vm.state.value.phase)
        assertTrue(vm.state.value.error is AppError.Storage)
        zipper.next = null
        vm.onIntent(ZipFilesIntent.CreateZipPressed)
        settle()
        assertEquals(2, zipper.requests.size)
    }

    @Test
    fun `ScreenStarted marks the zip feature used`() = mainDispatcher.runVmTest {
        val usage = FakeFeatureUsageRepository()
        val vm = ZipFilesViewModel(CreateZipFileUseCase(zipper), MarkFeatureUsedUseCase(usage))

        vm.onIntent(ZipFilesIntent.ScreenStarted)
        settle()

        assertEquals(listOf(FeatureId.ZipFiles), usage.marked)
    }

    private fun input(index: Int) = ZipInputFile(
        uri = "content://file/$index",
        displayName = "file-$index.txt",
        sizeBytes = index.toLong(),
    )

    private class FakeFileZipper : FileZipper {
        val requests = mutableListOf<ZipFileRequest>()
        var next: AppResult<ZipFileOutcome>? = null

        override suspend fun createZip(request: ZipFileRequest): AppResult<ZipFileOutcome> {
            requests += request
            return next ?: AppResult.Success(
                ZipFileOutcome(
                    uri = "content://zip/${requests.size}",
                    fileName = request.fileName,
                    inputCount = request.files.size,
                    inputBytes = request.files.sumOf(ZipInputFile::sizeBytes),
                    outputBytes = 1L,
                ),
            )
        }
    }
}
