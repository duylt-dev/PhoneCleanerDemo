package com.pion.phonecleaner.feature.photo.testing

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.trash.TrashDirectoryRequest
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashMoveOutcome
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.domain.model.trash.TrashSummary
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The two fakes `DeletePhotosUseCase` needs for its trash branch (plan `260908-0801-trash-bin`,
 * Phase 07). Split out of `PhotoFakes.kt` the way `PhotoScanFakes.kt` already is — one file per
 * concern, kept under the 200-line guideline (`.claude/rules/development-rules.md`).
 */

/**
 * [available] answers `isAvailable()`; defaults to `false` so an existing test that never sets it
 * keeps exercising the permanent-delete branch unchanged. Only [isAvailable] and [trashFiles] are
 * exercised from this module; the rest of [TrashRepository] belongs to `:feature:trash` and is
 * stubbed only enough to implement the interface.
 */
internal class FakeTrashRepository(
    var available: Boolean = false,
    var moveOutcomes: MutableList<AppResult<TrashMoveOutcome>> = mutableListOf(),
) : TrashRepository {
    val trashFilesCalls = mutableListOf<Pair<List<ScannedFile>, FeatureId>>()

    override suspend fun isAvailable(): Boolean = available

    override suspend fun trashFiles(
        files: List<ScannedFile>,
        source: FeatureId,
    ): AppResult<TrashMoveOutcome> {
        trashFilesCalls += files to source
        return moveOutcomes.removeFirstOrNull()
            ?: AppResult.Success(
                TrashMoveOutcome(
                    movedIds = files.map(ScannedFile::id).toImmutableList(),
                    movedBytes = files.sumOf(ScannedFile::sizeBytes),
                    failedPaths = persistentListOf(),
                ),
            )
    }

    override suspend fun trashDirectory(request: TrashDirectoryRequest): AppResult<TrashMoveOutcome> =
        AppResult.Success(
            TrashMoveOutcome(movedIds = persistentListOf(request.path), movedBytes = 0L, failedPaths = persistentListOf()),
        )

    override fun observeEntries(): Flow<ImmutableList<TrashEntry>> = MutableStateFlow(persistentListOf())
    override fun observeSummary(): Flow<TrashSummary> = MutableStateFlow(TrashSummary())
    override suspend fun restore(ids: List<String>): AppResult<TrashRestoreOutcome> =
        AppResult.Success(TrashRestoreOutcome(persistentListOf(), persistentListOf(), 0))

    override suspend fun restoreZip(ids: List<String>): AppResult<TrashRestoreOutcome> =
        AppResult.Success(TrashRestoreOutcome(persistentListOf(), persistentListOf(), 0))

    override suspend fun deleteForever(ids: List<String>): AppResult<TrashPurgeOutcome> =
        AppResult.Success(TrashPurgeOutcome(persistentListOf(), 0L, persistentListOf()))

    override suspend fun deleteAllForever(): AppResult<TrashPurgeOutcome> = deleteForever(emptyList())

    override suspend fun purgeExpired(): AppResult<TrashPurgeOutcome> =
        AppResult.Success(TrashPurgeOutcome(persistentListOf(), 0L, persistentListOf()))

    override suspend fun reconcile(): AppResult<Int> = AppResult.Success(0)
}

internal class FakePermissionRepository : PermissionRepository {
    val granted = MutableStateFlow<ImmutableSet<AppPermission>>(persistentSetOf())
    override fun observe(): Flow<ImmutableSet<AppPermission>> = granted
    override fun isGranted(permission: AppPermission): Boolean = permission in granted.value
    override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> = persistentSetOf()
}
