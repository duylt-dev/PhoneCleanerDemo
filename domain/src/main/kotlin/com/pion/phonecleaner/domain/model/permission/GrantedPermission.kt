package com.pion.phonecleaner.domain.model.permission

/**
 * One permission a **third-party** app currently holds. Ports the competitor's `be.k` / `Supstur`.
 *
 * `docs/system-architecture.md` §4.4 picked this name over `AndroidPermission(id, …)` precisely
 * because the type describes what someone else was granted — not what we are asking for. That is
 * [AppPermission], and the two must never be confused.
 *
 * [label] and [description] are nullable because they are resolved from the *declaring* app's
 * resources: `getString(info.labelRes)` with `labelRes == 0` throws, and the competitor's
 * per-permission `catch` then drops the permission silently, so its counts drift with no signal
 * (`docs/reverse-engineering/17-notification-and-permissions.md:648`). Null here means "the platform
 * gave us no label", which the row can render honestly.
 */
data class GrantedPermission(
    /** The AOSP permission name, e.g. `android.permission.CAMERA`. The identity. */
    val name: String,
    val label: String?,
    val description: String?,
)
