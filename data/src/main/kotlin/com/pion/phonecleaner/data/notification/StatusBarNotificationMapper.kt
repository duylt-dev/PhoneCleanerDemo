package com.pion.phonecleaner.data.notification

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import com.pion.phonecleaner.domain.model.notification.HiddenNotification
import com.pion.phonecleaner.domain.model.notification.NotificationFacts
import kotlin.time.Instant

/**
 * The one place a `StatusBarNotification` is read (`docs/screens/17-notification-and-permissions.md` §5).
 *
 * It exists so `NotificationInterceptionPolicy` can be a pure function over six scalars with six unit
 * tests and no `Context`: the platform is read here, once, and what crosses into `:domain` is data.
 */
internal class StatusBarNotificationMapper(context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * **[HiddenNotification.key] is never null.** `sbn.key` is documented non-null on every supported
     * API, but the competitor's model carries a nullable key *and deletes by it*, so one null-key entry
     * deletes every other null-key entry (§3.5). The fallback is deterministic — `"$package#$posted"` —
     * so the same notification re-posted updates its row rather than adding a second one.
     */
    fun toHiddenNotification(sbn: StatusBarNotification): HiddenNotification {
        val extras = sbn.notification.extras
        return HiddenNotification(
            key = sbn.key ?: "${sbn.packageName}#${sbn.postTime}",
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postedAt = Instant.fromEpochMilliseconds(sbn.postTime),
        )
    }

    /**
     * The six facts the policy is allowed to know.
     *
     * `hasContent` is false when the title **and** the body are both blank. The competitor stores such
     * an entry *and* cancels it, which removes a notification from the shade in exchange for a row the
     * user cannot identify (§5.2).
     */
    fun toFacts(sbn: StatusBarNotification): NotificationFacts {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return NotificationFacts(
            packageName = sbn.packageName,
            isSystemApp = isSystemApp(sbn.packageName),
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            category = notification.category,
            hasContent = title.isNotBlank() || body.isNotBlank(),
        )
    }

    /**
     * A package we cannot resolve is treated as a system app, i.e. **not hidden**. Hiding something we
     * could not identify is the worse of the two failures available here.
     */
    private fun isSystemApp(packageName: String): Boolean = try {
        val flags = packageManager.getApplicationInfo(packageName, 0).flags
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (missing: PackageManager.NameNotFoundException) {
        true
    }
}
