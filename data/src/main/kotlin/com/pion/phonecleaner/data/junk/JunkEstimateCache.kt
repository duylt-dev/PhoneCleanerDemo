package com.pion.phonecleaner.data.junk

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.domain.repository.JunkScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

/**
 * The silent estimator — no screen, no ViewModel (`docs/screens/12-junk-cleaning.md` §6).
 *
 * It replaces `wc.k`, whose whole design is a de-duplication hack wearing a TTL's clothes. Here the
 * two jobs are **two separate mechanisms**, and neither number is load-bearing on the other:
 *
 *  * a `Mutex` + `Deferred` **single-flight**, so N concurrent callers share ONE walk and all
 *    receive its value. `wc.k`'s `if (f82140b) return;` drops the second caller silently, so the
 *    Home badge's callback never fires and the badge keeps its old value with no indication
 *    (Deltas E1, E2);
 *  * a **TTL**, persisted, so it survives a restart. `wc.k`'s two statics reset on process death and
 *    there is no persisted timestamp, so every cold start re-walks the whole tree on the first Home
 *    resume (Delta E3).
 *
 * [cachedBytes] is one `Flow<Long?>` from DataStore — "observe, don't fetch". The competitor keeps
 * three copies of one number (a `volatile` static, a `commit()`ed preference and an Activity field),
 * none of them observable, which is why Home needs its own re-entry guard (Delta E5).
 *
 * `null` means "never measured" or "invalidated", and the badge renders that as *not measured*
 * rather than as "no junk found" (Delta E6). The competitor writes a literal `0` after a clean and
 * shows it (Deltas C8, C9).
 *
 * The keys live here rather than in `data/datastore/`: they are read by nothing outside this class,
 * and this cluster does not own that package.
 */
internal class JunkEstimateCache(
    private val scanner: JunkScanner,
    private val store: DataStore<Preferences>,
    private val clock: AppClock,
    private val scope: CoroutineScope,
) {

    private val mutex = Mutex()
    private var inFlight: Deferred<AppResult<Long>>? = null

    fun cachedBytes(): Flow<Long?> = store.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs -> prefs[KEY_BYTES] }
        .distinctUntilChanged()

    /**
     * A fresh value returns without walking; concurrent callers `await()` the same walk.
     *
     * The `Deferred` is replaced once it is no longer active, so a completed one is never served as
     * a cached answer — the TTL is the cache, and the `Deferred` is only the de-duplication.
     */
    suspend fun estimate(force: Boolean = false): AppResult<Long> {
        val walk = mutex.withLock {
            if (!force) freshValue()?.let { return it.asSuccess() }
            inFlight?.takeIf { it.isActive } ?: scope.async { measure() }.also { inFlight = it }
        }
        return walk.await()
    }

    /**
     * Records a total a **visible** scan already produced, so the badge is right without a second
     * walk. The competitor starts two identical full-tree scans two statements apart on a cold entry
     * to the scan screen (`TaribrActivity.java:441`, `:443`, Delta S4).
     */
    suspend fun publish(totalBytes: Long) {
        writeMeasurement(totalBytes)
    }

    suspend fun invalidate() {
        runCatching {
            store.edit { prefs ->
                prefs.remove(KEY_BYTES)
                prefs.remove(KEY_MEASURED_AT)
            }
        }
    }

    private suspend fun measure(): AppResult<Long> = try {
        val finished = scanner.scan().collectFinished()
        if (finished == null) {
            AppError.Unexpected("the junk scan produced no terminal emission").asFailure()
        } else {
            writeMeasurement(finished.totalBytes)
            finished.totalBytes.asSuccess()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AppError.Storage(cause = throwable.message).asFailure()
    }

    private suspend fun writeMeasurement(totalBytes: Long) {
        runCatching {
            store.edit { prefs ->
                prefs[KEY_BYTES] = totalBytes.coerceAtLeast(0L)
                prefs[KEY_MEASURED_AT] = clock.now().toEpochMilliseconds()
            }
        }
    }

    /** Reads the stored total when it is still inside the TTL. */
    private suspend fun freshValue(): Long? {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return null
        val bytes = prefs[KEY_BYTES] ?: return null
        val measuredAt = prefs[KEY_MEASURED_AT] ?: return null
        val age = clock.now().toEpochMilliseconds() - measuredAt
        return bytes.takeIf { age in 0..TTL.inWholeMilliseconds }
    }

    private companion object {
        val KEY_BYTES = longPreferencesKey("junk_estimate_bytes")
        val KEY_MEASURED_AT = longPreferencesKey("junk_estimate_measured_at")

        /**
         * §6's stated figure. It is a cache, so it is minutes rather than the competitor's 20
         * seconds — which is far too short to be a cache and far too long to be correctness.
         */
        val TTL = 5.minutes
    }
}

/** The terminal emission, and only that: the estimator has no use for progress (Delta E4). */
private suspend fun Flow<ScanProgress>.collectFinished(): ScanProgress.Finished? {
    var finished: ScanProgress.Finished? = null
    collect { progress -> if (progress is ScanProgress.Finished) finished = progress }
    return finished
}
