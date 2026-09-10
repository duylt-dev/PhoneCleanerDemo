package com.pion.phonecleaner.feature.cleanresult

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.repository.CleanupLedger

/**
 * The result screen (`docs/screens/14-file-tools-and-app-manager.md` §8).
 *
 * **It has no `Job` field and starts no work of its own.** Everything it renders either arrived on
 * the route argument or is observed: the count-up is the composable's, and the lifetime total is the
 * ledger's. That is the whole point of folding the competitor's 3 500 ms "Cleaning" destination into
 * a phase — there is nothing left here that can outlive the screen.
 *
 * **It suggests nothing.** The screen reports the run that just finished and offers Done; it does not
 * read `FeatureUsageRepository.staleFeatures()` and cannot navigate to another feature. That is an
 * owner decision (2026-09-03): this build exists to exercise each feature on its own, so a
 * cross-feature convenience layer would be surface with nothing behind it. `staleFeatures()` itself
 * stays — `recommend()` is built on it and the home exit offer still calls that.
 *
 * [summary] is a **Koin parameter**, not a `SavedStateHandle` read: `:feature:cleanresult` cannot
 * name the `@Serializable` route type, which lives in `:app/navigation/Routes.kt` (`LLM.md` §7.2),
 * so `:app` unwraps the argument and the Route passes it down. It survives process death because
 * the navigation back-stack entry holds it.
 *
 * `CleanupLedger.record` is **never** called here. The ledger is fed at the source by the use case
 * that actually freed the bytes (§8, rule 1); recording again on the result screen would double every
 * total the moment a user rotated the device.
 */
class CleanResultViewModel(
    summary: CleanupSummary,
    ledger: CleanupLedger,
    log: AppLogger,
) : MviViewModel<CleanResultState, CleanResultIntent, CleanResultEffect>(
    CleanResultState(summary),
    log,
) {

    init {
        // `init` observes, it does not act (MVI §3). `collectSafely`, never
        // `.onEach { }.launchIn(viewModelScope)`.
        ledger.observeLifetimeFreedBytes().collectSafely { total ->
            setState { copy(lifetimeFreedBytes = total) }
        }
    }

    override fun onIntent(intent: CleanResultIntent) {
        when (intent) {
            CleanResultIntent.CountingAnimationFinished ->
                setState { copy(phase = ResultPhase.Revealed) }

            CleanResultIntent.DonePressed -> sendEffect(CleanResultEffect.NavigateHome)
            CleanResultIntent.BackPressed -> sendEffect(CleanResultEffect.NavigateBack)
        }
    }
}
