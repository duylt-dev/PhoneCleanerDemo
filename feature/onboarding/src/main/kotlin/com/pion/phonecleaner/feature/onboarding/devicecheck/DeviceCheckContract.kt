package com.pion.phonecleaner.feature.onboarding.devicecheck

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Folds `AssimssesActivity`'s eight Activity fields plus the `Striden.appState` enum its adapter
 * mutated **in place** (`docs/screens/10-splash-and-onboarding.md` §3.1).
 *
 * `steps` there is `lateinit`, guarded by nine `throwUninitializedPropertyAccessException` checks
 * (delta 8). Here [rows] defaults to `persistentListOf()` and **the empty list is the initial
 * state** — the first 300 ms of the competitor's screen renders zero items too, it just cannot say
 * so in its type.
 */
data class DeviceCheckState(
    /**
     * Competitor: `steps: List<Striden>`, whose rows carry a `var` status shared with the adapter's
     * own list. Here `status` is a `val`, the reducer produces a new row, and the `LazyColumn` diffs
     * by `DeviceInfoField` (`LLM.md` §8).
     *
     * The list is capped by construction at the five `DeviceInfoField` constants — the cap is in the
     * ViewModel, never in the UI (`LLM.md` §8).
     */
    val rows: ImmutableList<DeviceInfoRow> = persistentListOf(),

    /** Competitor: `currentStep`, initialised to `-1`. Null is the honest spelling of "none yet". */
    val currentRowIndex: Int? = null,

    /** Competitor: `enhanuccee.isEnabled` — a view property read back at three sites. */
    val isContinueEnabled: Boolean = false,

    /** Competitor: the countdown local inside `flowJob`, 3 → 0. */
    val countdownSeconds: Int = 0,

    /** Competitor: `isNavigating`, checked at every await point. */
    val isLeaving: Boolean = false,

    /** Competitor: `flowFinished` — there, only used to stop the header animation pausing. */
    val isSequenceFinished: Boolean = false,

    /**
     * A probe that failed. The row keeps a null value and renders as "not read" rather than as a
     * number; the CTA comes up regardless, so a failed reading never traps the user (delta 10).
     */
    val error: AppError? = null,
) : UiState {

    val isCountingDown: Boolean get() = countdownSeconds > 0

    val isBusy: Boolean get() = isLeaving
}

/**
 * `AdFinished(shown)` from the appendix is deliberately absent: the ad boundary is carried as
 * "boundaries only, internals out of scope" (`docs/system-architecture.md` §5.9) and has no module
 * owner (`docs/screens/10-splash-and-onboarding.md` §5.3 open item 2). The marker is at the flow
 * point in [DeviceCheckViewModel], and nothing here pretends to be wired to it.
 */
sealed interface DeviceCheckIntent : UiIntent {

    data object ContinueClicked : DeviceCheckIntent

    /**
     * One intent for what the competitor implements twice — `e()` at `:405` and `onKeyDown` at
     * `:814`, which are allowed to disagree and eventually will.
     */
    data object BackPressed : DeviceCheckIntent

    /** Competitor: the `RESUMED` half of `Y()`'s lifecycle-aware delay. The clock stops while paused. */
    data object ScreenResumed : DeviceCheckIntent

    data object ScreenPaused : DeviceCheckIntent
}

sealed interface DeviceCheckEffect : UiEffect {

    data object NavigateToHome : DeviceCheckEffect

    /** Competitor: `thirlea.smoothScrollToPosition(index)`. */
    data class ScrollToRow(val index: Int) : DeviceCheckEffect
}
