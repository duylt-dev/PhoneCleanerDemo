package com.pion.phonecleaner.feature.onboarding.devicecheck

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.onboarding.DeviceCheckPacing
import com.pion.phonecleaner.domain.model.onboarding.DeviceCheckProbe
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoField
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoRow
import com.pion.phonecleaner.domain.model.onboarding.StepStatus
import com.pion.phonecleaner.domain.repository.OnboardingStateRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource

/**
 * Replaces `AssimssesActivity` (881 L) — `docs/screens/10-splash-and-onboarding.md` §3.2.
 *
 * **`init` acts here, and only here.** MVI §3.3 says `init` observes; §3.2 of the appendix grants
 * this screen the exception because it has exactly one job and no permission to ask for. The
 * competitor writes its "seen" flag as the very first statement of `z()`, `commit()`ed on the main
 * thread; the guarantee is kept and the write moved off the main thread.
 *
 * No `android.*` and no `androidx.compose.*` import appears in this file.
 */
class DeviceCheckViewModel(
    private val probe: DeviceCheckProbe,
    onboardingState: OnboardingStateRepository,
    private val pacing: DeviceCheckPacing,
    log: AppLogger,
) : MviViewModel<DeviceCheckState, DeviceCheckIntent, DeviceCheckEffect>(DeviceCheckState(), log) {

    private var sequenceJob: Job? = null

    /** Not part of the rendered state: nothing draws it, and a redraw per lifecycle edge is waste. */
    private val isResumed = MutableStateFlow(true)

    init {
        launchSafely(onError = ::report) { onboardingState.markDeviceCheckSeen() }
        startSequence()
    }

    override fun onIntent(intent: DeviceCheckIntent) {
        when (intent) {
            DeviceCheckIntent.ContinueClicked -> leave()
            // One intent, two competitor call sites. Back does nothing while the sequence runs; once
            // the CTA is live it means the same thing the CTA does.
            DeviceCheckIntent.BackPressed -> if (currentState.isContinueEnabled) leave() else Unit
            DeviceCheckIntent.ScreenResumed -> isResumed.value = true
            DeviceCheckIntent.ScreenPaused -> isResumed.value = false
        }
    }

    /**
     * `flowJob`, with the shape changed and the feel kept (delta 2). The competitor reads all six
     * values *before* the animation starts and then holds each row 1 000 ms with no call in between;
     * each row's probe runs inside its own step here, and [DeviceCheckPacing.perRowMillis] is the
     * floor on that step rather than a sleep after it.
     *
     * `lifecycleScope.launch` with no dispatcher becomes `launchSafely`: `viewModelScope` carries a
     * `SupervisorJob` and **no** `CoroutineExceptionHandler`, so `launchSafely` is the crash floor
     * (MVI §1, delta 11).
     */
    private fun startSequence() {
        sequenceJob?.cancel()
        sequenceJob = launchSafely(onError = ::failOpen) {
            val built = mutableListOf<DeviceInfoRow>()
            awaitResumed(pacing.leadInMillis)
            DeviceInfoField.entries.forEachIndexed { index, field ->
                built += DeviceInfoRow(field, StepStatus.Loading)
                setState { copy(rows = built.toImmutableList(), currentRowIndex = index) }
                sendEffect(DeviceCheckEffect.ScrollToRow(index))

                val startedAt = TimeSource.Monotonic.markNow()
                val reading = probe.read(field)
                built[index] = when (reading) {
                    is AppResult.Success ->
                        built[index].copy(status = StepStatus.Done, value = reading.value)
                    // The row stays value-less and the sequence carries on; the error surfaces once,
                    // above the list, and the CTA comes up at the end regardless (delta 10).
                    is AppResult.Failure -> {
                        report(reading.error)
                        built[index].copy(status = StepStatus.Done)
                    }
                }
                setState { copy(rows = built.toImmutableList()) }
                awaitResumed(pacing.perRowMillis - startedAt.elapsedNow().inWholeMilliseconds)
            }
            setState { copy(isSequenceFinished = true, isContinueEnabled = true, currentRowIndex = null) }
            countDown()
            navigateAway()
        }
    }

    /** `f0()` enables the CTA, then the CTA counts itself down and fires. The CTA is live throughout. */
    private suspend fun countDown() {
        repeat(pacing.countdownSeconds) { elapsed ->
            setState { copy(countdownSeconds = pacing.countdownSeconds - elapsed) }
            awaitResumed(SECOND_MILLIS)
        }
        setState { copy(countdownSeconds = 0) }
    }

    /**
     * `Y(totalMs)` — a delay whose clock **stops** while the screen is not resumed, re-evaluated ten
     * times a second (`docs/reverse-engineering/10-splash-and-onboarding.md:527`). Kept: it is the
     * best-written code in the competitor's cluster, and without it a screen backgrounded for a
     * minute navigates the moment it comes back.
     */
    private suspend fun awaitResumed(totalMillis: Long) {
        var remaining = totalMillis
        while (remaining > 0 && coroutineContext.isActive) {
            isResumed.first { it }
            val slice = minOf(remaining, SLICE_MILLIS)
            delay(slice)
            remaining -= slice
        }
    }

    /** From an intent: the sequence is over whether or not it finished. */
    private fun leave() {
        sequenceJob?.cancel()
        navigateAway()
    }

    /**
     * Re-entrancy is guarded in the reducer, not in the UI (MVI §3). The competitor checks
     * `isNavigating` at every await point *and* again in `b0()`, in three files.
     *
     * [AD GATE: interstitial] — `b0()` raises one here and navigates from its `req_callback`, so the
     * result screen is unreachable when the ad facade's static Activity handle is null
     * (`LLM.md` §7.1). Navigation happens on completion of the **work**; an ad, when the boundary has
     * an owner, is raced beside this line and never in front of it.
     */
    private fun navigateAway() {
        if (currentState.isLeaving) return
        setState { copy(isLeaving = true, isSequenceFinished = true, countdownSeconds = 0) }
        sendEffect(DeviceCheckEffect.NavigateToHome)
    }

    /**
     * The third path `AppResult` does not cover. It must lower every flag the call raised — here that
     * means the CTA comes up, so a crash in the sequence leaves a screen the user can leave.
     */
    private fun failOpen(error: AppError) {
        setState { copy(error = error, isContinueEnabled = true, isSequenceFinished = true) }
    }

    private fun report(error: AppError) = setState { copy(error = error) }

    private companion object {
        const val SECOND_MILLIS = 1_000L

        /** `Y()`'s own slice: the pause is noticed within 100 ms rather than at the next whole step. */
        const val SLICE_MILLIS = 100L
    }
}
