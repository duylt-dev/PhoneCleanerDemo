package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import kotlinx.coroutines.flow.Flow

/**
 * Primitive 1 of 3. **There is no type called `FileScanner`**
 * (`docs/screens/14-file-tools-and-app-manager.md:46`).
 *
 * Five competitor scanner families that share no code collapse into these three primitives, declared
 * once in `storageDataModule`. Every feature engine — `JunkScanner`, `DuplicateFinder`,
 * `SimilarPhotoScanner`, `WhatsAppScanner` — **composes** them and none re-implements a walk
 * (`docs/system-architecture.md` §4.5).
 *
 * The bounds live on [WalkConfig], not in the implementation, so a caller states them and a test can
 * shrink them. Cancellation is the collector's: the flow honours `ensureActive()` per directory
 * inside the collector's scope, so cancelling the collecting job stops the walk.
 */
interface StorageScanner {
    fun walk(config: WalkConfig): Flow<ScannedFile>
}
