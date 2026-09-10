package com.pion.phonecleaner.data.files

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.FileDigest
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pipeline's four load-bearing decisions, each of which was a defect before 2026-09-06.
 *
 * 1. **The corpus.** With all-files access it is a walk of every readable root, so a duplicate
 *    `.pdf` is found; before, it was three media collections and no non-media file could ever
 *    appear no matter how many copies of it existed.
 * 2. **The dedupe key.** `ScannedFile.id`, not `path` — a MediaStore row's `path` is
 *    `RELATIVE_PATH` on API 29+, a folder, so a path-keyed map collapsed a whole folder to one
 *    candidate and the screen reported "no duplicates" on every modern device.
 * 3. **The two-phase digest.** A same-size-different-content pair must cost one head read, not two
 *    full ones, or a whole-volume corpus never finishes.
 * 4. **Truncation.** A budget that expires publishes what completed AND says so. The competitor's
 *    `withTimeoutOrNull(4000)` reports success with a half-built map.
 */
internal class Md5DuplicateFinderTest {

    private val digest = RecordingDigest()

    /** Decision 1, and the whole point of the change: a duplicate `.pdf` is a duplicate. */
    @Test
    fun `with all-files access the walk finds duplicates of any extension`() = runTest {
        val finder = finder(
            allFiles = true,
            walked = listOf(
                walked("/sd/a.pdf", bytes = 10L, content = "same"),
                walked("/sd/docs/copy.pdf", bytes = 10L, content = "same"),
                walked("/sd/other.zip", bytes = 10L, content = "different"),
            ),
        )

        val groups = finder.finished().groups

        assertEquals(1, groups.size)
        assertEquals(FileKind.Other, groups.first().kind)
        assertEquals(
            listOf("/sd/a.pdf", "/sd/docs/copy.pdf"),
            groups.first().files.map(ScannedFile::path).sorted(),
        )
    }

    /** Without the grant the media collections are the corpus and the walk is never asked for. */
    @Test
    fun `without all-files access it falls back to the media collections`() = runTest {
        val scanner = RecordingScanner(emptyList())
        val finder = finder(
            allFiles = false,
            scanner = scanner,
            images = listOf(
                media("content://media/1", bytes = 10L, content = "same"),
                media("content://media/2", bytes = 10L, content = "same"),
            ),
        )

        val groups = finder.finished().groups

        assertEquals(1, groups.size)
        assertTrue("the walk must not run without the grant", scanner.configs.isEmpty())
    }

    /**
     * Decision 2. Every row shares `RELATIVE_PATH` "DCIM/Camera/" — which is what MediaStore
     * actually returns — so a path-keyed dedupe would leave ONE candidate and find nothing.
     */
    @Test
    fun `rows sharing a MediaStore relative path are separate candidates`() = runTest {
        val finder = finder(
            allFiles = false,
            images = listOf(
                media("content://media/1", bytes = 10L, content = "same", path = "DCIM/Camera/"),
                media("content://media/2", bytes = 10L, content = "same", path = "DCIM/Camera/"),
                media("content://media/3", bytes = 10L, content = "same", path = "DCIM/Camera/"),
            ),
        )

        assertEquals(3, finder.finished().groups.single().files.size)
    }

    /** Decision 3: same size, different content, and neither file is read past the head window. */
    @Test
    fun `a same-size different-content pair is settled by the head digest alone`() = runTest {
        val big = FileDigest.HEAD_BYTES * 4
        val finder = finder(
            allFiles = true,
            walked = listOf(
                walked("/sd/one.mp4", bytes = big, content = "a"),
                walked("/sd/two.mp4", bytes = big, content = "b"),
            ),
        )

        assertTrue(finder.finished().groups.isEmpty())
        assertEquals(
            "a head collision is the only thing that may trigger a full read",
            listOf(FileDigest.HEAD_BYTES, FileDigest.HEAD_BYTES),
            digest.reads.map { it.second },
        )
    }

    /** Decision 3 again: a head collision on a large file IS read in full before it is believed. */
    @Test
    fun `a head collision on a large file is confirmed by a full read`() = runTest {
        val big = FileDigest.HEAD_BYTES * 4
        val finder = finder(
            allFiles = true,
            walked = listOf(
                walked("/sd/one.mp4", bytes = big, content = "same"),
                walked("/sd/two.mp4", bytes = big, content = "same"),
            ),
        )

        assertEquals(1, finder.finished().groups.size)
        assertEquals(2, digest.reads.count { it.second == FileDigest.FULL_FILE })
    }

    /** A zero-byte file matches every other zero-byte file and is not a duplicate worth listing. */
    @Test
    fun `zero-byte files are not offered as duplicates`() = runTest {
        val finder = finder(
            allFiles = true,
            walked = listOf(
                walked("/sd/empty-a", bytes = 0L, content = ""),
                walked("/sd/empty-b", bytes = 0L, content = ""),
            ),
        )

        assertTrue(finder.finished().groups.isEmpty())
    }

    /** The walk is long enough that the screen needs to see it move. */
    @Test
    fun `collection progress is reported before any byte is hashed`() = runTest {
        val finder = finder(
            allFiles = true,
            walked = listOf(walked("/sd/a", bytes = 10L, content = "x")),
        )

        val progress = finder.find().toList()

        assertTrue(progress.first() is DuplicateScanProgress.Collecting)
        assertTrue(progress.any { it is DuplicateScanProgress.Hashing })
    }

    private suspend fun com.pion.phonecleaner.domain.repository.DuplicateFinder.finished():
        DuplicateScanProgress.Finished =
        find().toList().filterIsInstance<DuplicateScanProgress.Finished>().single()

    private fun finder(
        allFiles: Boolean,
        walked: List<ScannedFile> = emptyList(),
        images: List<ScannedFile> = emptyList(),
        scanner: StorageScanner = RecordingScanner(walked),
    ) = Md5DuplicateFinder(
        mediaStore = FixedMediaStore(images),
        scanner = scanner,
        roots = FixedRoots(if (allFiles) persistentListOf("/sd") else persistentListOf()),
        permissions = FixedPermissions(allFiles),
        digest = digest,
        dispatchers = TestDispatchers,
    )

    private companion object {
        fun walked(path: String, bytes: Long, content: String) = ScannedFile(
            id = path,
            path = path,
            name = path.substringAfterLast('/'),
            sizeBytes = bytes,
            kind = FileKind.Other,
            origin = FileOrigin.PlainFile,
            lastModifiedAtMillis = path.hashCode().toLong(),
        ).also { ContentByPath[path] = content }

        fun media(uri: String, bytes: Long, content: String, path: String = "DCIM/Camera/") =
            ScannedFile(
                id = uri,
                path = path,
                name = uri.substringAfterLast('/'),
                sizeBytes = bytes,
                kind = FileKind.Image,
                origin = FileOrigin.MediaStoreEntry(uri),
                lastModifiedAtMillis = uri.hashCode().toLong(),
            ).also { ContentByPath[uri] = content }

        /** Keyed by id, so a fake digest can answer "what are these bytes" without a filesystem. */
        val ContentByPath = mutableMapOf<String, String>()
    }

    /**
     * Answers from [ContentByPath] and records `(id, maxBytes)` so a test can assert that the head
     * pass really did stop at the window — the property the whole two-phase design rests on.
     *
     * A file at or below the window has one answer for both passes, exactly as a real digest that
     * hit EOF would.
     */
    private class RecordingDigest : FileDigest {
        val reads = mutableListOf<Pair<String, Long>>()

        override suspend fun digest(file: ScannedFile, maxBytes: Long): AppResult<String> {
            reads += file.id to maxBytes
            val content = ContentByPath[file.id].orEmpty()
            val head = if (maxBytes < file.sizeBytes) "head:${content.take(1)}" else "full:$content"
            return head.asSuccess()
        }
    }

    private class RecordingScanner(private val files: List<ScannedFile>) : StorageScanner {
        val configs = mutableListOf<WalkConfig>()

        override fun walk(config: WalkConfig): Flow<ScannedFile> {
            configs += config
            return if (files.isEmpty()) emptyFlow() else files.asFlow()
        }
    }

    private class FixedMediaStore(private val images: List<ScannedFile>) : MediaStoreRepository {
        override suspend fun images(): AppResult<ImmutableList<ScannedFile>> =
            images.toImmutableList().asSuccess()

        override suspend fun videos(): AppResult<ImmutableList<ScannedFile>> =
            persistentListOf<ScannedFile>().asSuccess()

        override suspend fun audio(): AppResult<ImmutableList<ScannedFile>> =
            persistentListOf<ScannedFile>().asSuccess()
    }

    private class FixedRoots(private val roots: ImmutableList<String>) : StorageRootProvider {
        override suspend fun readableRoots(): AppResult<ImmutableList<String>> = roots.asSuccess()

        override suspend fun coveredSurfaces(): AppResult<ImmutableList<String>> =
            persistentListOf<String>().asSuccess()
    }

    private class FixedPermissions(private val allFiles: Boolean) : PermissionRepository {
        override fun observe(): Flow<ImmutableSet<AppPermission>> = emptyFlow()

        override fun isGranted(permission: AppPermission): Boolean =
            permission == AppPermission.AllFiles && allFiles

        override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> = persistentSetOf()
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }
}
