package com.pion.phonecleaner.domain.model.applock

import kotlinx.serialization.Serializable

/**
 * What the one PIN screen is being opened for (`docs/screens/16-app-lock.md` §2.1).
 *
 * The competitor already carried this as an `int` extra with the same three values —
 * `1 = set`, `2 = change`, `3 = verify` (`docs/reverse-engineering/16-app-lock.md` §3.2) — so the
 * three constants are observed, not invented. What is **not** settled anywhere is the route
 * argument's *name*:
 *
 * > **UNKNOWN — the `Pin` route argument's name.** Looked for in `docs/screens/16-app-lock.md` §2.1
 * > (which proposes `mode: PinMode` and then says *"fix the name before the cluster is built, do not
 * > invent it twice"*), in `LLM.md` §7.2 (*"no report writes the argument name or the enum
 * > constants"*) and in `docs/system-architecture.md` §6.1/§10.3 U4. The proposal is followed here
 * > because a second invention is worse than a first; the route type itself belongs to
 * > `:app/navigation/Routes.kt` and is reported, not written, by this cluster.
 *
 * `@Serializable` because it crosses a type-safe navigation edge and arrives in a `SavedStateHandle`.
 */
@Serializable
enum class PinMode {
    /** First PIN. Enter, then confirm; on success App Lock opens. */
    Set,

    /**
     * Re-key. Verify the **old** PIN first, then enter and confirm the new one.
     *
     * The competitor's change flow never asks for the old PIN (`SacskipActivity.java:99-105`), so
     * anyone already past the entry gate can silently re-key. `PinStep.Verify` is what closes that.
     */
    Change,

    /** The gate into the App Lock home. One entry, compared against the stored digest. */
    Verify,
}
