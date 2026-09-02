package com.pion.phonecleaner.feature.settings.permissioncentre

import androidx.annotation.StringRes
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.feature.settings.R

/**
 * `AppPermission -> @StringRes`. The enum itself holds no resource id
 * (`docs/system-architecture.md` §4.4, `docs/screens/17-notification-and-permissions.md:682`).
 *
 * `docs/system-architecture.md` §4.4 assigns `AppPermission.labelRes()` to `:core:ui`. It is not
 * there today, and `:core:ui` is not this cluster's module; this file is the four rows **this
 * screen** renders, and it moves behind that extension the moment it exists — a rename, not a
 * redesign. Only the four in [PermissionCentreCatalog.managed] have copy, so a permission that is
 * not managed cannot accidentally acquire a user-facing sentence.
 *
 * ### The wording rule, applied
 *
 * Every body below states **what the app does with the grant**. None says the device is at risk
 * without it, none claims an outcome, and none carries a figure (`LLM.md` §1, §5). The competitor
 * builds this copy with `Html.fromHtml` around a hard-coded `<font color="#0090F6">` span
 * (`od.p0.r`) — a hex colour baked into copy, which defeats theming and dark mode (§5.4 delta 5);
 * emphasis here is the theme's, applied at render.
 */
internal object PermissionCopy {

    @StringRes
    fun titleRes(permission: AppPermission): Int = when (permission) {
        AppPermission.Storage -> R.string.settings_permission_storage_title
        AppPermission.Media -> R.string.settings_permission_media_title
        AppPermission.Notifications -> R.string.settings_permission_notifications_title
        AppPermission.NotificationListener -> R.string.settings_permission_listener_title
        else -> R.string.settings_permissions_title
    }

    @StringRes
    fun bodyRes(permission: AppPermission): Int = when (permission) {
        AppPermission.Storage -> R.string.settings_permission_storage_body
        AppPermission.Media -> R.string.settings_permission_media_body
        AppPermission.Notifications -> R.string.settings_permission_notifications_body
        AppPermission.NotificationListener -> R.string.settings_permission_listener_body
        else -> R.string.settings_permissions_title
    }

    /**
     * The numbered steps a user follows **before** they leave, and only for a grant taken in a
     * system screen. `null` for a runtime dialog, which needs none.
     *
     * This is the replacement for the two translucent activities the competitor draws over the
     * system Settings app 1 500 ms after handing the user out — a background activity start over
     * another app, restricted since Android 10 and largely blocked on 14, and deleted here by
     * settled decision (§5.4 delta 4, `LLM.md` §7.5).
     */
    @StringRes
    fun stepsRes(permission: AppPermission): Int? = when (permission) {
        AppPermission.NotificationListener -> R.string.settings_permission_steps_listener
        else -> null
    }
}
