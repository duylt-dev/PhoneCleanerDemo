package com.pion.phonecleaner.domain.model.file

/**
 * One file a scan found. Canonical for the six file tools; it folds five competitor row types into
 * one (`docs/system-architecture.md` §4.5).
 *
 * `Photo` — the photo cluster's own projection — is kept **separate** on purpose: a similar-photo
 * grid needs `perceptualHash` and `takenAt` and a big-file row does not, and merging them puts two
 * nullable fields on every row of a 5 000-item list (`LLM.md` §12). Both projections must come out of
 * **one** `ContentResolver` query builder in `:data/storage` (`docs/system-architecture.md` §10.3 U7).
 *
 * No `isSelected` field: selection lives on `State` as a `Set<String>` of [id] beside the list
 * (`LLM.md` §8). A mutated item is `equals` its predecessor inside the old list, so no diff can see it.
 */
data class ScannedFile(
    /**
     * Stable identity, and the only thing a selection, a delete request or a `LazyColumn` key ever
     * carries. `DeleteOutcome.Deleted.ids` is a list of these.
     */
    val id: String,
    /** Absolute path. Reported back in `DeleteOutcome.Deleted.failedPaths`. */
    val path: String,
    /** Display name. The ViewModel never formats it; the composable renders it as-is. */
    val name: String,
    /**
     * Size in BYTES, never kilobytes. The competitor's `ce.a.lengthKb: Float` cannot hold 4 GiB to
     * the byte (`docs/screens/14-file-tools-and-app-manager.md:476`).
     */
    val sizeBytes: Long,
    val kind: FileKind,
    val origin: FileOrigin,
    /**
     * `MediaStore`'s `mime_type` column where the row came from a media collection, null for a walk.
     * Carried because "the column is already in the row"
     * (`docs/screens/14-file-tools-and-app-manager.md:408`); `MimeTypeUseCase` falls back to the
     * extension when it is null.
     */
    val mimeType: String? = null,
    /**
     * Epoch millis of last modification. `DuplicateGroup.newestId` is decided from this, so it is not
     * optional on a row a duplicate finder may produce.
     */
    val lastModifiedAtMillis: Long = 0L,
)
