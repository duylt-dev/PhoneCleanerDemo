package com.pion.phonecleaner.domain.model.onboarding

/**
 * An opaque handle to whatever the consent form presents itself over.
 *
 * The form is Google's own Activity-hosted dialog, so the request needs an `Activity` — and `:domain`
 * holds no `android.*` type (`LLM.md` §2). This is the same deliberate shape as
 * `PendingIntentToken(val value: Any)` (`LLM.md` §12): **nothing above `:data` ever calls a method on
 * [platformHost]**; the Route builds it, the repository unwraps it, and the ViewModel never sees
 * either — it raises `SplashEffect.RequestConsent` and reduces the answer that comes back.
 *
 * `docs/screens/10-splash-and-onboarding.md:613` writes this interface with no members. It cannot be
 * empty and still carry the host it exists to carry, so it has exactly one, typed `Any`.
 */
interface ConsentHost {
    /** The platform host — an `android.app.Activity` in the Android build. Never called from here. */
    val platformHost: Any
}
