package com.pion.phonecleaner.domain.model.notification

import kotlin.time.Instant

/**
 * One notification the interceptor took out of the shade and put in the store
 * (`docs/screens/17-notification-and-permissions.md` §0.1).
 *
 * The name is adjudicated: `HiddenNotification`, not `InterceptedNotification`, and this cluster owns
 * the type (`docs/system-architecture.md` §4.1).
 *
 * **[key] is non-null, and it is the identity.** `od.i.C(key)` deletes by a *nullable* key, so one
 * null-key entry deletes every other null-key entry (§3.5). Where the platform hands the service no
 * key, the service mints `"$packageName#$postedAtMillis"` — deterministic, so the same notification
 * re-posted updates its row instead of adding a second one.
 *
 * **No icon field, and no `Drawable`.** [packageName] is the icon's identity and `AppIconLoader`
 * resolves it at draw time (`LLM.md` §8). The competitor's model holds a live `Drawable` that Gson is
 * asked to serialise (§5.2).
 *
 * [postedAt] stays an `Instant`; the row formats it at render, so a locale change or a midnight
 * rollover re-renders correctly and there is no shared mutable `SimpleDateFormat` (§3.1).
 *
 * `@Immutable` is not written here: `:domain` is compiled without the Compose plugin (`LLM.md` §2),
 * and `compose-stability.conf` covers `com.pion.phonecleaner.domain.model.*` instead.
 */
data class HiddenNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val body: String,
    val postedAt: Instant,
)
