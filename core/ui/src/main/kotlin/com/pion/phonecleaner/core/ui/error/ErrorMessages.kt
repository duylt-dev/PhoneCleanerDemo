package com.pion.phonecleaner.core.ui.error

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.ui.R

/**
 * `AppError` -> `@StringRes`, resolved **at render time**.
 *
 * The ViewModel never builds user-facing copy (MVI §5), and the reason is not purity: the app ships a
 * 17-locale in-app language picker, so a `String` built when the failure happened is still the old
 * locale's text after the user switches. The competitor's equivalent envelope, `ae.i1`, carries two
 * message fields and an `Object data` that every caller casts (system-architecture §4 / `r2-12:922`).
 *
 * A new `AppError` case adds its case in `:core:common/error/` and its copy here, in the same change
 * (`LLM.md` §4) — the `when` below is exhaustive over the sealed interface, so a case added without
 * copy fails the compile rather than reaching a user as a blank.
 *
 * `docs/screens/19-network-and-speed-test.md:233` maps a failure onto `AppError.Timeout`, which does
 * not exist on the sealed interface. The network cluster maps it to
 * [AppError.Unexpected] unless `:core:common` gains the case first; nothing is invented here.
 */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    is AppError.PermissionDenied -> R.string.error_permission_denied
    AppError.NoNetwork -> R.string.error_no_network
    is AppError.NotFound -> R.string.error_not_found
    is AppError.Storage -> R.string.error_storage
    is AppError.Unexpected -> R.string.error_unexpected
}

/**
 * The composable form. Named as MVI §5 names it (`core/util/ErrorMessages.kt`'s `userMessage()`),
 * so a screen reads `state.error?.let { Text(it.userMessage()) }`.
 */
@Composable
fun AppError.userMessage(): String = stringResource(messageRes())
