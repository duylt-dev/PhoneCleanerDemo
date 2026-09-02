package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile

/**
 * Primitive 3 of 3. **The only deleter in the app** — the photo cluster maps `Photo` to
 * [ScannedFile] and delegates here rather than owning a second one
 * (`docs/screens/13-photo-and-media.md:91`). Declared once in `storageDataModule`.
 *
 * Replaces `nd.g` + `md.q3.d` and a `deleteRecursively` wrapped in
 * `catch (Exception) { printStackTrace() }` returning `0L`.
 *
 * The parameter is a plain `List` because it is an input the caller already owns; the
 * `ImmutableList` rule (`LLM.md` §8) governs what a repository **hands out**, which is
 * [DeleteOutcome].
 */
interface FileDeleter {
    suspend fun delete(files: List<ScannedFile>): AppResult<DeleteOutcome>
}
