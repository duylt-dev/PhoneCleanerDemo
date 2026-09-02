package com.pion.phonecleaner.data.ledger

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.data.datastore.CleanupLedgerPrefs
import com.pion.phonecleaner.domain.repository.CleanupLedger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The lifetime "space freed" total the Home footer renders. Declared once, in `coreDataModule`
 * (`docs/system-architecture.md` §5.4) — two clusters had each declared their own.
 *
 * It replaces `md.g4`, whose accumulator is fed by `g4.e()`: a function that **parses the formatted
 * display string back into a `Long`**, loses about 5 % to `DecimalFormat("###.0")`, and defaults an
 * unrecognised unit to MB (`java/md/g4.java:41-109`). Here [record] takes a raw `Long` from the use
 * case that actually freed the bytes, and `ByteFormatter` stays display-only — `parseBytes` does not
 * exist and must not be added (§4.2).
 *
 * **The ledger is fed at the source, not at the result screen**
 * (`docs/screens/14-file-tools-and-app-manager.md:926`). The competitor's junk clean writes
 * `shown = if (cleanedSize > 0) cleanedSize else totalSize`, so a total failure is recorded here as a
 * total success (`docs/screens/12-junk-cleaning.md:737`).
 */
internal class DataStoreCleanupLedger(
    private val store: DataStore<Preferences>,
) : CleanupLedger {

    /**
     * Returns `AppResult` and never throws, per §7.6. A failure to write bookkeeping the user did not
     * ask for is logged by the caller and never surfaced — but it is still *returned*, so the caller
     * gets to make that decision rather than having it made by a swallowed exception.
     *
     * Negative input is clamped rather than rejected: a ledger that can go backwards is a ledger that
     * can disagree with itself, and no caller has a use for subtracting.
     */
    override suspend fun record(freedBytes: Long): AppResult<Unit> = try {
        val delta = freedBytes.coerceAtLeast(0L)
        if (delta > 0L) {
            store.edit { prefs ->
                val current = prefs[CleanupLedgerPrefs.LIFETIME_FREED_BYTES] ?: 0L
                prefs[CleanupLedgerPrefs.LIFETIME_FREED_BYTES] = current + delta
            }
        }
        Unit.asSuccess()
    } catch (e: IOException) {
        AppError.Storage(cause = e.message).asFailure()
    }

    /**
     * `catch` and not a `try`: `DataStore.data` surfaces a corrupt-file read as an `IOException` in
     * the flow, and a footer that cannot read its total should render 0, not take the screen down.
     */
    override fun observeLifetimeFreedBytes(): Flow<Long> = store.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs -> prefs[CleanupLedgerPrefs.LIFETIME_FREED_BYTES] ?: 0L }
        .distinctUntilChanged()
}
