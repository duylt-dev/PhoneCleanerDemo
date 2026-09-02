package com.pion.phonecleaner.domain.model.permission

/**
 * Every permission or special access **this app** may hold. One enum; it merged three clusters'
 * partial sets and folded the competitor's six static special-access rows (`ae.h1` / `ae.j1`) into
 * constants (`docs/system-architecture.md` §4.4).
 *
 * The six special-access grants are not a separate type: `WriteSettings`, `Overlay`, `DoNotDisturb`,
 * `NotificationListener`, `IgnoreBatteryOptimizations`, `UsageStats`
 * (`docs/screens/17-notification-and-permissions.md:91`).
 *
 * `AllFiles` is `MANAGE_EXTERNAL_STORAGE` and is **never assumed grantable**: the default branch is
 * `MediaStore` + SAF (`docs/system-architecture.md` §8.1). It is a constant here so the all-files
 * build can express it, not a precondition any default-branch feature may declare.
 *
 * `:core:ui` owns `AppPermission.labelRes()`; the enum itself holds no resource id
 * (`docs/screens/17-notification-and-permissions.md:682`).
 */
enum class AppPermission {
    Storage,
    AllFiles,
    Media,
    Notifications,
    NotificationListener,
    UsageStats,
    Overlay,
    WriteSettings,
    DoNotDisturb,
    IgnoreBatteryOptimizations,
    WhatsAppFolder,
}
