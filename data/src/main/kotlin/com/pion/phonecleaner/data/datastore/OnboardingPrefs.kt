package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * The key table for the three first-run latches (`docs/system-architecture.md` §7.3, "onboarding and
 * consent latches"; `LLM.md` §3.6).
 *
 * They replace the competitor's **three disagreeing first-run flags** — `flux_first_flux_value`,
 * `flux_first_launcher_phone_check_shown` and `need_req_noti_perm_spla`
 * (`docs/screens/10-splash-and-onboarding.md:293`).
 *
 * **The rule that comes with [NOTIFICATION_PERMISSION_ANSWERED]:** it is written *after* an answer,
 * never before the ask. `CucurtagActivity:678` writes its flag `false` **before** showing the dialog,
 * so a swipe-away or a process death burns the only ask the app ever makes
 * (`docs/screens/10-splash-and-onboarding.md:310` delta 2). This file cannot enforce that; the
 * cluster's `OnboardingStateRepository` must.
 *
 * This is a key table only. The latch *policy* — what each one gates, and in which order the splash
 * reads them — belongs to `OnboardingStateRepository` in the onboarding cluster's own data module
 * (`docs/screens/10-splash-and-onboarding.md:293`), which is not this agent's to write.
 *
 * UNKNOWN — the key strings; see [FeatureUsagePrefs] for where names were looked for. Ours, not the
 * competitor's, so `LegacyPrefsMigrationWorker` cannot read its own output.
 */
internal object OnboardingPrefs {

    /** True once the first-run flow has been completed at least once. */
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    /** True once the one-time device-check screen has been shown. */
    val DEVICE_CHECK_SHOWN = booleanPreferencesKey("onboarding_device_check_shown")

    /** True once the user has ANSWERED the notification-permission ask — accepted or refused. */
    val NOTIFICATION_PERMISSION_ANSWERED = booleanPreferencesKey("onboarding_notification_ask_answered")
}
