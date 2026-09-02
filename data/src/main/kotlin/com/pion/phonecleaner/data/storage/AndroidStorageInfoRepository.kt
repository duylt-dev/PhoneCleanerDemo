package com.pion.phonecleaner.data.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.device.StorageInfo
import com.pion.phonecleaner.domain.repository.StorageInfoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Total and available bytes on the primary volume — the Home storage ring, and the storage figures on
 * the device screens.
 *
 * It replaces `od.p0.n/o/q`, and it is where `docs/system-architecture.md` §5.6 records the specific
 * defect being removed: **`od.p0.p()` is a getter that `commit()`s inside a read**. Reading a number
 * must not write anything, so the read half is here and pure, and the "has the day rolled over"
 * bookkeeping is an explicit call on `CleanStatsRepository` — a different type, in a different module.
 *
 * **No percentage is computed here.** `StorageInfo` carries bytes, and `usedBytes` is derived on the
 * model. A ring renders occupancy from bytes; nothing in this app turns storage into a performance
 * figure (`LLM.md` §1's wording rule).
 */
internal class AndroidStorageInfoRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : StorageInfoRepository {

    /**
     * Observe, don't fetch (MVI §5): the ring stays correct after a clean performed anywhere, without
     * every screen remembering to re-read.
     *
     * Cold and bounded by the collector. `collectAsStateWithLifecycle()` stops collecting when the
     * screen stops, so the poll below cannot become the competitor's `Reciespec` Timer B — a rebuild
     * **every 5 s while the screen is on** whether or not anything changed (§7.2). `distinctUntilChanged`
     * means an unchanged reading costs one `StatFs` call and no emission.
     *
     * [REFRESH_INTERVAL] is ours, not a ported constant: free space changes when the user or another
     * app deletes something, and there is no broadcast for it.
     */
    override fun observe(): Flow<StorageInfo> = flow {
        while (true) {
            emit(read())
            delay(REFRESH_INTERVAL)
        }
    }.distinctUntilChanged().flowOn(dispatchers.io)

    override suspend fun current(): AppResult<StorageInfo> = withContext(dispatchers.io) {
        try {
            read().asSuccess()
        } catch (e: IllegalArgumentException) {
            AppError.Storage(cause = e.message).asFailure()
        } catch (e: IOException) {
            AppError.Storage(cause = e.message).asFailure()
        }
    }

    /**
     * `StorageStatsManager.getTotalBytes` (API 26+) reports the manufacturer's advertised capacity,
     * which is the number printed on the box and the one a user recognises. `StatFs` reports the
     * formatted partition, which is several gigabytes smaller and looks like a bug to a reader
     * comparing it with Settings. Available bytes come from `StatFs` in both paths, because that is
     * the number a clean actually moves.
     */
    private fun read(): StorageInfo {
        val dataDirectory = Environment.getDataDirectory()
        val stats = StatFs(dataDirectory.absolutePath)
        val available = stats.availableBytes
        val total = advertisedTotalBytes() ?: stats.totalBytes
        return StorageInfo(totalBytes = total, availableBytes = available.coerceAtMost(total))
    }

    private fun advertisedTotalBytes(): Long? = runCatching {
        context.getSystemService(StorageStatsManager::class.java)
            ?.getTotalBytes(StorageManager.UUID_DEFAULT)
    }.getOrNull()

    private companion object {
        val REFRESH_INTERVAL = 10.seconds
    }
}
