package com.pion.phonecleaner.data.notification

import com.pion.phonecleaner.core.common.result.AppResult

/**
 * **The one place a notification is posted.** Declared once, in `coreDataModule`
 * (`LLM.md` §6.4, `docs/system-architecture.md` §5.4 — claimed by clusters 08 and 11).
 *
 * It replaces `wd.r0` + `wd.j` + `hc.f`'s four mutable statics: a notification façade whose channel id
 * is a mutable `var`, so what channel a notification lands on depends on what ran before it.
 *
 * ### Why the interface lives in `:data` and not in `:domain`
 *
 * Posting a notification is not a domain concept — it is a platform capability with a
 * `PendingIntent`-shaped hole in it. `:domain` holds no `android.*` type (`LLM.md` §2), and the port
 * every ViewModel actually names is [com.pion.phonecleaner.domain.repository.HiddenNotificationNotifier],
 * which is in `:domain` and is implemented *over* this. Nothing above `:data` sees this type.
 *
 * The implementation is `internal`, so no cluster module can declare a second `single` for it even by
 * mistake — the §5.1 defect that Koin resolves silently by load order.
 *
 * UNKNOWN — the shape of this port. `docs/system-architecture.md:753`,
 * `docs/screens/21-shared-models-and-ui.md:625` and
 * `docs/screens/17-notification-and-permissions.md:719` are the only three places it appears in the
 * corpus, and all three state exactly one thing: the Koin line
 * `single<AppNotifier> { AppNotifierImpl(androidContext(), get()) }` and the competitor members it
 * replaces. No method list, no channel table and no notification-id table is written anywhere. What is
 * below is the smallest port that serves this cluster's one caller; a second caller is expected to
 * widen it rather than to find its shape already decided.
 */
interface AppNotifier {

    /**
     * Whether the app may post at all. A separate question from a failed post: on API 33+ this is
     * `POST_NOTIFICATIONS`, and the caller's correct response is to no-op, not to report an error the
     * user cannot act on from where they are.
     */
    fun areNotificationsEnabled(): Boolean

    /**
     * Posts, creating the channel first if it does not exist.
     *
     * It returns `AppResult` and does not throw: the one caller runs on a coroutine started from a
     * system binder thread, where an escaping exception has no owner.
     */
    fun post(spec: AppNotificationSpec): AppResult<Unit>

    fun cancel(id: Int)
}

/**
 * One notification, as data.
 *
 * No `PendingIntent` field: the tap target is derived by the implementation from the package's own
 * launch intent, because building a deep link needs `:app`'s route types and `:data` cannot see them
 * (`LLM.md` §7.3 — `NavDeepLinkBuilder` lives in `:app`). A richer tap target is the change that adds
 * one, and it belongs to whoever owns that wiring.
 */
data class AppNotificationSpec(
    val id: Int,
    val channel: AppNotificationChannel,
    val title: String,
    val body: String,
    /** Removes the notification when the user taps it. */
    val autoCancel: Boolean = true,
)

/**
 * Every channel the app owns, with its id fixed at compile time.
 *
 * `hc.f` keeps the channel id in a mutable `var`, so which channel a notification lands on depends on
 * what ran before it. An enum cannot be assigned to.
 *
 * [nameRes] and [descriptionRes] are `:data`'s own string resources: a channel name is user-visible and
 * must be translated, and it is read fresh on each `ensureChannel` call rather than cached at class
 * initialisation — `ae.i2` caches resolved strings in a static and leaves them stale after an in-app
 * locale change (`docs/system-architecture.md` §4.3).
 */
enum class AppNotificationChannel(
    val id: String,
    val nameRes: Int,
    val descriptionRes: Int,
) {
    /**
     * The id-900 "we hid N notifications" summary.
     *
     * OPEN (appendix §6 item 1) — whether that summary ships at all, and under which notification
     * policy, is not settled. This constant declares the channel it would use; it decides nothing.
     */
    HiddenNotifications(
        id = "hidden-notifications",
        nameRes = com.pion.phonecleaner.data.R.string.channel_hidden_notifications_name,
        descriptionRes = com.pion.phonecleaner.data.R.string.channel_hidden_notifications_description,
    ),
}
