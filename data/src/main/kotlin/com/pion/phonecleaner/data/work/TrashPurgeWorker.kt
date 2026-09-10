package com.pion.phonecleaner.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.usecase.PurgeExpiredTrashUseCase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Removes what the bin has held for two days. **The app's first WorkManager worker**, so it is also
 * the shape every later one copies (`docs/android-mvi-best-practices.md` §12).
 *
 * ### Why WorkManager and not "purge when the app opens"
 *
 * The screen already purges on open, and that alone would be enough for a user who opens the app. It
 * is not enough for the one who does not: the bin would hold their files indefinitely on the device
 * they installed a cleaner to free. Anything that must survive a swipe-kill or a reboot is
 * WorkManager and nothing else (`LLM.md` §6.4).
 *
 * ### Why nothing is ever purged early
 *
 * Expiry is read from the row's stored `expires_at`, written at insert from
 * `TrashRetention.expiresAt(trashedAt)` — never from "older than two days measured now". A late worker
 * therefore only keeps a file longer, which is the safe direction. In the RESTRICTED App Standby
 * bucket a periodic job is throttled to roughly ten minutes of execution per four-hour window, so an
 * entry can outlive its two days; nothing is lost by that and the next run takes it.
 *
 * ### Why every outcome is `Result.success()`
 *
 * This is periodic. It runs again in twelve hours whatever happens, so `Result.retry()` would only
 * delay the next period, and a permanently unwritable root would retry for ever. Failures are logged
 * and the next run tries again. A ONE-SHOT worker would want the opposite; do not copy this line into
 * one.
 */
internal class TrashPurgeWorker(
    appContext: Context,
    params: WorkerParameters,
    private val purgeExpiredTrash: PurgeExpiredTrashUseCase,
    private val log: AppLogger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            when (val outcome = purgeExpiredTrash()) {
                is AppResult.Failure -> log.e { "Trash purge failed: ${outcome.error}" }
                is AppResult.Success -> log.d { "Trash purge removed ${outcome.value.purgedIds.size}" }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.e(failure) { "Trash purge threw" }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "trash-purge"

        /**
         * Twelve hours, not twenty-four: an entry that expires just after a run then waits at most
         * twelve hours past its two days rather than a whole extra day. The floor WorkManager enforces
         * is fifteen minutes, so this is nowhere near it.
         *
         * **No constraints.** This is local file I/O — no network, no meaningful battery cost. Every
         * constraint added here is one more reason the job never runs, and `setRequiresDeviceIdle`
         * alone can hold a job off a device that is never idle.
         */
        fun request(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<TrashPurgeWorker>(12, TimeUnit.HOURS).build()
    }
}
