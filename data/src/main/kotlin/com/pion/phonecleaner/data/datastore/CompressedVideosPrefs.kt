package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The key table for the video compressor's ledger of already re-encoded videos. Mirrors
 * [CompressedPhotosPrefs] (`phase-04-data-media3-engine.md` step 5).
 *
 * One key, holding output-row `content://` ids **oldest first**, joined by [SEPARATOR]. Same reason
 * as the photo table: a `stringSetPreferencesKey` carries no order, so the record could never be
 * trimmed by age and would grow for the life of the install.
 *
 * **Ids are `String`s (`content://` URIs), not `Long`s** — [DataStoreCompressedVideoLedger] records
 * the output row's full URI, not a raw numeric id. [SEPARATOR] is a comma, reused from
 * [CompressedPhotosPrefs.SEPARATOR]: a `content://media/external/video/media/123` URI cannot itself
 * contain a comma (`Uri` encodes reserved characters), so it is safe as a join/split delimiter here
 * exactly as it is for the photo ledger's numeric ids.
 */
internal object CompressedVideosPrefs {
    /** Absent means "nothing compressed yet", never "compression failed". */
    val COMPRESSED_VIDEO_IDS = stringPreferencesKey("compressed_video_ids")

    const val SEPARATOR: String = CompressedPhotosPrefs.SEPARATOR
}
