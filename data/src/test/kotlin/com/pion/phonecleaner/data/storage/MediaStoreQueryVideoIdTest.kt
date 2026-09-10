package com.pion.phonecleaner.data.storage

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The round trip a fake-backed ViewModel test can never see: a fake ledger matches whatever id string
 * the test hands it, so the mismatch this pins was invisible to all 119 of them.
 *
 * `VideoOutputPublisher` inserts on `VOLUME_EXTERNAL_PRIMARY` because inserting into the read-only
 * `VOLUME_EXTERNAL` union throws, while `MediaStoreQuery.videosUri()` reads `VOLUME_EXTERNAL` from
 * API 29 up. `ScannedFile.id` is the URI *string*, so without a translation the id recorded at
 * publish time never compares equal to the id the next scan produces for that same row — and
 * `alreadyCompressed` is then false for ever: no badge, "Select all" re-encoding this app's own
 * outputs, and any output under the size floor silently gone from the next list.
 */
@RunWith(RobolectricTestRunner::class)
internal class MediaStoreQueryVideoIdTest {

    private val rowId = 12L

    /** What the publisher's `insert` returns. */
    private fun writeVolumeUri(): Uri =
        ContentUris.withAppendedId(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), rowId)

    /** What `toScannedFile` builds, and therefore what every candidate id in the app looks like. */
    private fun scannedId(): String =
        ContentUris.withAppendedId(MediaStoreQuery.videosUri(), rowId).toString()

    @Config(sdk = [29])
    @Test
    fun `the write volume and the read volume genuinely differ on API 29`() {
        assertNotEquals(scannedId(), writeVolumeUri().toString())
    }

    @Config(sdk = [29])
    @Test
    fun `a published URI resolves to the id the scanner will produce on API 29`() {
        assertEquals(scannedId(), MediaStoreQuery.videoIdFor(writeVolumeUri()))
    }

    @Config(sdk = [36])
    @Test
    fun `the same holds on the current target SDK`() {
        assertEquals(scannedId(), MediaStoreQuery.videoIdFor(writeVolumeUri()))
    }

    /** API 28 has one external volume, so the translation must be a no-op rather than a rewrite. */
    @Config(sdk = [28])
    @Test
    fun `an id already on the read volume is returned unchanged`() {
        val alreadyRead = ContentUris.withAppendedId(MediaStoreQuery.videosUri(), rowId)
        assertEquals(alreadyRead.toString(), MediaStoreQuery.videoIdFor(alreadyRead))
    }

    /** Not a media row: keep the string rather than throw away the only handle on the file. */
    @Config(sdk = [29])
    @Test
    fun `a URI with no numeric id falls back to its raw string`() {
        val odd = Uri.parse("content://media/external/video/media/not-a-number")
        assertEquals(odd.toString(), MediaStoreQuery.videoIdFor(odd))
    }
}
