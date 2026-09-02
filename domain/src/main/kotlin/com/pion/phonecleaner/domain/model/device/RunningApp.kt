package com.pion.phonecleaner.domain.model.device

/**
 * One row of the stoppable-apps list (`docs/screens/18-device-battery-and-apps.md` §1, §6).
 *
 * **One field.** `label` and `icon` are not here: five competitor models hold a `Drawable`, and every
 * one of them already carries the package name, which is the icon's identity. `AppIconLoader`
 * (`coreUiModule`) resolves the glyph at draw time.
 *
 * The name is adjudicated (`docs/system-architecture.md` §4.1): it is **not** `InstalledApp`, which is
 * the App Manager's rich seven-field model, and **not** `StoppableApp` — `isStopped` folds onto this
 * type rather than spawning a second one.
 */
data class RunningApp(
    val packageName: String,
    /**
     * Set only after a resume observes `ApplicationInfo.FLAG_STOPPED`
     * (§6.5). Nothing else may set it: the app cannot verify a force-stop any other way, and claiming
     * one it did not observe is the defect this field exists to avoid.
     */
    val isStopped: Boolean = false,
)
