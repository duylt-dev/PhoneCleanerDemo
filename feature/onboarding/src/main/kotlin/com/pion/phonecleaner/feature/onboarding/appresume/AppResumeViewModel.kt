package com.pion.phonecleaner.feature.onboarding.appresume

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.onboarding.AppResumePacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The splash's ramp, minus the consent gate, the permission ask and the `progress == 100`
 * precondition — the competitor's `W()` can leave at any progress
 * (`docs/screens/10-splash-and-onboarding.md` §2.2).
 *
 * The one addition is the 350 ms hand-off, and how it is owned: `ScoutioneActivity.Q()`
 * (`:244-251`) posts it on the base Activity's raw `Handler`, which nothing cancels, so a window
 * torn down inside those 350 ms still runs its callback. Here it is a plain `delay` **inside the same
 * job**, so cancelling the job cancels the hand-off with it.
 *
 * No `android.*` and no `androidx.compose.*` import appears in this file.
 */
class AppResumeViewModel(
    private val pacing: AppResumePacing,
    log: AppLogger,
) : MviViewModel<AppResumeState, AppResumeIntent, AppResumeEffect>(AppResumeState(), log) {

    private var rampJob: Job? = null

    override fun onIntent(intent: AppResumeIntent) {
        when (intent) {
            AppResumeIntent.Shown -> startRamp()
            AppResumeIntent.DismissRequested -> {
                rampJob?.cancel()
                dismiss()
            }
        }
    }

    /**
     * `Toufal.c()` cancels its ramp **and nulls the field**, so the restart path reassigns over a job
     * that may still be running. Here the old job is cancelled and replaced, and the ticker is a
     * structural child, cancelled in the parent's own `finally` — an unfinished child would keep the
     * parent from ever completing (MVI §3).
     */
    private fun startRamp() {
        if (currentState.isDismissing) return
        rampJob?.cancel()
        rampJob = launchSafely(onError = { dismiss() }) {
            val step = (pacing.rampMillis / pacing.progressSteps).coerceAtLeast(1L)
            val ticker = launch {
                repeat(pacing.progressSteps) { i ->
                    delay(step)
                    setState {
                        copy(progress = ((i + 1) * AppResumeState.PROGRESS_MAX) / pacing.progressSteps)
                    }
                }
            }
            try {
                // Bound every wait (MVI §3), so an overrun lands on the same path as a normal finish.
                //
                // [AD GATE: app-open] — this is the wait the ramp exists to cover:
                // `ads.awaitInventory(AppOpen, placement)` raced against the same deadline. The ad
                // boundary has no module owner (`docs/screens/10-splash-and-onboarding.md` §5.3 open
                // item 2), so nothing is called and nothing is faked. Adding it changes this line
                // and nothing else.
                withTimeoutOrNull(pacing.rampMillis) { ticker.join() }
                setState { copy(progress = AppResumeState.PROGRESS_MAX) }
                delay(pacing.dismissDelayMillis)
                dismiss()
            } finally {
                ticker.cancel()
            }
        }
    }

    /** Re-entrancy is guarded in the reducer, not in the UI (MVI §3). */
    private fun dismiss() {
        if (currentState.isDismissing) return
        setState { copy(isDismissing = true) }
        sendEffect(AppResumeEffect.Dismiss)
    }
}
