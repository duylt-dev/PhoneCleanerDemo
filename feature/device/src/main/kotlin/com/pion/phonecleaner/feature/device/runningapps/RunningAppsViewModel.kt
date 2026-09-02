package com.pion.phonecleaner.feature.device.runningapps

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import com.pion.phonecleaner.domain.usecase.ListStoppableAppsUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ReadMemoryUseCase
import com.pion.phonecleaner.domain.usecase.ReadUsageAccessUseCase
import com.pion.phonecleaner.domain.usecase.VerifyAppStoppedUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * `runningapps` (`docs/screens/18-device-battery-and-apps.md` §6.2).
 *
 * **This ViewModel never calls `killBackgroundProcesses`, never starts an `Intent` and never touches
 * `PackageManager` for icons.** The first is undeclared, returns `void`, sits inside an empty `catch`
 * in the competitor and cannot be verified at all; the second is the Route's job; the third is
 * `AppIconLoader`'s, in the composable.
 *
 * **A Stop is verified by observation or not claimed.** The Settings deep link reports nothing about
 * what the user did, so the only honest signal is `FLAG_STOPPED`, re-read for that one package on the
 * way back in (§6.5). If the bit is not set, the row stays and we say nothing.
 *
 * **No `Handler`, no `postDelayed`.** The competitor posts its coach-mark runnable on the *binding
 * root view*, tying a 500 ms timer to a `View`'s attachment state, and it races the Settings launch
 * in both directions. Here the order of operations is the whole delta: sheet → the user taps "Open
 * settings" → `OpenSystemAppInfo` → `startActivity`.
 *
 * ANALYTICS — deliberately absent, and not fabricated. §7 asks for `ForceStopInstructionsShown` /
 * `…Dismissed` / `…OpenSettingsTapped` so the most fragile step in the funnel finally has a
 * denominator. `AnalyticsEvent` is a sealed interface in another module with six arms and none of
 * them is one of these; a sealed interface admits implementations only in its own module, so a local
 * event type is not an option either. The three arms are reported for the owner to add, and no
 * unused `AnalyticsRepository` is injected here to look as though they were wired.
 *
 * ### PENDING OWNER DECISION 3 — §0.1, UNSETTLED
 *
 * [readUsageAccess] is read and rendered; it **gates nothing**. Under option A it becomes the
 * precondition and `ListStoppableAppsUseCase` changes its source behind the same signature; under
 * option B the surface is deleted with one `when` arm. Neither outcome touches the rest of this file.
 */
class RunningAppsViewModel(
    private val session: DeviceScanSessionStore,
    private val readMemory: ReadMemoryUseCase,
    private val listStoppableApps: ListStoppableAppsUseCase,
    private val verifyAppStopped: VerifyAppStoppedUseCase,
    private val readUsageAccess: ReadUsageAccessUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger,
) : MviViewModel<RunningAppsState, RunningAppsIntent, RunningAppsEffect>(RunningAppsState(), log) {

    /** True when `runningappsscan` handed a list over, so `init` need not enumerate for itself. */
    private var wasSeeded = false

    private var isFirstResume = true

    init {
        launchSafely { markFeatureUsed(FeatureId.RunningApps) }

        // A session is consumed once: taken, then cleared.
        val seed = session.runningApps
        session.runningApps = null
        if (seed != null) {
            wasSeeded = true
            setState { copy(apps = seed) }
        }

        // The session-lost branch §0.2 requires: with an empty store — process death, or a deep
        // link — the screen loads for itself instead of rendering a blank frame.
        refresh(verifyPending = false, includeApps = !wasSeeded)
        readAccess()
    }

    override fun onIntent(intent: RunningAppsIntent) {
        when (intent) {
            RunningAppsIntent.ScreenResumed -> onScreenResumed()

            // It emits nothing. The sheet is state, and the trip to Settings starts from the sheet's
            // own primary action — never from the tap that opened it.
            is RunningAppsIntent.StopTapped -> setState {
                copy(
                    awaitingForceStopOf = intent.packageName,
                    instructionsFor = intent.packageName,
                )
            }

            RunningAppsIntent.InstructionsOpenSettingsTapped -> onOpenSettings()

            // A user who backs out of the sheet never leaves the app, so nothing is pending either.
            RunningAppsIntent.InstructionsDismissed -> setState {
                copy(instructionsFor = null, awaitingForceStopOf = null)
            }

            RunningAppsIntent.UsageAccessGrantTapped ->
                sendEffect(RunningAppsEffect.OpenUsageAccessSettings)

            RunningAppsIntent.UsageAccessDismissed -> setState { copy(isUsageAccessDismissed = true) }

            RunningAppsIntent.RetryTapped -> refresh(verifyPending = false, includeApps = true)
            RunningAppsIntent.SkipTapped -> sendEffect(RunningAppsEffect.NavigateBack)
            RunningAppsIntent.BackPressed -> sendEffect(RunningAppsEffect.NavigateBack)
        }
    }

    private fun onOpenSettings() {
        val packageName = currentState.instructionsFor ?: return
        setState { copy(instructionsFor = null) }
        sendEffect(RunningAppsEffect.OpenSystemAppInfo(packageName))
    }

    /**
     * The resume that matters is the one after the Settings trip, and it does two things in one
     * pass: re-enumerate, then re-read `FLAG_STOPPED` for the pending package.
     *
     * The enumeration already filters `FLAG_STOPPED`, so a genuinely stopped app usually leaves the
     * list on its own; [verifyPendingStop] covers the case where the two reads disagree — and it is
     * the only thing that may ever set `RunningApp.isStopped`.
     */
    private fun onScreenResumed() {
        val isFirst = isFirstResume
        isFirstResume = false

        // The FIRST resume is the frame the screen opened in, and `init` has already supplied the
        // list — from the session store, or from its own refresh. Enumerating again here is the
        // two-passes-back-to-back defect §6.4 records, arriving by a different road. Memory is
        // re-read either way: the ring is per-screen, and the scan's value is a beat old.
        refresh(verifyPending = !isFirst, includeApps = !isFirst)
    }

    /**
     * Idempotent. A resume during an in-flight refresh starts a second one and the second `setState`
     * wins; if that ordering ever matters the fix is `refreshJob?.cancelAndJoin()` at the top —
     * never a `@Volatile` guard.
     */
    private fun refresh(verifyPending: Boolean, includeApps: Boolean) {
        setState { copy(isRefreshing = includeApps, error = null) }
        launchSafely(onError = ::onFailure) {
            coroutineScope {
                val memoryRead = async { readMemory() }
                val appsRead = async { if (includeApps) listStoppableApps() else null }

                val memoryValue = memoryRead.await().getOrNull()
                val appsResult = appsRead.await()

                setState {
                    copy(
                        // A failed memory read keeps the previous value rather than blanking a ring
                        // that was correct a second ago; a first failure leaves it null, which is a
                        // shimmer.
                        memory = memoryValue ?: memory,
                        apps = appsResult?.getOrNull() ?: apps,
                        isRefreshing = false,
                        error = appsResult?.errorOrNull(),
                    )
                }
            }
            if (verifyPending) verifyPendingStop()
        }
    }

    private suspend fun verifyPendingStop() {
        val packageName = currentState.awaitingForceStopOf ?: return
        val isStopped = verifyAppStopped(packageName).getOrNull() == true
        setState { withVerifiedStop(packageName, isStopped) }
    }

    /**
     * PENDING OWNER DECISION 3 (§0.1). Read once per screen entry; it gates nothing today. The read
     * is an `AppOpsManager` check, not a permission check — there is no `checkSelfPermission` answer
     * for a special access.
     */
    private fun readAccess() {
        launchSafely {
            val access = readUsageAccess()
            setState { copy(usageAccess = access) }
        }
    }

    /**
     * Lowers `isRefreshing` as well as setting `error`. `launchSafely`'s catch path reaches neither
     * `AppResult` arm, so a flag it does not lower stays raised — and here the raised flag is also
     * what `isEmpty` reads, so the screen would show neither a list, an empty state nor an error.
     */
    private fun onFailure(error: AppError) {
        setState { copy(isRefreshing = false, error = error) }
        sendEffect(RunningAppsEffect.ShowMessage(error))
    }
}
