package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.ZipFileOutcome
import com.pion.phonecleaner.domain.model.file.ZipFileRequest
import com.pion.phonecleaner.domain.model.file.ZipInputFile
import com.pion.phonecleaner.domain.repository.FileZipper
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateZipFileUseCaseTest {

    private val zipper = FakeFileZipper()
    private val useCase = CreateZipFileUseCase(zipper)

    @Test
    fun `empty selection fails before reaching the zipper`() = runTest {
        val result = useCase(ZipFileRequest(emptyList<ZipInputFile>().toImmutableList(), "empty.zip"))

        assertTrue(result is AppResult.Failure)
        assertEquals(0, zipper.calls)
    }

    @Test
    fun `more than ten files fails before reaching the zipper`() = runTest {
        val files = (1..11).map { ZipInputFile("content://$it", "file-$it.txt", 1L) }.toImmutableList()
        val result = useCase(ZipFileRequest(files, "too-many.zip"))

        assertTrue(result is AppResult.Failure)
        assertEquals(0, zipper.calls)
    }

    @Test
    fun `valid request delegates to zipper`() = runTest {
        val files = listOf(ZipInputFile("content://1", "file.txt", 1L)).toImmutableList()
        val result = useCase(ZipFileRequest(files, "ok.zip"))

        assertTrue(result is AppResult.Success)
        assertEquals(1, zipper.calls)
    }

    private class FakeFileZipper : FileZipper {
        var calls = 0

        override suspend fun createZip(request: ZipFileRequest): AppResult<ZipFileOutcome> {
            calls++
            return if (request.fileName == "fail.zip") {
                AppResult.Failure(AppError.Storage(request.fileName))
            } else {
                AppResult.Success(
                    ZipFileOutcome(
                        uri = "content://zip",
                        fileName = request.fileName,
                        inputCount = request.files.size,
                        inputBytes = request.files.sumOf(ZipInputFile::sizeBytes),
                        outputBytes = 1L,
                    ),
                )
            }
        }
    }
}
