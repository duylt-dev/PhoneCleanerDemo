package com.pion.phonecleaner.feature.settings.permissioncentre

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.pion.phonecleaner.domain.model.permission.AppPermission

/**
 * The platform half of the funnel: which manifest permissions a card asks for, and which system
 * screen it opens.
 *
 * ### Why it is in the Route layer and not behind a repository
 *
 * `PermissionRepository`'s KDoc states it: launching a system settings screen is **not a repository
 * at all** — it is an Effect the Route performs (`docs/system-architecture.md` §4.4). The appendix
 * names `:data/permission/SpecialAccessIntents.kt` as the file, and `:feature` may not depend on
 * `:data` (`LLM.md` §2). That file does not exist yet; when it does, this one collapses into a call
 * to it and nothing else changes. Written here, `NEVER` behind a permission library: XXPermissions is
 * out of scope and the replacement is deliberately smaller — `ActivityResultContracts` plus an
 * explicit `Settings.ACTION_*` intent.
 *
 * @see PermissionCentreCatalog for which permissions this screen manages and why the rest are absent.
 */
internal object PermissionSystemIntents {

    /**
     * The manifest permissions a runtime request must ask for, in this order.
     *
     * Empty means "there is nothing to ask on this API level", and the caller must treat that as an
     * immediate refusal rather than as a grant: below API 33 there is no `POST_NOTIFICATIONS`, and
     * from API 33 `READ_EXTERNAL_STORAGE` is not requestable at all. Inventing a `true` there is how
     * a screen ends up claiming a permission it does not hold.
     *
     * UNKNOWN — `POST_NOTIFICATIONS` is **not declared** in `:data`'s `AndroidManifest.xml`, which
     * states plainly that it sits behind a pending owner decision. Requesting an undeclared runtime
     * permission returns denied immediately and silently. The card is still offered because the same
     * request already exists on the home screen; the manifest line, when the decision lands, is the
     * only change.
     */
    fun runtimePermissions(permission: AppPermission): List<String> = when (permission) {
        AppPermission.Storage ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                emptyList()
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

        AppPermission.Media ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

        AppPermission.Notifications ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }

        else -> emptyList()
    }

    /**
     * The system screen a special access is granted in, plus the app-details page as the fallback for
     * a runtime permission the user has permanently refused.
     *
     * `FLAG_ACTIVITY_NEW_TASK` is deliberately **not** set: this is launched through an
     * `ActivityResultLauncher` from the hosting Activity, and a new task is what gives the
     * competitor's screens back stacks the app cannot see.
     */
    fun settingsIntent(context: Context, permission: AppPermission): Intent =
        when (permission) {
            AppPermission.NotificationListener ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

            AppPermission.Notifications ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

            else -> appDetails(context)
        }

    /** The one screen that can always be reached, whatever the permission. */
    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
}
