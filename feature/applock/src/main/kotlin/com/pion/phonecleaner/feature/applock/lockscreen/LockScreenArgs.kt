package com.pion.phonecleaner.feature.applock.lockscreen

import androidx.lifecycle.SavedStateHandle

/**
 * The `Intent` extra `LockScreenActivity` carries, and the one reader of it.
 *
 * **This screen is not a `NavHost` route** (`LLM.md` §7.5): it must appear over *another* app,
 * driven by `ForegroundAppMonitor.lockRequests`, not by anything the user taps in ours. So it is an
 * `Activity` in `:app` — `launchMode="singleTask"`, `exported="false"` — and the target package
 * reaches this ViewModel through the extras that `ComponentActivity` folds into its default
 * `SavedStateHandle`. `LockScreenActivity` therefore maps its `Intent` by putting one extra under
 * [PACKAGE_NAME]; nothing else in the app reads an `Intent` (`LLM.md` §7.3).
 *
 * > UNKNOWN — the extra's name. `docs/screens/16-app-lock.md` §3.2 says only that *"`targetPackage`
 * > arrives through `SavedStateHandle`"*, and `LLM.md` §7.5 and `docs/system-architecture.md` §6.4
 * > fix the *shape* (an Activity, not a route) without naming the extra. The constant is declared
 * > **here**, in the module that reads it, so `:app` imports it rather than repeating a string
 * > literal — one spelling, checked by the compiler. The competitor's equivalent is an `exported`
 * > Activity taking an arbitrary `pkg` extra from any app on the device
 * > (`AndroidManifest.xml:246`); ours is not exported, so the key is an internal contract between
 * > two of our own files.
 */
object LockScreenArgs {
    const val PACKAGE_NAME = "targetPackage"
}

/**
 * An empty package is a first-class value, not a crash: the extra is missing whenever the Activity
 * is recreated from a task the system rebuilt. The screen then renders an unlabelled prompt and
 * still refuses input until the PIN is right — a denial of service on the lock surface would be a
 * worse failure than a missing label.
 */
internal fun SavedStateHandle.lockTargetPackage(): String =
    get<String>(LockScreenArgs.PACKAGE_NAME).orEmpty()
