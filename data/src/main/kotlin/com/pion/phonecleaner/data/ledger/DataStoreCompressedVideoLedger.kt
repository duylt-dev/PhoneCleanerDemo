package com.pion.phonecleaner.data.ledger

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.pion.phonecleaner.data.datastore.CompressedVideosPrefs
import com.pion.phonecleaner.domain.repository.CompressedVideoLedger
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * The ledger behind [CompressedVideoLedger], on the one process-wide `DataStore<Preferences>`
 * (`data/di/CoreModule.kt`) — never a second store. Mirrors [DataStoreCompressedPhotoLedger]
 * character for character on the mechanics (`phase-04-data-media3-engine.md` step 5); two
 * differences are deliberate:
 *
 * - Ids are **`String`s** (`content://` URIs of the *output* row), not `Long`s, so [decode] keeps
 *   every non-blank split part rather than parsing with `toLongOrNull`.
 * - [MAX_TRACKED] is **1 000**, far smaller than the photo ledger's 5 000 — a device holds hundreds
 *   of videos, not thousands. The cost of the cap being hit is that the oldest output becomes
 *   re-compressible again, a mild annoyance and never data loss.
 *
 * Bound in `filesDataModule`, not `coreDataModule`: only the files cluster names this type
 * (`LLM.md` §6.4).
 */
internal class DataStoreCompressedVideoLedger(
    private val store: DataStore<Preferences>,
) : CompressedVideoLedger {

    override suspend fun compressedIds(): Set<String> = read().toSet()

    /**
     * Re-recording an id moves it to the newest end rather than duplicating it. One `edit` per video,
     * off the caller's thread: `DataStore` serialises writes on its own scope.
     */
    override suspend fun record(id: String) {
        store.edit { prefs ->
            val kept = decode(prefs[CompressedVideosPrefs.COMPRESSED_VIDEO_IDS])
                .filterTo(mutableListOf()) { it != id }
            kept += id
            if (kept.size > MAX_TRACKED) kept.subList(0, kept.size - MAX_TRACKED).clear()
            prefs[CompressedVideosPrefs.COMPRESSED_VIDEO_IDS] = kept.joinToString(CompressedVideosPrefs.SEPARATOR)
        }
    }

    /**
     * A corrupt or unreadable preferences file reads as "nothing recorded" — the same rule
     * [DataStoreCompressedPhotoLedger] applies. Throwing here would turn a bookkeeping fault into a
     * failed scan on a screen that has a perfectly good list to show.
     */
    private suspend fun read(): List<String> {
        val prefs = store.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .first()
        return decode(prefs[CompressedVideosPrefs.COMPRESSED_VIDEO_IDS])
    }

    private fun decode(raw: String?): List<String> = raw
        ?.splitToSequence(CompressedVideosPrefs.SEPARATOR)
        ?.filter(String::isNotBlank)
        ?.toList()
        .orEmpty()

    private companion object {
        /** A device holds hundreds of videos, not thousands — see the class KDoc. */
        const val MAX_TRACKED = 1_000
    }
}
