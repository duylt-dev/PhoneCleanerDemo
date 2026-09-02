package com.pion.phonecleaner.domain.model.onboarding

import com.pion.phonecleaner.core.common.result.AppResult

/**
 * Reads ONE line of the one-time device check.
 *
 * Replaces `cd.a`'s static reads as used by `AssimssesActivity.X()` (`:546-572`) — and only those
 * five. `cd.a` also exposes `/proc/stat` CPU busy, ABIs and core count, RAM via
 * `ActivityManager.MemoryInfo`, and `scaling_cur_freq`; **none of them is used by this screen**
 * (`docs/reverse-engineering/10-splash-and-onboarding.md:504`). They are the device-status cluster's
 * data source, behind that cluster's own port.
 *
 * **One field per call, not one snapshot of all five.** `X()` is called once, synchronously, before
 * the animation starts, and every row is then held in `LOADING` for exactly 1 000 ms with no call
 * between — nine seconds of theatre over five reads that already happened
 * (`docs/screens/10-splash-and-onboarding.md` §3.5 delta 2). Here each row's probe runs inside its
 * own step and `DeviceCheckPacing.perRowMillis` is the **floor** on that step, not a sleep.
 *
 * A failure is an [AppResult.Failure], never a fallback that looks like a reading: every competitor
 * probe swallows its exception and renders `"0KB/0KB"` (`od/p0.java:289-291`), which is a number the
 * user has no way to distinguish from a real one (delta 10).
 *
 * ---
 * **UNKNOWN — where this interface belongs.** `LLM.md` §4 puts a repository interface in
 * `:domain/repository/`, and this is one. It is declared here because `:domain/repository/` is
 * outside this change's file ownership and three other agents are writing concurrently. Looked for,
 * and not found: any existing port over `Build` / window metrics — `:domain/repository/` holds
 * twenty files and none of them reads a device fact, and no `deviceDataModule` exists yet.
 *
 * **It must be moved to `:domain/repository/DeviceCheckProbe.kt`, or folded into the device
 * cluster's `DeviceMetricsRepository` once that type exists and has an owner**
 * (`docs/screens/10-splash-and-onboarding.md` §3.4 names `DeviceMetricsRepository` as
 * "`deviceDataModule` — do not redeclare"). Deliberately **not** named `DeviceMetricsRepository`
 * here: two `single`s of that type in two modules is a load-order coin flip that Koin resolves
 * silently (`LLM.md` §6.4), and this narrow five-field port is not that type.
 */
interface DeviceCheckProbe {

    /**
     * The reading for [field]. Suspends; the dispatcher is chosen **inside** the implementation
     * (`LLM.md` §6.5), never by the ViewModel that calls it.
     */
    suspend fun read(field: DeviceInfoField): AppResult<DeviceInfoValue>
}
