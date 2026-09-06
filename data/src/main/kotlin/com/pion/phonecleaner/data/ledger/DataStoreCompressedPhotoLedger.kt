package com.pion.phonecleaner.data.ledger

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.pion.phonecleaner.data.datastore.CompressedPhotosPrefs
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.CompressedPhotoLedger
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * The ledger behind [CompressedPhotoLedger], on the one process-wide `DataStore<Preferences>`
 * (`data/datastore/AppDataStore.kt`) — never a second store and never a `SharedPreferences`.
 *
 * Bound in `photoDataModule`, not `coreDataModule`: only the photo cluster names this type, and
 * `LLM.md` §6.4 lets a per-cluster module declare exactly the types no other cluster names.
 *
 * Room is not the mechanism for the same reason it is not the mechanism for the other two ledgers:
 * the app holds exactly two tables and this is key-value bookkeeping
 * (`docs/system-architecture.md` §5.7).
 */
internal class DataStoreCompressedPhotoLedger(
    private val store: DataStore<Preferences>,
) : CompressedPhotoLedger {

    override suspend fun compressedIds(): Set<PhotoId> = read().mapTo(mutableSetOf(), ::PhotoId)

    /**
     * Re-recording an id moves it to the newest end rather than duplicating it, so a photo the user
     * compresses again is also the last one [MAX_TRACKED] would drop.
     *
     * One `edit` per photo, off the caller's thread: `DataStore` serialises writes on its own scope.
     * The competitor's equivalent bookkeeping is `commit()` on the calling thread
     * (`docs/system-architecture.md` §7.3 item 1).
     */
    override suspend fun record(id: PhotoId) {
        store.edit { prefs ->
            val kept = decode(prefs[CompressedPhotosPrefs.COMPRESSED_PHOTO_IDS])
                .filterTo(mutableListOf()) { it != id.value }
            kept += id.value
            if (kept.size > MAX_TRACKED) kept.subList(0, kept.size - MAX_TRACKED).clear()
            prefs[CompressedPhotosPrefs.COMPRESSED_PHOTO_IDS] =
                kept.joinToString(CompressedPhotosPrefs.SEPARATOR)
        }
    }

    /**
     * A corrupt or unreadable preferences file reads as "nothing recorded" — the same rule
     * [DataStoreFeatureUsageRepository] applies. Throwing here would turn a bookkeeping fault into a
     * failed scan on a screen that has a perfectly good list to show.
     */
    private suspend fun read(): List<Long> {
        val prefs = store.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .first()
        return decode(prefs[CompressedPhotosPrefs.COMPRESSED_PHOTO_IDS])
    }

    private fun decode(raw: String?): List<Long> = raw
        ?.splitToSequence(CompressedPhotosPrefs.SEPARATOR)
        ?.mapNotNull(String::toLongOrNull)
        ?.toList()
        .orEmpty()

    private companion object {
        /**
         * The oldest entries are dropped past this many. The record is only ever read whole, so an
         * unbounded list would put every photo the install has ever compressed into memory on every
         * scan; 5 000 ids is roughly 60 KB of text and far more photos than a device holds above the
         * candidate threshold.
         */
        const val MAX_TRACKED = 5_000
    }
}
