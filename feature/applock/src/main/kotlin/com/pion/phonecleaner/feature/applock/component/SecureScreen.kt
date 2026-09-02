package com.pion.phonecleaner.feature.applock.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * `FLAG_SECURE` on the host window while a PIN surface is on screen, cleared when it leaves.
 *
 * Neither of the competitor's two PIN surfaces sets it (`docs/screens/16-app-lock.md` §2.5, §3.5),
 * so the pad is screenshottable and lands in the recents thumbnail — a PIN pad with four dots filled
 * in tells an observer the length, and a screen recorder tells them the rest.
 *
 * It is set and cleared by a `DisposableEffect` rather than in an Activity's `onCreate`, because the
 * `pin` screen is one destination inside a shared window: a flag added on entry and never removed
 * would leave every later screen in that task unscreenshottable, which is a different bug.
 *
 * Shared by `pin` and `lockscreen`, which is why it is in the cluster's `component/` package rather
 * than either screen's (`LLM.md` §3.7).
 */
@Composable
internal fun SecureScreen() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * A composable's `Context` is a `ContextThemeWrapper` around the Activity, not the Activity — so
 * this unwraps rather than casting. A cast is what `md.g1`'s adapter base does, and its two call
 * sites disagree about whether the result can be null.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
