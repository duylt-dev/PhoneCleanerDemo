package com.pion.phonecleaner.feature.trash.testing

import androidx.lifecycle.ViewModelStore
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.*
import com.pion.phonecleaner.feature.trash.TrashIntent
import com.pion.phonecleaner.feature.trash.TrashViewModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf

internal class TrashFixture(
    val repository: FakeTrashRepository = FakeTrashRepository(),
    val clock: FakeClock = FakeClock(),
) : AutoCloseable {
    val permissions = FakePermissionRepository()
    val ledger = FakeCleanupLedger()
    val vm = TrashViewModel(
        ObserveTrashUseCase(repository), ObserveTrashSummaryUseCase(repository),
        RestoreFromTrashUseCase(repository), RestoreZipFromTrashUseCase(repository),
        DeleteTrashForeverUseCase(repository, ledger),
        ReconcileTrashUseCase(repository), permissions, clock,
    )
    private val store = ViewModelStore().apply { put("trash", vm) }

    fun start() {
        vm.onIntent(TrashIntent.ScreenResumed)
        vm.onIntent(TrashIntent.ScreenStarted)
    }

    fun restore(id: String = "entry-1") {
        vm.onIntent(TrashIntent.EntryToggled(id))
        vm.onIntent(TrashIntent.RestorePressed)
        vm.onIntent(TrashIntent.ConfirmAccepted)
    }

    override fun close() = store.clear()
}

internal class FakePermissionRepository : PermissionRepository {
    var allFilesGranted = true
    override fun isGranted(permission: AppPermission): Boolean = allFilesGranted
    override fun observe() = flowOf(persistentSetOf(AppPermission.AllFiles))
    override fun missingFor(feature: FeatureId) = persistentSetOf<AppPermission>()
}

internal class FakeCleanupLedger : CleanupLedger {
    var creditedBytes = 0L
    override suspend fun record(freedBytes: Long): AppResult<Unit> {
        creditedBytes += freedBytes
        return AppResult.Success(Unit)
    }
    override fun observeLifetimeFreedBytes() = flowOf(creditedBytes)
}
