package com.pion.phonecleaner.feature.notification.permissionmanager.component

import androidx.annotation.StringRes
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.permission.PermissionGroupId
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.permissionmanager.PermissionSection

/**
 * The enum → `@StringRes` table. **The models hold no resource id**
 * (`docs/screens/17-notification-and-permissions.md` §0.1, §4.5).
 *
 * That split is the single biggest structural change in the cluster: `ae.j2`, `be.c` and `ae.j1` all
 * store `nameResId`/`iconResId` **inside the model**, which is what forces the competitor's walker and
 * its ViewModel to live above the platform — nothing below its UI can be unit-tested off-device, and no
 * model can be reused by a widget or a notification.
 *
 * PLACEMENT — §4.3 puts this table in `:core:ui`, beside `FeatureDescriptor`. It is here because
 * the `core` modules are not this cluster's to write. Its home is `:core:ui/catalog/` the moment somebody owns
 * that file; nothing about the shape changes when it moves, because the enums are already resource-free
 * and every caller resolves through `stringResource`.
 *
 * No `iconRes()`. §4.3 names one for `PermissionGroupId`; this repository contains no drawable for any
 * of the eleven groups, and a fabricated drawable id would not compile. The group headers render
 * their label and their app count, which is the information the row carries.
 */
@StringRes
internal fun AppPermission.labelRes(): Int = when (this) {
    AppPermission.Storage -> R.string.app_permission_storage
    AppPermission.AllFiles -> R.string.app_permission_all_files
    AppPermission.Media -> R.string.app_permission_media
    AppPermission.Notifications -> R.string.app_permission_notifications
    AppPermission.NotificationListener -> R.string.app_permission_notification_listener
    AppPermission.UsageStats -> R.string.app_permission_usage_stats
    AppPermission.Overlay -> R.string.app_permission_overlay
    AppPermission.WriteSettings -> R.string.app_permission_write_settings
    AppPermission.DoNotDisturb -> R.string.app_permission_do_not_disturb
    AppPermission.IgnoreBatteryOptimizations -> R.string.app_permission_ignore_battery_optimizations
    AppPermission.WhatsAppFolder -> R.string.app_permission_whatsapp_folder
}

/** The eleven groups, in `SensitivePermissionCatalog`'s fixed order — that of `ae.a1.C()`. */
@StringRes
internal fun PermissionGroupId.labelRes(): Int = when (this) {
    PermissionGroupId.ACTIVITY_RECOGNITION -> R.string.permission_group_activity_recognition
    PermissionGroupId.BODY_SENSORS -> R.string.permission_group_body_sensors
    PermissionGroupId.CALENDAR -> R.string.permission_group_calendar
    PermissionGroupId.CAMERA -> R.string.permission_group_camera
    PermissionGroupId.CONTACTS -> R.string.permission_group_contacts
    PermissionGroupId.LOCATION -> R.string.permission_group_location
    PermissionGroupId.PHONE -> R.string.permission_group_phone
    PermissionGroupId.QUERY_PACKAGES -> R.string.permission_group_query_packages
    PermissionGroupId.BOOT -> R.string.permission_group_boot
    PermissionGroupId.MICROPHONE -> R.string.permission_group_microphone
    PermissionGroupId.SMS -> R.string.permission_group_sms
}

@StringRes
internal fun PermissionSection.labelRes(): Int = when (this) {
    PermissionSection.Sensitive -> R.string.permission_manager_section_sensitive
    PermissionSection.Normal -> R.string.permission_manager_section_normal
    PermissionSection.Other -> R.string.permission_manager_section_other
}
