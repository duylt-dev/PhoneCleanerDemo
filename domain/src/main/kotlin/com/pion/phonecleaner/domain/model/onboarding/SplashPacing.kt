package com.pion.phonecleaner.domain.model.onboarding

/**
 * How long the splash holds the user, and in how many steps the bar moves.
 *
 * Injected rather than constant so a test does not wait it out
 * (`docs/screens/10-splash-and-onboarding.md` §1.2).
 *
 * The values replace `Skiphitec`'s three literals: a **25 000 ms** ceiling, 200 ticks at 125 ms, and
 * every progress value emitted twice — which the observer then silently drops with a monotonic guard
 * (`docs/reverse-engineering/10-splash-and-onboarding.md:387`). 25 s of blank screen.
 * `docs/system-architecture.md:1275` fixes the replacement at 5 000 ms over 100 steps.
 */
data class SplashPacing(
    val timeoutMillis: Long = 5_000L,
    val progressSteps: Int = 100,
)
