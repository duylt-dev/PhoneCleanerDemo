package com.pion.phonecleaner.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult

/**
 * The only implementation of [AppNotifier], and the only place in the app that calls `notify`.
 *
 * `internal`, deliberately: a cluster module cannot name this class, so it cannot declare a second
 * `single<AppNotifier>` — the silent Koin override that is a runtime coin flip on module load order
 * (`docs/system-architecture.md` §5.1). The one binding is in `coreDataModule`.
 *
 * `ensureChannel` runs on every post rather than once at construction. Creating a channel that already
 * exists is a documented no-op, and doing it here means the channel exists even when the process was
 * started by the system binder for the notification listener — a path that runs no `Application`
 * initialisation of the app's choosing. `hc.f` instead keeps its channel id in a **mutable static**,
 * so which channel a notification lands on depends on what ran before it.
 *
 * UNKNOWN — the small icon. No drawable is named for a notification anywhere in `docs/screens/` or
 * `docs/reverse-engineering/`, and `:data` owns no drawable of its own. `applicationInfo.icon` is the
 * one icon this module can resolve without inventing a resource name; it is a real, existing id rather
 * than a fabricated `R.drawable.*` that would not compile, and swapping it is a one-line change once a
 * notification icon exists.
 */
internal class AppNotifierImpl(
    private val context: Context,
    private val log: AppLogger,
) : AppNotifier {

    private val manager = NotificationManagerCompat.from(context)

    override fun areNotificationsEnabled(): Boolean = manager.areNotificationsEnabled()

    override fun post(spec: AppNotificationSpec): AppResult<Unit> {
        if (!areNotificationsEnabled()) {
            // Not an error the user can act on from where they are: the caller no-ops.
            return AppResult.Failure(AppError.PermissionDenied(POST_NOTIFICATIONS))
        }
        return try {
            ensureChannel(spec.channel)
            manager.notify(spec.id, build(spec))
            AppResult.Success(Unit)
        } catch (denied: SecurityException) {
            // `notify` throws this when the runtime grant was revoked between the check and the post.
            log.e(denied) { "Notification ${spec.id} refused" }
            AppResult.Failure(AppError.PermissionDenied(POST_NOTIFICATIONS))
        } catch (failure: IllegalStateException) {
            log.e(failure) { "Notification ${spec.id} could not be posted" }
            AppResult.Failure(AppError.Unexpected(failure.message))
        }
    }

    override fun cancel(id: Int) = manager.cancel(id)

    private fun build(spec: AppNotificationSpec) =
        NotificationCompat.Builder(context, spec.channel.id)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
            .setAutoCancel(spec.autoCancel)
            .setContentIntent(launchIntent())
            .build()

    /**
     * The tap target: this app's own launcher entry.
     *
     * Not a deep link — `NavDeepLinkBuilder` needs `:app`'s route types and `:data` may not see them
     * (`LLM.md` §7.3). Not an exported trampoline either: `mf.EnsileActivity` is `exported="true"` with
     * no `intent-filter`, no permission and no caller check, and copies the caller's extras verbatim
     * into a launch, so any installed app can drive it anywhere.
     */
    private fun launchIntent() =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    /** Creating a channel that already exists is a no-op, which is why this is safe to repeat. */
    private fun ensureChannel(channel: AppNotificationChannel) {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(
                channel.id,
                context.getString(channel.nameRes),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(channel.descriptionRes) },
        )
    }

    private companion object {
        /** Written out rather than imported: `Manifest.permission` is not declared below API 33. */
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    }
}
