package com.pion.phonecleaner.feature.cleanresult

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import kotlinx.collections.immutable.toImmutableList

/**
 * The result screen (`docs/screens/14-file-tools-and-app-manager.md` §8).
 *
 * **It has no `Job` field and starts no work of its own.** Everything it renders either arrived on
 * the route argument or is observed: the count-up is the composable's, and the lifetime total is the
 * ledger's. That is the whole point of folding the competitor's 3 500 ms "Cleaning" destination into
 * a phase — there is nothing left here that can outlive the screen.
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
    private val featureUsage: FeatureUsageRepository,
    ledger: CleanupLedger,
    private val analytics: AnalyticsRepository,
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
        featureUsage.staleFeatures().collectSafely { stale ->
            setState {
                copy(
                    suggestions = stale.asSequence()
                        .filter { it != feature }
                        .take(MAX_SUGGESTIONS)
                        .toList()
                        .toImmutableList(),
                )
            }
        }
    }

    override fun onIntent(intent: CleanResultIntent) {
        when (intent) {
            CleanResultIntent.CountingAnimationFinished ->
                setState { copy(phase = ResultPhase.Revealed) }

            is CleanResultIntent.SuggestionTapped -> openSuggestion(intent.feature)
            CleanResultIntent.DonePressed -> sendEffect(CleanResultEffect.NavigateHome)
            CleanResultIntent.BackPressed -> sendEffect(CleanResultEffect.NavigateBack)
        }
    }

    /**
     * `FeatureOpened` carries [FeatureId], and the wire id is `FeatureId.analyticsId` — on the enum,
     * so a destination and its event can never be edited apart. The competitor keeps the two in
     * unrelated tables and has already transposed a pair of them.
     */
    private fun openSuggestion(feature: FeatureId) {
        analytics.track(AnalyticsEvent.FeatureOpened(feature))
        sendEffect(CleanResultEffect.NavigateToFeature(feature))
    }

    private companion object {
        /** Three tiles fit above the fold; a longer list is a menu, not a suggestion. */
        const val MAX_SUGGESTIONS = 3
    }
}
