package com.pion.phonecleaner.domain.model.permission

import kotlinx.collections.immutable.ImmutableList

/**
 * One sensitive permission group and the apps that hold something in it.
 *
 * Chosen over `PermissionCategory` / `PermissionLevelGroup` (`docs/system-architecture.md` §4.4).
 * **The model holds an enum id, never a resource id** — `ae.j2` and `be.c` both store
 * `nameResId`/`iconResId` inside the model, which is what forces the competitor's walker and its
 * ViewModel to live above the platform. `:core:ui` owns `PermissionGroupId.labelRes()/iconRes()`
 * (`docs/screens/17-notification-and-permissions.md:66-93`).
 */
data class PermissionGroup(
    val id: PermissionGroupId,
    /** The AOSP permission names in this group. See `SensitivePermissionCatalog`. */
    val permissions: ImmutableList<String>,
    /** Apps holding at least one of [permissions]. */
    val apps: ImmutableList<AppPermissionReport>,
)

/**
 * The eleven groups of `ae.a1`, in the order `a1.C()` returns them
 * (`docs/reverse-engineering/17-notification-and-permissions.md:616-630`).
 */
enum class PermissionGroupId {
    ACTIVITY_RECOGNITION,
    BODY_SENSORS,
    CALENDAR,
    CAMERA,
    CONTACTS,
    LOCATION,
    PHONE,
    QUERY_PACKAGES,
    BOOT,
    MICROPHONE,
    SMS,
}
