package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The key table for the photo compressor's ledger of already re-encoded photos.
 *
 * One key, holding `MediaStore` ids **oldest first**, joined by [SEPARATOR].
 *
 * A `stringSetPreferencesKey` is the obvious shape here and is the wrong one: a `Set` carries no
 * order, so the record could never be trimmed by age and would grow for the life of the install —
 * every entry read back into memory on every scan. The joined form keeps insertion order, which is
 * the only thing that makes `DataStoreCompressedPhotoLedger.MAX_TRACKED` enforceable.
 *
 * Ids are `Long`s, so no id can ever contain the separator.
 */
internal object CompressedPhotosPrefs {
    /** Absent means "nothing compressed yet", never "compression failed". */
    val COMPRESSED_PHOTO_IDS = stringPreferencesKey("compressed_photo_ids")

    const val SEPARATOR: String = ","
}
