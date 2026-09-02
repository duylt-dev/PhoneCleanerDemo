package com.pion.phonecleaner.feature.home

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.model.feature.FeatureId

/**
 * The `SavedStateHandle` keys the route arguments arrive under, and the one reader of them.
 *
 * Route arguments **are** the initial state (MVI §3 rule 5): they are read once, in the ViewModel's
 * constructor, and never re-read from an Intent on recreation. The competitor reads its `Intent`
 * again on every `onResume` and maps it as step 4 of 14 inside `z()`, which is why a feature screen
 * can start on top of a half-built home (delta 9).
 *
 * UNKNOWN — `:app/navigation/Routes.kt` is not this cluster's file, and today it declares `Home` as
 * an argument-free `data object`. With Navigation-Compose type-safe destinations the key is the
 * route class's **property name**, so these two constants must match the property names reported in
 * `routesNeeded`. Looked for a stated argument name in `docs/screens/11-home.md` §1.2 — which writes
 * `Routes.ARG_FROM_NOTIFICATION` / `Routes.ARG_FEATURE` without defining either — and in `LLM.md`
 * §7.2/§7.3, which fix the *shape* (a `LaunchSource` plus an optional `FeatureId` for `Home`) and
 * not the spelling. Until the route lands, both reads return the default, which is the same state a
 * launcher tap produces.
 */
object HomeArgs {
    const val FROM_NOTIFICATION = "fromNotification"
    const val FEATURE = "feature"
}

/** True only when this launch came from a notification, which suppresses the opt-in sheet's door 1. */
internal fun SavedStateHandle.arrivedFromNotification(): Boolean =
    get<Boolean>(HomeArgs.FROM_NOTIFICATION) == true

/**
 * Reads the deep-link argument without asserting how Navigation stored it: a type-safe route may
 * hand back the enum itself or its `name`, and `SavedStateHandle.get<T>` is an unchecked cast that
 * would fail at the use site rather than here.
 *
 * `FeatureId.fromLegacyIndex` is deliberately not consulted — it exists only for reading legacy data
 * (an old preference, a push payload) and is **never** used for navigation (`LLM.md` §7.2).
 */
internal fun SavedStateHandle.pendingFeature(): FeatureId? =
    when (val raw = get<Any>(HomeArgs.FEATURE)) {
        is FeatureId -> raw
        is String -> FeatureId.entries.firstOrNull { it.name == raw }
        else -> null
    }
