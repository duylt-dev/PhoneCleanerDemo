package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.map
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The audio collection (`docs/screens/14-file-tools-and-app-manager.md` §4).
 *
 * Structurally [LoadVideosUseCase] with a different collection, and kept apart from it for the reason
 * §4 gives: the two screens diverge — audio has no partial-access variant and no play badge — and one
 * "load media" use case with a collection parameter would put that divergence in a `when` inside a
 * ViewModel instead.
 *
 * **The competitor's 10 s watchdog is not reproduced.** Its audio engine flips a `volatile` flag,
 * stops the cursor loop mid-way and reports success. The screen bounds this call with
 * `withTimeoutOrNull` instead, so an expiry lands in the same `when` as every other outcome and the
 * user is told the answer is partial (§4).
 */
class LoadAudioUseCase(
    private val mediaStore: MediaStoreRepository,
) {
    suspend operator fun invoke(): AppResult<ImmutableList<ScannedFile>> =
        mediaStore.audio().map { it.take(LoadVideosUseCase.MAX_RESULTS).toImmutableList() }
}
