package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.onboarding.OnboardingState
import kotlinx.coroutines.flow.Flow

/**
 * The one port over the first-run latches (`docs/screens/10-splash-and-onboarding.md` §1.4).
 *
 * **OWNERSHIP IS UNRESOLVED AND MUST BE SETTLED DELIBERATELY.** `docs/system-architecture.md` §5.7
 * lists ten per-cluster `:data` modules and none of them is an onboarding module, so this binding has
 * no declared home; the same appendix's §5.3 open item 1 says so in as many words. The
 * implementation and an `onboardingDataModule` ship with this cluster, and the module is reported to
 * the assembly point rather than added to `:app` here.
 *
 * **Do not add a second repository over these keys.** `docs/screens/11-home.md:500` records
 * `AppSettingsRepository` — `hasCompletedFirstRun` / `hasAcceptedTerms` — as an open binding with no
 * owner. Those are two fields of [OnboardingState]; a separate `single<AppSettingsRepository>` over
 * the same DataStore keys is two writers to one latch, and Koin resolves the duplicate silently by
 * load order (`LLM.md` §6.4).
 *
 * Every write is one key. The competitor's preference layer rewrites a whole key set with `commit()`
 * on the calling thread (`java/od/d0.java:78-81`).
 */
interface OnboardingStateRepository {

    /**
     * The latches, re-emitting on every write. **Observe, don't fetch** (MVI §5): the splash's
     * consent row is correct after the write that accepts the terms without the screen re-entering.
     */
    fun observe(): Flow<OnboardingState>

    /**
     * The user reached home. Called by the **home** cluster on its first creation, matching
     * `EstissueActivity.z1()` — not by the splash (`docs/screens/11-home.md:356` delta 17).
     */
    suspend fun markFirstRunCompleted()

    /** The user left the splash with the policies box ticked. Hides the consent row from then on. */
    suspend fun markTermsAccepted()

    /** The one-time device check has been shown. Written when that screen opens, off the main thread. */
    suspend fun markDeviceCheckSeen()

    /**
     * The notification-permission ask has been answered — **whatever the answer was**. The outcome is
     * deliberately not recorded: the platform is the authority on whether the permission is held, and
     * a second copy of that answer is a second thing to keep in step.
     */
    suspend fun markNotificationAskAnswered()
}
