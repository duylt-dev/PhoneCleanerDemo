package com.pion.phonecleaner.domain.model.onboarding

/**
 * The first-run latches, as ONE record with one write point per flag.
 *
 * It replaces the competitor's **three disagreeing first-run flags** — `flux_first_flux_value`,
 * `flux_first_launcher_phone_check_shown` and `need_req_noti_perm_spla`
 * (`docs/screens/10-splash-and-onboarding.md:293`, delta 10) — and it also closes
 * `docs/screens/11-home.md` delta 17, where the single string `flux_first_flux_value` answers **two
 * unrelated questions**: "has the user seen the consent row?" and "is this the first launch?".
 *
 * That delta names the split `AppSettingsRepository.hasCompletedFirstRun` / `hasAcceptedTerms`.
 * **Both booleans are here**, on this one record, because a second repository over the same two keys
 * is exactly the duplicate-binding defect `LLM.md` §6.4 exists to prevent: two `single`s, two writers,
 * and Koin picking a winner by module load order with no error.
 *
 * `hasCompletedFirstRun` is written by **home**, on its first creation — that is what
 * `EstissueActivity.z1()` (`glossnsfe/attoin/EstissueActivity.java:1314-1319`) does, and moving the
 * write to the splash would change when the first-launch branch flips. `hasAcceptedTerms` is written
 * by the splash, when the user leaves it with the box ticked.
 */
data class OnboardingState(
    /**
     * The user has reached home at least once. Read by the first-launch branch; **not** by the
     * splash's consent row (that is [hasAcceptedTerms]).
     */
    val hasCompletedFirstRun: Boolean = false,
    /** The user ticked the policies box and left the splash. The consent row is shown while false. */
    val hasAcceptedTerms: Boolean = false,
    /** The one-time device-check screen has been shown. The splash routes past it once true. */
    val hasSeenDeviceCheck: Boolean = false,
    /**
     * The notification-permission ask has been **ANSWERED** — accepted or refused.
     *
     * Written after an answer, never before the ask. `CucurtagActivity:678` writes its flag `false`
     * *before* showing the dialog, so a swipe-away or a process death burns the only ask the app
     * ever makes (`docs/screens/10-splash-and-onboarding.md:310` delta 2, `LLM.md` §7.4).
     */
    val hasAnsweredNotificationAsk: Boolean = false,
)
