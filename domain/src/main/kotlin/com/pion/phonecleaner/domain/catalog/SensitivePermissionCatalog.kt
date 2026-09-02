package com.pion.phonecleaner.domain.catalog

import com.pion.phonecleaner.domain.model.permission.PermissionGroupId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The eleven sensitive permission groups, and the AOSP permission names in each.
 *
 * A pure `object`, and **not in Koin**: it holds no state and touches no platform, so injecting it
 * buys nothing (`LLM.md` §6.3, `docs/system-architecture.md` §5.3).
 *
 * It carries **no resource id**. The competitor's `ae.a1` stores `(nameResId, iconResId, String[])`
 * triples, which is what forces its walker and its ViewModel to live above the platform; `:core:ui`
 * owns `PermissionGroupId.labelRes()/iconRes()` instead
 * (`docs/system-architecture.md` §4.4, `docs/screens/17-notification-and-permissions.md:682`).
 *
 * Group membership is verbatim from `ae.a1.C()`
 * (`docs/reverse-engineering/17-notification-and-permissions.md:616-630`). The names are written
 * fully qualified because the membership test runs against `PackageInfo.requestedPermissions`, whose
 * entries are fully qualified; the source table abbreviates them.
 */
object SensitivePermissionCatalog {

    private val groups: Map<PermissionGroupId, ImmutableList<String>> = mapOf(
        PermissionGroupId.ACTIVITY_RECOGNITION to persistentListOf(
            "android.permission.ACTIVITY_RECOGNITION",
        ),
        PermissionGroupId.BODY_SENSORS to persistentListOf(
            "android.permission.BODY_SENSORS",
        ),
        PermissionGroupId.CALENDAR to persistentListOf(
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
        ),
        PermissionGroupId.CAMERA to persistentListOf(
            "android.permission.CAMERA",
        ),
        PermissionGroupId.CONTACTS to persistentListOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
        ),
        PermissionGroupId.LOCATION to persistentListOf(
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        ),
        PermissionGroupId.PHONE to persistentListOf(
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.ANSWER_PHONE_CALLS",
        ),
        PermissionGroupId.QUERY_PACKAGES to persistentListOf(
            "android.permission.QUERY_ALL_PACKAGES",
        ),
        PermissionGroupId.BOOT to persistentListOf(
            "android.permission.RECEIVE_BOOT_COMPLETED",
        ),
        PermissionGroupId.MICROPHONE to persistentListOf(
            "android.permission.RECORD_AUDIO",
        ),
        PermissionGroupId.SMS to persistentListOf(
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
        ),
    )

    /** Every group, in the fixed order `ae.a1.C()` returns them. */
    val groupIds: ImmutableList<PermissionGroupId> = PermissionGroupId.entries.toImmutableList()

    /** The permission names in one group. Empty for no group — every id has entries. */
    fun permissionsIn(group: PermissionGroupId): ImmutableList<String> =
        groups[group] ?: persistentListOf()

    /**
     * Every sensitive name, flattened. This is the membership test for "sensitive": the competitor
     * flattens the same eleven arrays into one 20-name list in `vd.d`'s static initialiser
     * (`java/vd/d.java:40-45`).
     */
    val allSensitive: ImmutableSet<String> = groups.values.flatten().toImmutableSet()

    /** The group a permission name belongs to, or null when it is not sensitive. */
    fun groupOf(permissionName: String): PermissionGroupId? =
        groups.entries.firstOrNull { permissionName in it.value }?.key
}
