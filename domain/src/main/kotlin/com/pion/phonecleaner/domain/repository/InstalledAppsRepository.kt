package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.app.InstalledApp
import kotlinx.collections.immutable.ImmutableList

/**
 * The one list-installed-apps port. **Four clusters claimed it** — files, app-lock, notification and
 * device — and the competitor has three separate entry points to the same list (`vd.c`, `vd.d.d(pm)`,
 * `cd.j.b()`). One binding, in `coreDataModule` (`docs/system-architecture.md` §4.1, §5.4).
 *
 * Storage statistics are deliberately **not** here: they are `AppStorageStatsRepository` in
 * `filesDataModule` (§4.1), because measuring them costs a `StorageStatsManager` round trip per
 * package that three of the four callers do not want.
 *
 * The enumeration runs on `dispatchers.default` **inside the implementation** and is cached in
 * memory; icons are never enumerated — a row asks `AppIconLoader` by package name at draw time
 * (`docs/screens/17-notification-and-permissions.md:303`).
 *
 * UNKNOWN — no appendix states this interface's method names or its argument list; they are chosen
 * here. What *is* stated is the distinction [includeWithoutLauncher] preserves: `vd.c.a()` and
 * `vd.d.d(pm)` are **different enumerations** — the App Manager includes apps with no launcher
 * activity, the Permission Manager does not
 * (`docs/reverse-engineering/17-notification-and-permissions.md:785`). Collapsing them to one port
 * without that parameter would silently pick one caller's answer for both.
 */
interface InstalledAppsRepository {

    suspend fun installedApps(
        includeWithoutLauncher: Boolean = false,
    ): AppResult<ImmutableList<InstalledApp>>

    /** null means the package is not installed, or is filtered out by the same rules as the list. */
    suspend fun find(packageName: String): AppResult<InstalledApp?>
}
