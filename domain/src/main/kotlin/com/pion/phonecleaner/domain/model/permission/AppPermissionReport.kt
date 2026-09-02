package com.pion.phonecleaner.domain.model.permission

import kotlinx.collections.immutable.ImmutableList

/**
 * What one installed app was granted. Ports `Selerover`, whose fields are all public `var`, whose
 * icon is a `Drawable` that `writeToParcel` silently drops, and whose `CREATOR` reads only the name.
 *
 * Named `AppPermissionReport`, not `AppPermissions`: one letter from the enum [AppPermission] is a
 * real readability defect on a 35-screen app (`docs/system-architecture.md` §4.1).
 *
 * **No icon field.** [packageName] *is* the icon's identity; `AppIconLoader` in `:core:ui` resolves
 * it at render (`LLM.md` §8).
 */
data class AppPermissionReport(
    val packageName: String,
    val label: String,
    /** Granted permissions that appear in [com.pion.phonecleaner.domain.catalog.SensitivePermissionCatalog]. */
    val sensitive: ImmutableList<GrantedPermission>,
    /** Granted permissions whose protection level is normal. */
    val normal: ImmutableList<GrantedPermission>,
    /**
     * Everything granted that is in neither bucket. The competitor **discards** these, so its two
     * counts do not add up to what the app actually holds
     * (`docs/reverse-engineering/17-notification-and-permissions.md:655`).
     */
    val other: ImmutableList<GrantedPermission>,
) {
    val sensitiveCount: Int get() = sensitive.size
}
