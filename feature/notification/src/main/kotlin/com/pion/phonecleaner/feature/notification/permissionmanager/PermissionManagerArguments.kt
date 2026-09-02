package com.pion.phonecleaner.feature.notification.permissionmanager

import androidx.lifecycle.SavedStateHandle

/**
 * The `tab` route argument, read once at construction
 * (`docs/system-architecture.md` §6.1: `PermissionManager(tab: PermissionTab = Apps)`).
 *
 * The argument exists so the home screen's permission-centre entry can land on a specific tab — the
 * competitor reaches the same place through `goTag`, an `Int` checked by nothing whose two `when`
 * blocks have already drifted apart (`LLM.md` §7.2).
 *
 * **It reads defensively.** Navigation-Compose's type-safe API decides how an enum argument is stored
 * — the instance itself, its `name`, or its ordinal — and the route type is declared in `:app`, which
 * is not this cluster's to write and is not yet wired (`Routes.kt` still declares only `Home`). All
 * three shapes resolve here, and anything else falls back to [PermissionTab.Apps]: a bad argument lands
 * the user on the first tab, never on a crash.
 */
internal fun SavedStateHandle.permissionTab(): PermissionTab = when (val raw = get<Any>(TAB_KEY)) {
    is PermissionTab -> raw
    is String -> PermissionTab.entries.firstOrNull { it.name == raw } ?: PermissionTab.Apps
    is Int -> PermissionTab.entries.getOrNull(raw) ?: PermissionTab.Apps
    else -> PermissionTab.Apps
}

/**
 * The `SavedStateHandle` key. Type-safe navigation keys an argument by its **property name**, so this
 * string and the property in `:app`'s `PermissionManager` route must be the same word.
 */
internal const val TAB_KEY: String = "tab"
