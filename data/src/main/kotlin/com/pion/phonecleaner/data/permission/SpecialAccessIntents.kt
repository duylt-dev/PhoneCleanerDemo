package com.pion.phonecleaner.data.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.pion.phonecleaner.domain.model.permission.AppPermission

/**
 * Which system screen grants each special access. The file `HomeRoute`'s KDoc and
 * `PermissionSystemIntents`' KDoc both already name as its home
 * (`docs/system-architecture.md` §4.4).
 *
 * Launching a system settings screen is **not a repository call** — it is an Effect the host
 * performs. `PermissionRepository` answers *whether* a grant is held; this answers *where the user
 * goes to give it*, and the two are kept side by side deliberately: every branch below pairs with a
 * check on the same `AppPermission` in `AndroidPermissionRepository.isGranted`, so a permission that
 * can be checked can also be reached.
 *
 * `FLAG_ACTIVITY_NEW_TASK` is deliberately **not** set. These are launched through an
 * `ActivityResultLauncher` from the hosting Activity, so the system screen sits on our task and
 * returning lands back on the screen that asked. A new task is what gives the competitor's three
 * background-start overlays a back stack the app cannot see
 * (`AndroidManifest.xml:246`, pending owner decision 4).
 *
 * [appDetails] is the fallback rather than a throw: the app-details page can always be reached, and
 * a permission with no dedicated screen must still lead somewhere the user can act.
 */
object SpecialAccessIntents {

    fun forPermission(context: Context, permission: AppPermission): Intent = when (permission) {
        AppPermission.NotificationListener ->
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        AppPermission.Notifications ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        AppPermission.UsageStats ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

        AppPermission.Overlay ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri(context))

        AppPermission.WriteSettings ->
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, appUri(context))

        AppPermission.DoNotDisturb ->
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

        AppPermission.IgnoreBatteryOptimizations ->
            // The *list* screen, not ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. That one needs the
            // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission, which Google Play restricts to apps
            // whose core function is broken by Doze; this app's is not, so it is never declared.
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        AppPermission.AllFiles ->
            // UNKNOWN whether MANAGE_EXTERNAL_STORAGE is ever grantable for this app: it is never
            // assumed to be, and the default branch is MediaStore + SAF (owner decision, 2026-08-26).
            // The screen is still reachable so a device that does allow it is not locked out.
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, appUri(context))

        // Storage, Media and WhatsAppFolder are runtime permissions or a SAF tree, not a settings
        // screen. They reach here only after a permanent refusal, where app details is the one place
        // the grant can still be given.
        AppPermission.Storage,
        AppPermission.Media,
        AppPermission.WhatsAppFolder,
        -> appDetails(context)
    }

    /** The one screen that can always be reached, whatever the permission. */
    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(context))

    private fun appUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}
