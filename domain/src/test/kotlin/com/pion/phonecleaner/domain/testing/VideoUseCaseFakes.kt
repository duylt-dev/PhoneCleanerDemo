package com.pion.phonecleaner.domain.testing

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressProgress
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.repository.CompressedVideoLedger
import com.pion.phonecleaner.domain.repository.VideoCandidateRepository
import com.pion.phonecleaner.domain.repository.VideoCompressor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Hand-written fakes for `:domain`'s own use-case tests (`LLM.md` §9 — no mocking library).
 *
 * Deliberately **separate** from `feature/files/.../testing/FilesFakes.kt`'s equivalents: `:domain`
 * and `:feature:files` are different Gradle modules with no shared test source set, and `LLM.md` §9
 * itself leaves "a fake needed by two modules" as an open question with no `testFixtures`/
 * `:core:testing` module chosen yet. Duplicating three ~10-line fakes is cheaper than inventing one
 * for two callers, and it is the decision recorded here per §9's own instruction to record it the
 * first time it happens.
 */
internal class FakeVideoCandidateRepository(
    var pool: List<VideoCandidate> = emptyList(),
    var failure: AppResult.Failure? = null,
) : VideoCandidateRepository {
    override suspend fun candidates(): AppResult<ImmutableList<VideoCandidate>> =
        failure ?: AppResult.Success(pool.toImmutableList())

    /** Rows for [ids], in the CALLER's order — same contract the port itself states. */
    override suspend fun rowsFor(ids: List<String>): AppResult<ImmutableList<VideoCandidate>> =
        AppResult.Success(ids.mapNotNull { id -> pool.firstOrNull { it.id == id } }.toImmutableList())
}

internal class FakeCompressedVideoLedger(
    ids: Set<String> = emptySet(),
) : CompressedVideoLedger {
    val recorded: MutableSet<String> = ids.toMutableSet()
    override suspend fun compressedIds(): Set<String> = recorded
    override suspend fun record(id: String) {
        recorded += id
    }
}

/** [emissions] only — `CompressVideosUseCaseTest` needs one pass per test, never a held-open run. */
internal class FakeVideoCompressor(
    var emissions: List<VideoCompressProgress> = emptyList(),
) : VideoCompressor {
    override fun compress(
        ids: List<String>,
        preset: VideoQualityPreset,
        codec: VideoCodecOption,
    ): Flow<VideoCompressProgress> = flow { emissions.forEach { emit(it) } }
}
