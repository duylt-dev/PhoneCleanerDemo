package com.pion.phonecleaner.domain.model.onboarding

/**
 * The device check's scripted rhythm, replacing `AssimssesActivity`'s `flowJob` literals
 * (`docs/screens/10-splash-and-onboarding.md` §1.4, §3.5 delta 4).
 *
 * `300 + 5 x 400 + 3 x 1 000 = 5 300 ms` against the competitor's **9 300 ms** before it navigates by
 * itself.
 *
 * [perRowMillis] is a **minimum dwell, not a sleep**: the competitor reads all six values *before* the
 * animation starts and then holds each row 1 000 ms of pure theatre (delta 2). Each row's probe runs
 * inside its own step and the remainder of the dwell is what is waited out.
 */
data class DeviceCheckPacing(
    /** Before the first row starts, so the screen is drawn before anything moves. */
    val leadInMillis: Long = 300L,
    /** The floor on one row's step, probe included. */
    val perRowMillis: Long = 400L,
    /** After the last row, counting down on the CTA, which is live throughout. */
    val countdownSeconds: Int = 3,
)
