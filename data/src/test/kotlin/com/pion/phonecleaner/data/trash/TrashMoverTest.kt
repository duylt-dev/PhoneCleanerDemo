package com.pion.phonecleaner.data.trash

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TrashMoverTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `URI and an already trashed source are refused without mutation`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        assertNull(f.mover.resolveDestination("content://media/1", "id"))
        val row = f.row()
        assertNull(f.mover.resolveDestination(row.trashedPath, "again"))
        assertEquals("payload", File(row.trashedPath).readText())
    }

    @Test fun `refused rename leaves the source intact`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val source = f.source()
        val destination = File(f.volume, ".PhoneCleanerTrash/id")
        f.refuseMove = true
        assertEquals(MoveResult.Refused, f.mover.moveIn(source.path, destination))
        assertTrue(File(source.path).exists())
        assertFalse(destination.exists())
    }

    @Test fun `restore preserves newer original and chooses a suffixed file`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val row = f.row()
        File(row.originalPath).writeText("newer")
        val result = f.mover.moveOut(row.trashedPath, row.originalPath) as RestoreResult.Restored
        assertTrue(result.renamed)
        assertEquals("row (1).txt", File(result.path).name)
        assertEquals("payload", File(result.path).readText())
        assertEquals("newer", File(row.originalPath).readText())
    }

    @Test fun `all 99 occupied suffixes refuse restore and keep payload`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val row = f.row()
        File(row.originalPath).writeText("newer")
        repeat(99) { File(f.volume, "row (${it + 1}).txt").writeText("occupied") }
        assertEquals(RestoreResult.Failed, f.mover.moveOut(row.trashedPath, row.originalPath))
        assertEquals("payload", File(row.trashedPath).readText())
    }

    @Test fun `restore recreates missing parent directories`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val row = f.row()
        val target = File(f.volume, "missing/nested/row.txt")
        val result = f.mover.moveOut(row.trashedPath, target.path)
        assertTrue(result is RestoreResult.Restored)
        assertEquals("payload", target.readText())
    }

    @Test fun `permanent delete refuses paths outside trash roots`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val file = f.source()
        assertEquals(PurgeResult(0L, false), f.mover.deletePermanently(file.path))
        assertEquals("payload", File(file.path).readText())
    }
}
