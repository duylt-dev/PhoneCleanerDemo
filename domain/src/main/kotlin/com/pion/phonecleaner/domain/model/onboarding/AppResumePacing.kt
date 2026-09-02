package com.pion.phonecleaner.domain.model.onboarding

/**
 * The app-resume window's own timings (`docs/screens/10-splash-and-onboarding.md` §2.2).
 *
 * [dismissDelayMillis] is the competitor's `Q()` → `postDelayed(350)` → `finish()`
 * (`ScoutioneActivity`), kept as a value and awaited as a **structural child of the same job** rather
 * than on the base Activity's raw `Handler`, which nothing cancels.
 *
 * UNKNOWN — no source fixes the app-resume ramp's length. `docs/system-architecture.md:1275` settles
 * the **cold splash** at 5 000 ms and says nothing about this surface; the competitor reuses its 25 s
 * ramp here (`docs/reverse-engineering/10-splash-and-onboarding.md:386`), which is the value that
 * document calls wrong. Looked for, and not found: a figure in `docs/system-architecture.md` §10, in
 * `docs/screens/10-splash-and-onboarding.md` §2, and in the chapter's §5. The splash's own 5 000 ms is
 * carried here as the conservative choice, not as a checked one.
 */
data class AppResumePacing(
    val rampMillis: Long = 5_000L,
    val progressSteps: Int = 100,
    val dismissDelayMillis: Long = 350L,
)
