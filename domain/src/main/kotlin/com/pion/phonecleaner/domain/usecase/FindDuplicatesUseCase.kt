package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.repository.DuplicateFinder
import kotlinx.coroutines.flow.Flow

/**
 * The duplicate pipeline, as the screen sees it
 * (`docs/screens/14-file-tools-and-app-manager.md` §2.2).
 *
 * It delegates: the five stages the appendix assigns to this use case — collect, dedupe by path,
 * group by exact size, digest, group by digest — live in [DuplicateFinder]'s implementation, because
 * three of them are I/O against the shared primitives and `:domain` holds no I/O. Splitting them
 * across both would put half an algorithm in each, which is the arrangement the appendix is
 * replacing.
 *
 * It exists rather than the ViewModel naming the engine for one reason worth the file: a ViewModel
 * that names an engine names a `:data` type's port directly, and every other screen in this cluster
 * goes through a use case. Registered `factoryOf(::FindDuplicatesUseCase)` in `domainModule`.
 */
class FindDuplicatesUseCase(
    private val finder: DuplicateFinder,
) {
    operator fun invoke(): Flow<DuplicateScanProgress> = finder.find()
}
