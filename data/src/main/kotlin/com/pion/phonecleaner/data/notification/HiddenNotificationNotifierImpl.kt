package com.pion.phonecleaner.data.notification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.data.datastore.NotificationPrefs
import com.pion.phonecleaner.domain.repository.HiddenNotificationNotifier
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.minutes

/**
 * The id-900 summary — `od.i.G` and `od.i.l`
 * (`docs/screens/17-notification-and-permissions.md` §5.1).
 *
 * DECLARED IN `notificationDataModule`. It posts through [AppNotifier], which is the app's one posting
 * point (`coreDataModule`), rather than building a `Notification` itself.
 *
 * ### The throttle, and the one line that matters
 *
 * The timestamp is advanced in a `finally`. The competitor writes it **inside** the `try`, after the
 * post, so a throw leaves it un-advanced and the next intercepted notification retries immediately, in
 * a loop (§5.2). Here the window is spent whether or not the post succeeded.
 *
 * [advertise] no-ops on all three of: notifications disabled, an empty list, and inside the window.
 * None of the three is an error — the caller is a system binder thread's coroutine that has nobody to
 * report to.
 *
 * OPEN (appendix §6 item 1) — whether this summary ships at all, and under which notification policy.
 * Nothing here decides it. What it does fix, if it ships: the competitor's summary re-posts forever and
 * cannot be turned off from either notification screen, and its count is recomputed by hand rather than
 * read from the store.
 */
internal class HiddenNotificationNotifierImpl(
    private val context: Context,
    private val notifier: AppNotifier,
    private val dataStore: DataStore<Preferences>,
    private val clock: AppClock,
    private val log: AppLogger,
) : HiddenNotificationNotifier {

    override suspend fun advertise(count: Int, packageNames: List<String>): AppResult<Unit> {
        if (count <= 0 || packageNames.isEmpty()) return AppResult.Success(Unit)
        if (!notifier.areNotificationsEnabled()) return AppResult.Success(Unit)

        val now = clock.now().toEpochMilliseconds()
        val last = runCatching {
            dataStore.data.first()[NotificationPrefs.SUMMARY_LAST_POSTED_AT] ?: 0L
        }.getOrDefault(0L)
        if (now - last < THROTTLE.inWholeMilliseconds) return AppResult.Success(Unit)

        return try {
            notifier.post(
                AppNotificationSpec(
                    id = SUMMARY_ID,
                    channel = AppNotificationChannel.HiddenNotifications,
                    title = context.getString(com.pion.phonecleaner.data.R.string.hidden_notifications_summary_title),
                    body = context.resources.getQuantityString(
                        com.pion.phonecleaner.data.R.plurals.hidden_notifications_summary_body,
                        count,
                        count,
                    ),
                ),
            )
        } finally {
            // ALWAYS, including on a throw: an un-advanced window is a retry loop (§5.2).
            runCatching {
                dataStore.edit { it[NotificationPrefs.SUMMARY_LAST_POSTED_AT] = now }
            }.onFailure { log.e(it) { "Hidden-notification summary throttle not advanced" } }
        }
    }

    override fun dismissSummary() = notifier.cancel(SUMMARY_ID)

    private companion object {
        /** The competitor's id, kept: "the id-900 summary" (§5.2). */
        const val SUMMARY_ID = 900

        /** The competitor's 10-minute window (`od.i.G`). */
        val THROTTLE = 10.minutes
    }
}
