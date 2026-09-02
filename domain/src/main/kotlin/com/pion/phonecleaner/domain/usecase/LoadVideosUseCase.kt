package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.map
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The video collection, newest first (`docs/screens/14-file-tools-and-app-manager.md` §3).
 *
 * The cap is applied here and not in the grid: a `LazyVerticalGrid` rendering 60 of 6 000 still
 * retains all 6 000 (`LLM.md` §8).
 *
 * **A failure stays a failure.** `cd.d.e` swallows a `SecurityException` into an empty map, so a
 * missing permission renders as "no videos" and the user is told nothing; `MediaStoreRepository`
 * returns `AppError.PermissionDenied` and this passes it through untouched.
 */
class LoadVideosUseCase(
    private val mediaStore: MediaStoreRepository,
) {
    suspend operator fun invoke(): AppResult<ImmutableList<ScannedFile>> =
        mediaStore.videos().map { it.take(MAX_RESULTS).toImmutableList() }

    companion object {
        /**
         * UNKNOWN — no source states a cap for the media tools. §1.2 fixes 200 for big files and
         * nothing fixes this one; looked in §3.2 and §4. The same number is used so two lists of the
         * same shape do not disagree, and the screen says nothing that implies it is everything.
         */
        const val MAX_RESULTS: Int = 200
    }
}
