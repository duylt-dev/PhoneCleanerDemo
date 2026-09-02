package com.pion.phonecleaner.feature.photo.compressor

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.photo.CompressionEstimate
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `compressor` — `docs/screens/13-photo-and-media.md` §3.1. Replaces `ShrinivaActivity` (758 L).
 *
 * The picker only *chooses*. Nothing is re-encoded here: `ContinuePressed` hands the chosen ids to
 * `compressrun`, which owns the write.
 */
data class PhotoCompressorState(
    val phase: ToolPhase = ToolPhase.Idle,
    /** The landing panel. `StartPressed` dismisses it, and only then does the scan begin (§3.2). */
    val introVisible: Boolean = true,
    /** Month buckets, newest first — `PhotoGrouping.byMonth`, the same rule `privacy` uses. */
    val groups: ImmutableList<PhotoGroup> = persistentListOf(),
    val selectedIds: ImmutableSet<PhotoId> = persistentSetOf(),
    /**
     * Measured through the real encoder, never a constant. The competitor's panel states
     * "807KB → 484KB(-40%)" and "up to about 40%" as literal strings that its own engine does not
     * produce (§3.5); `null` here means *not measured yet*, and the screen then says nothing at all.
     */
    val estimate: CompressionEstimate? = null,
    val error: AppError? = null,
) : UiState {
    val selectedCount: Int get() = selectedIds.size
    val selectedBytes: Long
        get() = groups.sumOf { g -> g.photos.sumOf { if (it.id in selectedIds) it.sizeBytes else 0L } }

    /** An empty selection cannot reach the run screen: the button is disabled, not a silent no-op. */
    val canContinue: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0
    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && groups.isEmpty()
    val isBusy: Boolean get() = phase == ToolPhase.Scanning
}

sealed interface PhotoCompressorIntent : UiIntent {
    data object ScreenStarted : PhotoCompressorIntent
    data object StartPressed : PhotoCompressorIntent
    data class PhotoToggled(val id: PhotoId) : PhotoCompressorIntent
    data class MonthToggled(val key: String) : PhotoCompressorIntent
    data object SelectAllToggled : PhotoCompressorIntent
    data object CompletionAnimationFinished : PhotoCompressorIntent
    data object ContinuePressed : PhotoCompressorIntent
    data object BackPressed : PhotoCompressorIntent
}

/**
 * UNKNOWN — §3.1 also declares `ShowMessage(val text: UiText)`. No `UiText` type exists anywhere in
 * this repository (searched `core/common`, `core/ui`, `core/mvi` and every `:feature` module), and
 * inventing one is worse than a blank. The two things it would have carried — a scan failure and an
 * empty result — are [PhotoCompressorState.error] and [PhotoCompressorState.showEmptyState], both
 * rendered by the screen.
 */
sealed interface PhotoCompressorEffect : UiEffect {
    /**
     * The hand-off is **scalars**, not a payload: `compressrun` re-reads the rows from
     * `PhotoRepository`, so process death re-materialises the screen instead of emptying a static
     * field (§4.2). The competitor's equivalent is the second of its three static hand-offs.
     */
    data class OpenCompressRun(val ids: ImmutableList<Long>) : PhotoCompressorEffect
    data object NavigateBack : PhotoCompressorEffect
}
