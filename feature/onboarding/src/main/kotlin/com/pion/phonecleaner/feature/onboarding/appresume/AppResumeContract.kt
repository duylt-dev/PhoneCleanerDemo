package com.pion.phonecleaner.feature.onboarding.appresume

import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState

/**
 * Replaces `ScoutioneActivity` (282 L) + `Toufal` (139 L)
 * (`docs/screens/10-splash-and-onboarding.md` §2.1).
 *
 * **Two fields against the competitor's five.** Its `curScene` is the constant `2` and never
 * reassigned, its `hasOpen` is the ad layer's question to answer, and its `overTime` is written at
 * five sites and read at none — there is no `r0()`-style gate on this surface, so it is dead state
 * (`docs/reverse-engineering/10-splash-and-onboarding.md:434`). None of the three is ported.
 *
 * This is **a process-level policy that occasionally needs a window, not a screen**
 * (`LLM.md` §7.5): a `dialog { }` destination on the existing back stack, not an Activity started
 * from `Application` scope with `FLAG_ACTIVITY_NEW_TASK` — background Activity starts are
 * increasingly restricted, and a route has no such problem.
 */
data class AppResumeState(
    /** 0..[PROGRESS_MAX]. The same ramp as the splash, minus the consent gate and the ask. */
    val progress: Int = 0,

    /** The one-shot latch against a double dismissal. Competitor: it has none, and `Q()` is unguarded. */
    val isDismissing: Boolean = false,
) : UiState {

    val isProgressComplete: Boolean get() = progress >= PROGRESS_MAX

    val isBusy: Boolean get() = isDismissing

    companion object {
        const val PROGRESS_MAX = 100
    }
}

/**
 * Two cases the appendix lists are deliberately absent, because nothing in this module could raise
 * them and an Intent nothing raises is dead code that reads as wired:
 *
 * - `AdFinished(shown)` — the ad boundary is carried as "boundaries only, internals out of scope"
 *   (`docs/system-architecture.md` §5.9) and has no module owner (§5.3 open item 2). See
 *   [AppResumeViewModel] for the `[AD GATE: app-open]` marker at the flow point.
 * - `TimedOut` — the ramp lives in the ViewModel, so its expiry is not something the UI observes and
 *   reports back. The competitor's equivalent is a field the Activity writes to itself.
 */
sealed interface AppResumeIntent : UiIntent {

    /** The window is on screen. The ramp starts here, never in `init` (MVI §3.3). */
    data object Shown : AppResumeIntent

    /**
     * Back, while the window is up.
     *
     * The competitor cannot be dismissed: `Q()` is an unconditional 350 ms hand-off and back is
     * swallowed by its base Activity. This surface is one the user did not ask for, so back ends it.
     */
    data object DismissRequested : AppResumeIntent
}

sealed interface AppResumeEffect : UiEffect {
    /** Competitor: `Q()` → `postDelayed(350)` → `finish()` + `overridePendingTransition(0, 0)`. */
    data object Dismiss : AppResumeEffect
}
