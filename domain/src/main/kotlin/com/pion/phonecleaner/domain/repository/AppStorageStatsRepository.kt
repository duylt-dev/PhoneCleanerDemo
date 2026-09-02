package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.app.AppStorageStats
import com.pion.phonecleaner.domain.model.app.InstalledApp
import kotlinx.coroutines.flow.Flow

/**
 * How much disk each app occupies — **split off [InstalledAppsRepository] on purpose**
 * (`docs/system-architecture.md` §4.1, `docs/screens/14-file-tools-and-app-manager.md` §0.1).
 *
 * Three of the four clusters that list installed apps do not want this: measuring it costs a
 * `StorageStatsManager` round trip per package, and on a 180-app device that is the slow half of the
 * screen. Keeping it on the shared port would make every caller pay for it.
 *
 * DECLARED IN `filesDataModule` — this cluster is its only caller. `InstalledAppsRepository` is
 * declared in `coreDataModule` and is **not** redeclared beside it (`LLM.md` §6.4).
 *
 * A `Flow`, not a `suspend fun` returning a list: the App Manager renders its rows from
 * `InstalledAppsProgress.Enumerated` and fills sizes in as they land. **The concurrency is the
 * implementation's** — `dispatchers.io.limitedParallelism(8)` inside the repository, never chosen at
 * the call site (`LLM.md` §6.5). Choosing per call site is what put one competitor scanner on
 * `Default` while its four siblings ran on IO.
 */
interface AppStorageStatsRepository {

    /**
     * One emission per app, in completion order. An app the platform refuses to measure still emits,
     * with null sizes: a row that says `—` is honest, and the competitor's `1000L` default is not.
     *
     * The parameter is a plain `List` because it is an input the caller already owns; the
     * `Immutable*` rule governs what a repository hands out (`LLM.md` §8).
     */
    fun statsFor(apps: List<InstalledApp>): Flow<AppStorageStats>
}
