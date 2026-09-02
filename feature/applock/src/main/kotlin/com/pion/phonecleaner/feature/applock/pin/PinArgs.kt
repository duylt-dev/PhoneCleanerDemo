package com.pion.phonecleaner.feature.applock.pin

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.model.applock.PinMode

/**
 * The `SavedStateHandle` key the `Pin` route's argument arrives under, and the one reader of it.
 *
 * A route argument **is** the initial state (MVI §3): it is read once, in the ViewModel's
 * constructor, and never re-read from an `Intent` on recreation.
 *
 * > **UNKNOWN — the `Pin` route argument's name.** Looked for in `docs/screens/16-app-lock.md` §2.1
 * > (which proposes `mode: PinMode`, then says *"fix the name before the cluster is built, do not
 * > invent it twice"*), in `LLM.md` §7.2 (*"no report writes the argument name or the enum
 * > constants"*) and in `docs/system-architecture.md` §6.1/§10.3 U4. `PinMode`'s own KDoc in
 * > `:domain` already follows that proposal, so this constant matches it rather than inventing a
 * > second spelling. With Navigation-Compose type-safe destinations the key is the route class's
 * > **property name**, so the declaration reported in `routesNeeded` names its property `mode`.
 */
object PinArgs {
    const val MODE = "mode"
}

/**
 * Reads the argument without asserting how Navigation stored it: a type-safe route may hand back the
 * enum itself or its `name`, and `SavedStateHandle.get<T>` is an unchecked cast that would fail at
 * the use site rather than here.
 *
 * Unrecognised input falls back to [PinMode.Verify] — the **most** restrictive of the three, because
 * the other two write a PIN and this one only compares against a stored digest. A malformed argument
 * must never be a path to re-keying.
 */
internal fun SavedStateHandle.pinMode(): PinMode = when (val raw = get<Any?>(PinArgs.MODE)) {
    is PinMode -> raw
    is String -> PinMode.entries.firstOrNull { it.name == raw } ?: PinMode.Verify
    else -> PinMode.Verify
}

/**
 * The argument, turned into the state the screen opens on. `PinMode.Change` proves the **old** PIN
 * first; the other two open straight onto an entry.
 */
internal fun SavedStateHandle.initialPinState(): PinState = pinMode().let { mode ->
    PinState(mode = mode, step = if (mode == PinMode.Change) PinStep.Verify else PinStep.Enter)
}
