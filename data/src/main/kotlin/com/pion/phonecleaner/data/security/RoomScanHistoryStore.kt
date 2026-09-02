package com.pion.phonecleaner.data.security

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.data.database.ThreatCacheDao
import com.pion.phonecleaner.domain.model.security.ScanRecord
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * `threat_cache`, through the DAO that is **already `single` in `coreDataModule`**. This cluster
 * injects it and declares no second binding, and opens no second database
 * (`docs/screens/15-antivirus.md` §0.4; `LLM.md` §6.4).
 *
 * The clock is injected because a test that asserts on `finishedAtEpochMs` cannot pin
 * `System.currentTimeMillis()`; `AppClock` is `single` in `coreModule`.
 */
internal class RoomScanHistoryStore(
    private val dao: ThreatCacheDao,
    private val clock: AppClock,
    private val dispatchers: DispatcherProvider,
) : ScanHistoryStore {

    /**
     * `MAX(found_at)` and the active rows, combined. A `null` timestamp is "no scan has ever
     * completed"; once every row has been removed or ignored the record goes back to `null`, and the
     * result screen renders its empty state rather than a stale headline.
     */
    override fun observeRecord(): Flow<ScanRecord?> =
        combine(dao.observeLastFoundAt(), dao.observeActive()) { foundAt, rows ->
            foundAt?.let { ScanRecord(it, rows.map { row -> row.toVerdict() }.toImmutableList()) }
        }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    /**
     * **The ignore flags are read before the write and carried forward.** The DAO's own KDoc names
     * this hazard: `insertAll` is `REPLACE`, so re-inserting an md5 the user had ignored resets
     * `is_ignored` to its default and the ignore lasts exactly until the next scan.
     *
     * The clear-then-insert pair is not in one transaction: a `@Transaction` method would have to be
     * added to `ThreatCacheDao`, which belongs to `coreDataModule`'s owner. The worst case is an
     * empty table if the process dies between the two writes — the next scan repopulates it, and
     * nothing the user chose is lost, because the ignore set is re-read at the start of the next
     * save from whatever survived.
     */
    override suspend fun save(findings: List<ThreatVerdict>): AppResult<Unit> = write {
        val ignored = dao.ignoredMd5s().toSet()
        val foundAt = clock.now().toEpochMilliseconds()
        dao.clearAll()
        dao.insertAll(findings.map { it.toEntity(foundAt, isIgnored = it.md5 in ignored) })
    }

    override suspend fun forget(md5: String): AppResult<Unit> = write { dao.deleteByMd5(md5) }

    override suspend fun ignore(md5: String): AppResult<Unit> = write { dao.markIgnored(md5) }

    /**
     * A repository never throws across the boundary (`LLM.md` §4) — a Room failure becomes
     * `AppError.Storage`.
     *
     * `CancellationException` is rethrown explicitly, and `runCatching` is deliberately not used: it
     * catches `Throwable`, which includes the cancellation a caller uses to stop this work, and
     * swallowing it strands the coroutine that was being cancelled.
     */
    private suspend fun write(block: suspend () -> Unit): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                block()
                AppResult.Success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError.Storage(cause = throwable.message))
            }
        }
}
