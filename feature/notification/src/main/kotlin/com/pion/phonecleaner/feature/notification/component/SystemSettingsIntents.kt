package com.pion.phonecleaner.feature.notification.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.pion.phonecleaner.domain.model.permission.AppPermission

/**
 * The system settings screens this cluster hands the user to, and nothing else.
 *
 * PLACEMENT — `docs/screens/17-notification-and-permissions.md` §4.4 puts this table in
 * `:data/permission/SpecialAccessIntents.kt`, "a plain file, not a binding". It is here instead
 * because `:feature:notification` has no `:data` edge (`LLM.md` §2) and a Route cannot import one; the
 * table therefore lives beside its only caller. `data/permission/` is another cluster's package and
 * this cluster does not write into it.
 *
 * **Every launch is guarded.** `startActivity` for a `Settings.ACTION_*` screen throws
 * `ActivityNotFoundException` on devices where the OEM removed it, and an uncaught throw here kills the
 * process from a lambda inside `CollectEffects`. Each function reports whether the screen opened, and
 * the caller says so rather than appearing to do nothing.
 *
 * Row 5 of the competitor's Special Access tab is labelled *"Optimize battery usage"* and opens
 * `BATTERY_SAVER_SETTINGS` — the **global** battery-saver screen, not any app's exemption (§4.5). Ours
 * opens `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, which is the screen the label names.
 */
internal fun openNotificationListenerSettings(context: Context): Boolean =
    start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

/** The system settings page for one installed app — `ManageTapped` on the Apps tab. */
internal fun openAppSettings(context: Context, packageName: String): Boolean =
    start(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )

/**
 * The Special Access tab's six rows plus the runtime-permission constants the enum also carries.
 *
 * `AllFiles` is present as a row destination only. **It is never assumed grantable**
 * (`docs/system-architecture.md` §8.1): opening the screen is not the same as declaring the app takes
 * that branch, and nothing here requests the permission.
 *
 * Returns `null` where the platform has no screen to open for a constant. The caller renders that as a
 * message; a silently-ignored tap is what the competitor's dead `vd.d` six-name list produces.
 */
internal fun specialAccessIntent(access: AppPermission, packageName: String): Intent? = when (access) {
    AppPermission.NotificationListener -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    AppPermission.UsageStats -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    AppPermission.Overlay ->
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", packageName, null))

    AppPermission.WriteSettings ->
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", packageName, null))

    AppPermission.DoNotDisturb -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    AppPermission.IgnoreBatteryOptimizations ->
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    AppPermission.Notifications -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

    AppPermission.AllFiles -> Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    // Runtime permissions, not special access: they are granted in this app's own settings page, and
    // the Special Access tab does not list them.
    AppPermission.Storage, AppPermission.Media, AppPermission.WhatsAppFolder -> null
}

internal fun openSpecialAccessSettings(
    context: Context,
    access: AppPermission,
    packageName: String,
): Boolean {
    val intent = specialAccessIntent(access, packageName) ?: return false
    return start(context, intent)
}

/**
 * `FLAG_ACTIVITY_NEW_TASK` because the caller may be an application context, and a guarded start
 * because an OEM that removed the screen must not be a crash.
 */
private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (missing: ActivityNotFoundException) {
    false
} catch (denied: SecurityException) {
    false
}

/**
 * Opens another app by its launcher entry — the hidden list's row tap
 * (`docs/screens/17-notification-and-permissions.md` §3.3).
 *
 * Returns false when the package has no launcher activity. The competitor's row tap "restores" the
 * notification by launching the app and dropping the row **whether or not anything opened**; ours
 * reports the failure, and the row's `dismiss` runs on its own path (§3.5).
 */
internal fun launchApp(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    return start(context, intent)
}
