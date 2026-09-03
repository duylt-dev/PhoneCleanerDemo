package com.pion.phonecleaner.feature.device.batteryscan

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.device.BatteryCheck
import com.pion.phonecleaner.domain.model.device.ScanStep
import com.pion.phonecleaner.domain.model.device.StepState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * `batteryscan` (`docs/screens/18-device-battery-and-apps.md` §4.1). Replaces `HazartarActivity`.
 *
 * **Always every row, always in `BatteryCheck` order.** The competitor grows its `RecyclerView`
 * one row at a time, which is why its list cannot be restored after a config change; here the list is
 * fixed-size and immutable and only `state` changes.
 *
 * `BatteryCheck` itself lives in `:domain/model/device` rather than in this file, because [ScanStep]
 * — a `:domain` type — has a field of that type and `:domain` cannot see a `:feature` package.
 */
@Immutable
data class BatteryScanState(
    val rows: ImmutableList<ScanStep> = defaultRows(),
    val isFinished: Boolean = false,
    /**
     * Folds the Activity field `scanning`: back is refused while this is true.
     *
     * Deliberately **not** derived from [isFinished]. They agree today and stop agreeing the moment a
     * "cancel scan" affordance is added; the flag is named after what it governs. If that ever needs
     * fixing, the fix is a computed `val` — never two stored fields that can disagree.
     */
    val isBackBlocked: Boolean = true,
    val error: AppError? = null,
) : UiState

sealed interface BatteryScanIntent : UiIntent {
    data object BackPressed : BatteryScanIntent
}

sealed interface BatteryScanEffect : UiEffect {
    /** The route pops itself: `popUpTo(self) { inclusive = true }` (§8). */
    data object NavigateToBatteryInfo : BatteryScanEffect
    data object NavigateBack : BatteryScanEffect
    data object ShowScanInProgressMessage : BatteryScanEffect
}

private fun defaultRows(): ImmutableList<ScanStep> =
    BatteryCheck.entries.map(::ScanStep).toImmutableList()

/**
 * **No element is ever mutated in place.** The competitor's setter mutates the same objects its
 * adapter's second `ArrayList` holds, which is why it needs `notifyItemChanged` called with exactly
 * the right index; here the list is rebuilt by `copy` and a wrong index cannot desynchronise
 * anything.
 */
internal fun ImmutableList<ScanStep>.markRunning(check: BatteryCheck): ImmutableList<ScanStep> =
    map { step ->
        when (step.id) {
            check -> step.copy(state = StepState.Running)
            // Everything before the running row has already been passed, so it is done.
            else -> if (step.id.ordinal < check.ordinal) step.copy(state = StepState.Done) else step
        }
    }.toImmutableList()

internal fun ImmutableList<ScanStep>.markAllDone(): ImmutableList<ScanStep> =
    map { it.copy(state = StepState.Done) }.toImmutableList()
