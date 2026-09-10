package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.map
import com.pion.phonecleaner.domain.model.video.VideoSpaceCheck
import com.pion.phonecleaner.domain.policy.VideoSizeEstimate
import com.pion.phonecleaner.domain.repository.StorageInfoRepository

/**
 * Whether the volume has room for a run (`phase-03-domain-video-compression.md` step 9).
 *
 * **Reuses the existing port — does not read free space itself.** [StorageInfoRepository] is
 * already a `single` in `storageDataModule` (`LLM.md` §6.4); a second free-space reader would be a
 * second answer to the same question, which is precisely the class of bug §6.4 opens with.
 */
class CheckSpaceForCompressionUseCase(
    private val storage: StorageInfoRepository,
) {
    suspend operator fun invoke(estimatedOutputs: List<Long>): AppResult<VideoSpaceCheck> =
        storage.current().map { info ->
            val required = VideoSizeEstimate.requiredFreeBytes(estimatedOutputs)
            val deficit = required + VideoSizeEstimate.FREE_SPACE_FLOOR_BYTES - info.availableBytes
            if (deficit > 0L) VideoSpaceCheck.Short(deficit) else VideoSpaceCheck.Sufficient
        }
}
