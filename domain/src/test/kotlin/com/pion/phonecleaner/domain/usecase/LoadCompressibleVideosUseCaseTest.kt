package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.testing.FakeCompressedVideoLedger
import com.pion.phonecleaner.domain.testing.FakeVideoCandidateRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `phase-03-domain-video-compression.md` step 9, D7. Fixture sizes are written relative to
 * [LoadCompressibleVideosUseCase.MIN_COMPRESSIBLE_BYTES] rather than a literal — the floor itself
 * moved once already (20 MiB -> 5 MiB, measured on-device, `LoadCompressibleVideosUseCase.kt`'s own
 * KDoc), and a test that hardcodes the old or the new number breaks on the next re-measurement instead
 * of on an actual filter regression.
 */
class LoadCompressibleVideosUseCaseTest {

    private val ledger = FakeCompressedVideoLedger()
    private val repository = FakeVideoCandidateRepository()
    private val useCase = LoadCompressibleVideosUseCase(repository, ledger)

    @Test
    fun `rows below the floor are dropped`() = runTest {
        repository.pool = listOf(clip("small", belowFloor()))

        val result = useCase() as AppResult.Success

        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `a row in the ledger comes back alreadyCompressed and stays in the list`() = runTest {
        repository.pool = listOf(clip("v1", aboveFloor()))
        ledger.recorded += "v1"

        val result = useCase() as AppResult.Success

        assertEquals(1, result.value.size)
        assertTrue(result.value.single().alreadyCompressed)
    }

    /**
     * D7's whole point, and the case that actually happens: a successful transcode is what drops a
     * row under the floor. Drop `|| it.id in known` from the use case and this assertion is what goes
     * red — the row would otherwise vanish from its own evidence.
     */
    @Test
    fun `a row in the ledger AND below the floor is still in the list`() = runTest {
        repository.pool = listOf(clip("v1", belowFloor()))
        ledger.recorded += "v1"

        val result = useCase() as AppResult.Success

        assertEquals(1, result.value.size)
        assertTrue(result.value.single().alreadyCompressed)
    }

    @Test
    fun `a repository failure passes straight through, never an empty list`() = runTest {
        repository.failure = AppResult.Failure(AppError.PermissionDenied())

        val result = useCase()

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.PermissionDenied(), (result as AppResult.Failure).error)
    }

    private companion object {
        fun belowFloor() = LoadCompressibleVideosUseCase.MIN_COMPRESSIBLE_BYTES - 1L
        fun aboveFloor() = LoadCompressibleVideosUseCase.MIN_COMPRESSIBLE_BYTES * 10L

        fun clip(id: String, sizeBytes: Long) = VideoCandidate(
            file = ScannedFile(
                id = id,
                path = "/movies/$id.mp4",
                name = "$id.mp4",
                sizeBytes = sizeBytes,
                kind = FileKind.Video,
                origin = FileOrigin.MediaStoreEntry("content://video/$id"),
            ),
            durationMs = 60_000L,
            width = 1920,
            height = 1080,
        )
    }
}
