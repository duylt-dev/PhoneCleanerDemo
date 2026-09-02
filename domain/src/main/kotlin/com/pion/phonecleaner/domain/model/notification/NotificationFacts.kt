package com.pion.phonecleaner.domain.model.notification

/**
 * Everything `NotificationInterceptionPolicy` is allowed to know about a posted notification
 * (`docs/screens/17-notification-and-permissions.md` §5).
 *
 * It exists so the competitor's six-test filter becomes a pure function with six unit tests and no
 * `Context`: `StatusBarNotification`, `Notification.flags` and `PackageManager` are read once, in
 * `:data`, and what crosses into `:domain` is six scalars.
 *
 * [category] is `Notification.category`, which the platform documents as nullable — null means the
 * poster set none, not that it is uncategorised in a way the policy may treat as safe.
 */
data class NotificationFacts(
    val packageName: String,
    val isSystemApp: Boolean,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val category: String?,
    val hasContent: Boolean,
)
