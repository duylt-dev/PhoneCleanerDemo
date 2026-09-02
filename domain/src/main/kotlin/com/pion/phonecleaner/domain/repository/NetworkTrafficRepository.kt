package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.domain.model.network.TrafficReport

/**
 * Per-app data use, read from the platform. Replaces the competitor's `cd.d`
 * (`docs/screens/19-network-and-speed-test.md` §0), bound **once**, in `networkDataModule`.
 *
 * ### Why this returns [AppResult] rather than an empty report
 *
 * Every one of `cd.d`'s five `try` blocks is `catch (Exception unused) {}`
 * (`docs/reverse-engineering/19-network-and-speed-test.md` :519-521), so a `SecurityException` from a
 * missing usage-access grant produces an empty list that is indistinguishable from a month in which
 * no app used data. That is finding 13 of the chapter. A refusal here is
 * `AppError.PermissionDenied`, an absent service is `AppError.NotFound`, and the screen can tell
 * them apart.
 *
 * ### Threading
 *
 * The implementation chooses its own dispatcher through `DispatcherProvider`. The caller must not
 * wrap this in `withContext` — `cd.d.d`'s two call sites each pick a dispatcher, so the same query
 * behaves differently depending on which screen asked (`LLM.md` §6.5).
 */
interface NetworkTrafficRepository {
    suspend fun report(period: TrafficPeriod): AppResult<TrafficReport>
}
