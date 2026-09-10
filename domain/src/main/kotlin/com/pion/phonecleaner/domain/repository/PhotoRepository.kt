package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The photo cluster's read surface, and its one delete path
 * (`docs/screens/13-photo-and-media.md` §0.3).
 *
 * **The storage branch is `MediaStore` + SAF and `MANAGE_EXTERNAL_STORAGE` is never assumed
 * grantable** (§0.1, `docs/system-architecture.md` §8.1). Nothing above this interface learns which
 * branch is active: a screen names this port, the implementation names the mechanism. The competitor
 * demands all-files access for three of these four tools.
 */
interface PhotoRepository {

    /**
     * The **tool** surface: the images every engine in this cluster can actually re-encode or re-tag.
     *
     * MIME-filtered to jpeg and png, which is the filter all three engines already carry — the
     * similar scan (`nd/a`, `docs/reverse-engineering/13-photo-and-media.md` §4.1 step 1), the
     * compression scan (`nd/b`, §4.2) and, per `docs/screens/13-photo-and-media.md` §5.2, the privacy
     * candidate query, whose competitor equivalent opens an `ExifInterface` on **every** image row
     * because it forgets the filter.
     *
     * [observeAlbums] and [photosInFolder] are deliberately **not** filtered: an album index that
     * hides a HEIC photo is wrong about what is on the device.
     */
    suspend fun photos(): AppResult<ImmutableList<Photo>>

    /**
     * The album index, as a feed. A photo added or deleted elsewhere updates the screen without a
     * re-entry (§6.2); the competitor re-queries only in `onCreate`.
     */
    fun observeAlbums(): Flow<AppResult<ImmutableList<PhotoAlbum>>>

    /** Every image under one full parent path — the album's identity, not its display name (§6.5). */
    suspend fun photosInFolder(folderName: String): AppResult<ImmutableList<Photo>>

    /**
     * **This is not a second deleter.** It maps `Photo` -> `ScannedFile` with
     * `FileOrigin.MediaStoreEntry(contentUri)` taken from the scan and delegates to [FileDeleter],
     * which is declared once in `storageDataModule` (§0.3, `LLM.md` §6.4).
     *
     * The mapping sets `ScannedFile.id = Photo.contentUri`, so every id that comes back on
     * [DeleteOutcome.Deleted] and [DeleteOutcome.PendingConsent] is a value the reducer already has
     * on the row: it prunes with `photo.contentUri in outcome.ids`, with no parsing and no
     * `android.net.Uri` anywhere near a ViewModel.
     *
     * [DeleteOutcome.PendingConsent] is the **normal** path on API 30+, not a failure:
     * `MediaStore.createDeleteRequest` raises a system dialog only an Activity can launch, so the
     * reducer turns it into an Effect the Route unwraps, and calls this again afterwards. In the
     * all-files branch the case simply never fires (`docs/system-architecture.md` §8.4).
     */
    suspend fun delete(ids: List<PhotoId>): AppResult<DeleteOutcome>

    /**
     * The **pure projection** `Photo -> ScannedFile` for [ids], with no delete policy attached (plan
     * `260908-0801-trash-bin`, Phase 07, key insight 4). [delete] is refactored to call this, so there
     * remains exactly **one** projection out of this interface, in one place.
     *
     * Why this exists rather than branching inside [delete]: `DeletePhotosUseCase` needs the resolved
     * `ScannedFile`s to hand to `TrashRepository.trashFiles` when the bin is available, and that
     * branch has to live in `:domain` where it is visible (engineer decision E3) — putting it here,
     * inside `:data`, would hide the exception exactly where E3 forbids it.
     */
    suspend fun resolve(ids: List<PhotoId>): AppResult<ImmutableList<ScannedFile>>
}
