package com.pion.phonecleaner.data.trash

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.database.entity.TrashEntryTypeRow
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.FileKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RoomTrashRepositoryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `real move commits one row and preserves contents`() = runTest {
        val f = TrashFixture(temp.root.canonicalFile, StandardTestDispatcher(testScheduler))
        val source = f.source()
        val result = f.repository.trashFiles(listOf(source), FeatureId.Trash) as AppResult.Success
        assertTrue(result.value.movedIds.isEmpty() || result.value.movedIds == listOf(source.id))
        if (result.value.movedIds.isEmpty()) {
            assertEquals(listOf(source.path), result.value.failedPaths)
            assertEquals("payload", File(source.path).readText())
        } else {
            assertFalse(File(source.path).exists())
            assertEquals("payload", File(f.dao.rows.values.single().trashedPath).readText())
            assertEquals("TRASHED", f.dao.rows.values.single().state)
            assertEquals(1, f.scheduled)
        }
    }

    @Test fun `refused move leaves original intact and no row`() = runTest {
        val f = TrashFixture(temp.root.canonicalFile, StandardTestDispatcher(testScheduler))
        f.refuseMove = true
        val source = f.source()
        val result = f.repository.trashFiles(listOf(source), FeatureId.Trash) as AppResult.Success
        assertEquals(listOf(source.path), result.value.failedPaths)
        assertEquals("payload", File(source.path).readText())
        assertTrue(f.dao.rows.isEmpty())
    }

    @Test fun `media kind creates zip row even when mime type is missing`() = runTest {
        val f = TrashFixture(temp.root.canonicalFile, StandardTestDispatcher(testScheduler))
        val source = f.source("photo.jpg").copy(kind = FileKind.Image, mimeType = null)

        val result = f.repository.trashFiles(listOf(source), FeatureId.ImageManager) as AppResult.Success

        assertEquals(listOf(source.id), result.value.movedIds)
        assertFalse(result.value.zipFailed)
        assertEquals(
            1,
            f.dao.rows.values.count {
                it.state == "TRASHED" && it.entryType == TrashEntryTypeRow.ORIGINAL.name
            },
        )
        assertEquals(
            1,
            f.dao.rows.values.count {
                it.state == "TRASHED" && it.entryType == TrashEntryTypeRow.ZIP.name
            },
        )
    }

    @Test fun `reconcile keeps interrupted restore when original is occupied`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val row = f.row(state = "RESTORING")
        File(row.originalPath).writeText("newer")
        f.repository.reconcile()
        assertEquals("TRASHED", f.dao.rows[row.id]?.state)
        assertEquals("payload", File(row.trashedPath).readText())
        assertEquals("newer", File(row.originalPath).readText())
    }

    @Test fun `reconcile settles pending and purging but drops genuinely missing rows`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        f.row("pending", "PENDING")
        f.row("purging", "PURGING")
        val missing = f.row("missing")
        File(missing.trashedPath).delete()
        f.repository.reconcile()
        assertEquals(setOf("pending", "purging"), f.dao.rows.keys)
        assertTrue(f.dao.rows.values.all { it.state == "TRASHED" })
    }

    @Test fun `revoked access never deletes a ledger row during reconciliation`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val row = f.row()
        File(row.trashedPath).delete()
        f.granted = false
        f.repository.reconcile()
        assertTrue(f.dao.rows.containsKey(row.id))
    }

    @Test fun `reconcile settles rows after a committed move`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val first = f.source("one")
        val second = f.source("two")
        f.repository.trashFiles(listOf(first, second), FeatureId.Trash)
        f.repository.reconcile()
        assertTrue(f.dao.rows.values.all { it.state == "TRASHED" })
    }

    @Test fun `purge only deletes expired entries and accounts zero byte files`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        f.row("due", content = "")
        f.now += 1.days
        f.row("future")
        f.now += 1.days
        val result = f.repository.purgeExpired() as AppResult.Success
        assertEquals(listOf("due"), result.value.purgedIds)
        assertEquals(0L, result.value.freedBytes)
        assertEquals(setOf("future"), f.dao.rows.keys)
    }

    @Test fun `empty bin covers more than the visible 500 entries`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        repeat(501) { f.row("item-$it", content = "x") }
        val result = f.repository.deleteAllForever() as AppResult.Success
        assertEquals(501, result.value.purgedIds.size)
        assertEquals(501L, result.value.freedBytes)
        assertTrue(f.dao.rows.isEmpty())
    }

    @Test fun `partial directory purge credits removed bytes and keeps the remainder`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val row = f.row()
        val dir = File(row.trashedPath).apply { delete(); mkdirs() }
        File(dir, "removed").writeText("abc")
        File(dir, "kept").writeText("12345")
        f.dao.rows[row.id] = row.copy(sizeBytes = 8, isDirectory = true, fileCount = 2)
        f.remove = { File(it, "removed").delete(); false }
        val partial = f.repository.deleteForever(listOf(row.id)) as AppResult.Success
        assertEquals(3L, partial.value.freedBytes)
        assertEquals(listOf(row.id), partial.value.failedIds)
        assertEquals(5L, f.dao.rows[row.id]?.sizeBytes)
        assertEquals("TRASHED", f.dao.rows[row.id]?.state)
        f.remove = { it.deleteRecursively() }
        val remaining = f.repository.deleteForever(listOf(row.id)) as AppResult.Success
        assertEquals(5L, remaining.value.freedBytes)
        assertTrue(f.dao.rows.isEmpty())
    }
}
