package com.pion.phonecleaner.data.onboarding

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.onboarding.ConsentHost
import com.pion.phonecleaner.domain.model.onboarding.ConsentOutcome
import com.pion.phonecleaner.domain.model.onboarding.ConsentStatus
import com.pion.phonecleaner.domain.repository.ConsentRepository
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The consent seam. **The Google UMP SDK is deliberately not integrated here.**
 *
 * Consent- and ad-SDK internals are out of scope for this project
 * (`docs/system-architecture.md` §5.9: the ad and remote-config facade becomes "the `AdGateway` /
 * `AdHost` / `RemoteConfig` boundaries only; internals out of scope"). The convention used throughout
 * `docs/` is a one-line `[AD GATE: <format>]` marker at the flow point, and that is what
 * [requestConsent] carries. What ships is the shape: one instance, resolved lazily, holding its own
 * state — never the static `ConsentInformation` field of `md.l3` (`java/md/l3.java:23`), which throws
 * `throwUninitializedPropertyAccessException` on eight paths.
 *
 * ### What integrating it means, and what must not change when it happens
 *
 * - The SDK call goes **inside** [requestConsent], behind the marker below. Nothing above this class
 *   learns that it exists: `SplashViewModel` raises an Effect and reduces `ConsentResolved`.
 * - `md.l3.k()` calls `j()`, and `j()` has an empty body (`:163-172`, `:183-184`). Both are deleted
 *   rather than ported — ad-SDK initialisation belongs to the ad boundary, not to a consent gateway.
 * - `ConsentDebugSettings` and test-device ids go in **behind `BuildConfig.DEBUG`**, or the form is
 *   untestable before release (`docs/screens/10-splash-and-onboarding.md:623`).
 * - The whole `IABTCF_PurposeConsents` bitstring becomes `ConsentStatus.purposeConsents`. `md.l3.h()`
 *   reads only its first character (`:133-146`).
 *
 * ### Until then
 *
 * It answers [ConsentOutcome.Unavailable] with `canRequestAds = false`. That is the honest, and the
 * conservative, reading of "no consent has been collected": the splash's gate settles and the user is
 * not held, while nothing is told it may request personalised ads. It never claims a `Granted` the
 * user was never asked for.
 */
internal class UmpConsentRepository(
    private val log: AppLogger,
) : ConsentRepository {

    private val status = MutableStateFlow(NOT_COLLECTED)

    /**
     * [host] is accepted and, today, unused: it is the `Activity` the form would present itself over,
     * and it is on the signature so that integrating the SDK does not change the contract, the Route
     * or the ViewModel. Never dereferenced here — `platformHost` is `Any` for exactly that reason
     * (`LLM.md` §12).
     */
    override suspend fun requestConsent(host: ConsentHost): AppResult<ConsentStatus> {
        // [AD GATE: UMP consent form]
        // UserMessagingPlatform.getConsentInformation(host.platformHost as Activity)
        //     .requestConsentInfoUpdate(...) then loadAndShowConsentFormIfRequired(...)
        // goes here, and its result replaces the value below. Out of scope for this project.
        log.d { "Consent form not integrated; reporting ${NOT_COLLECTED.outcome}" }
        status.value = NOT_COLLECTED
        return NOT_COLLECTED.asSuccess()
    }

    override fun observeStatus(): Flow<ConsentStatus> = status.asStateFlow()

    private companion object {
        /**
         * No form has run, so nothing has been consented to. `canRequestAds = false` is the default
         * that cannot be wrong: the competitor's splash discards the whole return code and proceeds
         * identically whatever the user chose (`CucurtagActivity.java:274`).
         */
        val NOT_COLLECTED = ConsentStatus(
            outcome = ConsentOutcome.Unavailable,
            canRequestAds = false,
            purposeConsents = persistentSetOf(),
        )
    }
}
