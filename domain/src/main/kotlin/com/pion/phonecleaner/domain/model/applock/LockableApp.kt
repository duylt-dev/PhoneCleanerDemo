package com.pion.phonecleaner.domain.model.applock

/**
 * One launcher app the user may put behind the App Lock PIN
 * (`docs/screens/16-app-lock.md` §0).
 *
 * **No `Drawable`.** [packageName] *is* the icon's identity and `AppIconLoader` (`coreUiModule`)
 * resolves it at draw time. The competitor's `be.g` carries the icon on the model, which is the
 * direct cause of two identical rows comparing unequal once the image loader hands back a different
 * instance (`LLM.md` §8).
 *
 * **Identity is [packageName], not [label].** `be.g` implements `equals`/`hashCode` on the label, so
 * two apps that share a display name collide in any `Set` or `distinct`
 * (`docs/screens/16-app-lock.md` §1.5).
 *
 * ### [isLocked] is a field on the model, and that is deliberate
 *
 * The app-wide rule is that selection lives on `State` as a `Set<Id>` and never as a flag on a row
 * (`LLM.md` §8). This is the documented exception, recorded in `LLM.md` §12: [isLocked] is
 * **persisted domain truth arriving on a repository `Flow`**, not a tap-driven selection. Holding it
 * on `State` would need a write-back path to the repository that a selection never needs. The
 * *pending* toggle does live on `State` — `AppLockState.togglingPackages` and
 * `AppLockState.pendingUnlockPackage`.
 *
 * `@Immutable` is not written here: `:domain` is compiled without the Compose plugin (`LLM.md` §2),
 * and `compose-stability.conf`'s `com.pion.phonecleaner.domain.model.*` line is what declares it
 * stable to every Compose module (`LLM.md` §8).
 */
data class LockableApp(
    /** The identity, the `LazyColumn` key, and the argument of every lock/unlock write. */
    val packageName: String,
    /** Display label, resolved by the enumeration. Never used for equality. */
    val label: String,
    /** Persisted truth: the package is in the lock list right now. */
    val isLocked: Boolean,
)
