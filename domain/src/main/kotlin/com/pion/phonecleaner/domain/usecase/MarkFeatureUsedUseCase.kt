package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository

/**
 * Records that the user opened a feature.
 *
 * Declared **once**, as a `factory` in `domainModule`. Five cluster designs each declared their own
 * copy, and nine of the twelve appendices inject it
 * (`docs/screens/21-shared-models-and-ui.md:6.4`, `docs/screens/18-device-battery-and-apps.md:175` —
 * *"`MarkFeatureUsedUseCase` is already there — do not add a copy"*).
 *
 * The call site is `launchSafely { markFeatureUsed(feature) }` in the intent handler that opened the
 * feature — **fire and forget**. This is bookkeeping the user did not ask for, so a failure is logged
 * and never surfaced (`docs/screens/21-shared-models-and-ui.md:5.3`).
 *
 * It cannot silently no-op, which its competitor equivalent can: `qd.a.d()` returns a nullable static
 * cache that is `null` until a priming call has run, and every call site is `d()?.X0()` — a
 * null-safe call with no else branch (delta G2, same section).
 */
class MarkFeatureUsedUseCase(
    private val featureUsage: FeatureUsageRepository,
) {
    suspend operator fun invoke(feature: FeatureId) = featureUsage.markUsed(feature)
}
