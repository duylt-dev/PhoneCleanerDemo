package com.pion.phonecleaner.domain.catalog

import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * What a feature needs before it can run. A pure `object`, and **not in Koin** — a dependency-free
 * lookup that holds no state and touches no platform gains nothing from injection, and two proposals
 * to declare it (`single<FeatureCatalogRepository>`, `single<FeatureCatalog> { StaticFeatureCatalog() }`)
 * were both rejected (`docs/system-architecture.md` §4.1, §5.3).
 *
 * **It holds `FeatureId` plus `requires` only.** The `@StringRes`/`@DrawableRes` ids live on
 * `FeatureDescriptor` in `:core:ui/catalog`, which is populated from here
 * (`docs/system-architecture.md` §4.3). That split is what keeps `:domain` Android-free while still
 * fixing the competitor's worst localisation defect: `ae.i2` resolves feature names to `String`s from
 * `Resources` at object initialisation (`java/ae/i2.java:315-415`) in an app with a 17-locale in-app
 * picker, so switching language leaves every feature name stale until the process restarts.
 *
 * [requires] is what the permission gate reads, **in the reducer**, never in a router: `md.g1.g0` is
 * 14 gate lambdas inside the competitor's router, so a destination and its precondition are edited in
 * different files (`docs/system-architecture.md` §4.8).
 */
object FeatureCatalog {

    /**
     * The permissions [feature] cannot run without. An empty set means "nothing is required, or
     * nothing is settled yet" — see the UNKNOWN note on [requirements].
     */
    fun requires(feature: FeatureId): ImmutableSet<AppPermission> =
        requirements[feature] ?: persistentSetOf()

    /**
     * UNKNOWN — the full twenty-row table is not settled by any source, and the three rows below are
     * every row the corpus actually states. Looked for, and not found: a requirements table in
     * `docs/system-architecture.md` §4.3 (which defines `requires` but lists no values), in
     * `docs/screens/21-shared-models-and-ui.md` §5, and in each cluster appendix — every appendix
     * gates its own screen with a direct `permissions.isGranted(...)` call instead of a catalogue
     * lookup, so no cluster's row can be read off one.
     *
     * Two structural reasons the rest must stay blank rather than be guessed:
     *
     *  1. **The storage half is not a constant.** `docs/system-architecture.md` §8.4 puts it behind
     *     `FileAccessProfile`, a `:domain` policy answering `requires(feature)`, from which
     *     `FeatureDescriptor.requires` is populated — precisely so the permission a feature asks for
     *     changes with the storage branch in one place. `MANAGE_EXTERNAL_STORAGE` is never assumed
     *     grantable (§8.1), so writing `AppPermission.AllFiles` against any feature here would encode
     *     the branch this design exists to keep out of the reducer. `FileAccessProfile` itself is
     *     flagged in §8.4 as that chapter's own name, asserted by no report, and is not created here.
     *  2. **`RunningApps` is a PENDING OWNER DECISION (3)** — whether the user must grant
     *     `PACKAGE_USAGE_STATS` by hand. Writing `UsageStats` against it would close that decision.
     *
     * A wrong row here is worse than a missing one: a fabricated requirement blocks a working screen
     * behind a permission it does not need, and a missing one fails visibly with
     * `AppError.PermissionDenied` from the repository instead of silently claiming to be checked.
     */
    private val requirements: Map<FeatureId, ImmutableSet<AppPermission>> = mapOf(
        // The notification interceptor cannot run at all without listener access; the gate resolves
        // "no listener access -> ListenerAccessMissing" first
        // (docs/screens/17-notification-and-permissions.md:216).
        FeatureId.NotificationCleaner to persistentSetOf(AppPermission.NotificationListener),
        // ScreenStarted returns Phase.NeedsUsageAccess when UsageStats is not granted
        // (docs/screens/19-network-and-speed-test.md:240).
        FeatureId.NetworkTraffic to persistentSetOf(AppPermission.UsageStats),
        // The App Lock settings state is built from exactly these two grants
        // (docs/screens/16-app-lock.md:203-204).
        FeatureId.AppLock to persistentSetOf(AppPermission.Overlay, AppPermission.UsageStats),
    )
}
