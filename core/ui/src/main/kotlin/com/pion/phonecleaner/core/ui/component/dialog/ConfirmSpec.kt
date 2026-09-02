package com.pion.phonecleaner.core.ui.component.dialog

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * Six of the competitor's eighteen dialog presenters are the same dialog with different copy. This
 * is the difference between them, held as a nullable field on a screen's `State`
 * (`docs/screens/21` §3.2).
 *
 * **It carries [count] plus a `@PluralsRes` body, never a pre-formatted `String`.** Two reasons, both
 * observed: the competitor appends an English plural `"s"` by hand at `java/nc/n0.java:62`, and a
 * formatted `String` held on state is still the old locale's text after the in-app language picker
 * changes it — the app ships 17 locales.
 *
 * It holds resource ids, so it lives in `:core:ui` next to `FeatureDescriptor` rather than in
 * `:domain`, for the reason `LLM.md` §2 gives: the rule bends by moving the type, not by weakening it.
 *
 * A dialog is **state, never an `Effect`** — an `Effect` is a one-shot instruction the UI performs and
 * forgets, and a dialog is a visible condition that must survive a rotation, a dark-mode switch and a
 * `LaunchedEffect` restart (system-architecture §4.6).
 */
@Immutable
data class ConfirmSpec(
    @param:StringRes @get:StringRes val titleRes: Int,
    @param:PluralsRes @get:PluralsRes val bodyRes: Int,
    /** How many items the action is about. Passed to `pluralStringResource`, never formatted here. */
    val count: Int,
    @param:StringRes @get:StringRes val confirmRes: Int,
    /** `true` renders the confirm action in the error colour. Deletion is the common case. */
    val destructive: Boolean = true,
)
