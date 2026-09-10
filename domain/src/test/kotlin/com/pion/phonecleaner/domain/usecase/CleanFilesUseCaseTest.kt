package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.FileCleanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.testing.FakeFileDeleter
import com.pion.phonecleaner.domain.testing.FakeTrashRepository
import com.pion.phonecleaner.domain.testing.FakeLedger
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanFilesUseCaseTest {

    private val file = ScannedFile(
        id = "/path/file.txt",
        path = "/path/file.txt",
        name = "file.txt",
        kind = FileKind.Other,
        sizeBytes = 1000L,
        origin = FileOrigin.PlainFile,
    )

    private val ledger = FakeLedger()
    private val deleter = FakeFileDeleter()
    private val trash = FakeTrashRepository()

    private val useCase = CleanFilesUseCase(deleter, trash, ledger)

    @Test
    fun `with the bin available the deleter is never called`() = runTest {
        trash.isAvailable = true
        trash.movedBytes = 1000L

        useCase(listOf(file), FeatureId.JunkClean, requireTrash = trash.isAvailable).toList()

        assertTrue("deleter should not be called when bin is available", deleter.deleteCalls.isEmpty())
    }

    @Test
    fun `with the bin available the ledger is never written`() = runTest {
        trash.isAvailable = true
        trash.movedBytes = 1000L

        useCase(listOf(file), FeatureId.JunkClean, requireTrash = trash.isAvailable).toList()

        assertEquals("ledger should not record on trash branch", 0L, ledger.recordedBytes)
    }

    @Test
    fun `with the bin available the outcome is recoverable`() = runTest {
        trash.isAvailable = true
        trash.movedBytes = 1000L

        val emissions = useCase(listOf(file), FeatureId.JunkClean, requireTrash = trash.isAvailable).toList()

        val finished = emissions.filterIsInstance<FileCleanProgress.Finished>().lastOrNull()
        assertTrue("outcome should be recoverable when moved to trash", finished?.recoverable == true)
    }

    @Test
    fun `with the bin unavailable the behaviour is byte-for-byte what it was`() = runTest {
        trash.isAvailable = false

        useCase(listOf(file), FeatureId.JunkClean, requireTrash = trash.isAvailable).toList()

        assertEquals("ledger should record freed bytes on permanent delete", 1000L, ledger.recordedBytes)
        assertEquals("deleter should be called when bin unavailable", 1, deleter.deleteCalls.size)
    }

    @Test
    fun `confirmed trash never falls back to permanent deletion when bin becomes unavailable`() = runTest {
        trash.isAvailable = false
        val result = useCase(listOf(file), FeatureId.JunkClean, requireTrash = true).toList()
        assertTrue(result.any { it is FileCleanProgress.Failed })
        assertTrue(deleter.deleteCalls.isEmpty())
        assertEquals(0L, ledger.recordedBytes)
    }
}
