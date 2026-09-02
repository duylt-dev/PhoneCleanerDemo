package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.policy.MinimumDuration
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.HiddenNotificationNotifier
import com.pion.phonecleaner.domain.repository.NotificationCleanerRepository

/**
 * Wipes the store and returns how many rows went
 * (`docs/screens/17-notification-and-permissions.md` §0.3).
 *
 * **The on-screen floor lives here, through the injected [MinimumDuration] from `coreModule`** — not as
 * a `delay` in a ViewModel, and not as a top-level `withMinimumDuration` helper. `MinimumDuration` uses
 * `TimeSource.Monotonic`, so a wall-clock change mid-clear cannot produce a negative wait, and a test
 * injects `Duration.ZERO` to skip it (`LLM.md` §3.2).
 *
 * The wipe happens first and the wait wraps it, which is the competitor's order too — but here the count
 * is durable, because it travels back to `ClearStage.Finished` in ViewModel state rather than living in
 * an Activity field the next rotation resets (§3.5).
 *
 * The id-900 summary is dismissed on the way out: leaving a "we hid N notifications" notification up
 * after the store is empty is the one thing the clear must not do.
 *
 * Registered `factoryOf(::ClearHiddenNotificationsUseCase)` in `domainModule`.
 */
class ClearHiddenNotificationsUseCase(
    private val repository: NotificationCleanerRepository,
    private val notifier: HiddenNotificationNotifier,
    private val minimumDuration: MinimumDuration,
) {
    suspend operator fun invoke(): AppResult<Int> = minimumDuration.around {
        val result = repository.clearAll()
        if (result is AppResult.Success) notifier.dismissSummary()
        result
    }
}
