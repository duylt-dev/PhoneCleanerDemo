package com.pion.phonecleaner.feature.files.duplicates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.DuplicateGroup
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.FindDuplicatesUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.MimeTypeUseCase
import com.pion.phonecleaner.feature.files.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.files.testing.FakeCleanupLedger
import com.pion.phonecleaner.feature.files.testing.FakeDuplicateFinder
import com.pion.phonecleaner.feature.files.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.files.testing.FakeFileDeleter
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class DuplicatesViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val finder = FakeDuplicateFinder()
    private val deleter = FakeFileDeleter()
    private val analytics = FakeAnalyticsRepository()
    private val featureUsage = FakeFeatureUsageRepository()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = DuplicatesViewModel(
        savedState = savedState,
        findDuplicates = FindDuplicatesUseCase(finder),
        deleteFiles = DeleteFilesUseCase(deleter, FakeCleanupLedger()),
        markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
        mimeTypeOf = MimeTypeUseCase(),
        analytics = analytics,
        log = AppLogger.NoOp,
    )

    /** The pre-selection is the product: every copy but the kept one arrives selected (§2.2). */
    @Test
    fun `older copies are pre-selected when the groups arrive`() = mainDispatcher.runVmTest {
        finder.emissions = listOf(DuplicateScanProgress.Finished(persistentListOf(group())))
        val vm = viewModel()

        vm.onIntent(DuplicatesIntent.ScreenStarted)
        settle()

        assertEquals(setOf("b", "c"), vm.state.value.selectedIds.toSet())
        assertEquals(ToolPhase.Completing, vm.state.value.phase)
    }

    /** A truncated scan publishes what completed AND says so — it does not report success (§2.2). */
    @Test
    fun `truncation reaches the state with the groups that completed`() = mainDispatcher.runVmTest {
        finder.emissions = listOf(
            DuplicateScanProgress.Hashing(hashed = 3, candidates = 9),
            DuplicateScanProgress.Finished(persistentListOf(group()), truncated = true),
        )
        val vm = viewModel()

        vm.onIntent(DuplicatesIntent.ScreenStarted)
        settle()

        assertTrue(vm.state.value.scanTruncated)
        assertEquals(1, vm.state.value.groups.size)
    }

    /** "Select all older copies" is the way back from a deselect; the competitor has none (§2.4). */
    @Test
    fun `deselect all then select all older restores the pre-selection`() =
        mainDispatcher.runVmTest {
            finder.emissions = listOf(DuplicateScanProgress.Finished(persistentListOf(group())))
            val vm = viewModel()
            vm.onIntent(DuplicatesIntent.ScreenStarted)
            settle()

            vm.onIntent(DuplicatesIntent.DeselectAllPressed)
            assertTrue(vm.state.value.selectedIds.isEmpty())

            vm.onIntent(DuplicatesIntent.SelectAllOlderPressed)
            assertEquals(setOf("b", "c"), vm.state.value.selectedIds.toSet())
        }

    /** A group down to one member is not a duplicate any more and leaves the list. */
    @Test
    fun `a delete prunes the rows and drops a group left with one copy`() =
        mainDispatcher.runVmTest {
            finder.emissions = listOf(DuplicateScanProgress.Finished(persistentListOf(group())))
            val vm = viewModel()
            vm.onIntent(DuplicatesIntent.ScreenStarted)
            settle()
            vm.onIntent(DuplicatesIntent.CompletionAnimationFinished)

            vm.effects.test {
                vm.onIntent(DuplicatesIntent.DeletePressed)
                vm.onIntent(DuplicatesIntent.DeleteConfirmed)
                settle()

                val effect = awaitItem()
                assertTrue(effect is DuplicatesEffect.NavigateToCleanResult)
                assertEquals(2L * FileBytes, (effect as DuplicatesEffect.NavigateToCleanResult).bytesFreed)
            }
            assertTrue(vm.state.value.groups.isEmpty())
            assertEquals(ToolPhase.Ready, vm.state.value.phase)
        }

    /** `PendingConsent` is a round trip the UI runs, never an error (§0.2). */
    @Test
    fun `pending consent asks the user and re-issues exactly the same ids`() =
        mainDispatcher.runVmTest {
            finder.emissions = listOf(DuplicateScanProgress.Finished(persistentListOf(group())))
            deleter.outcomes = mutableListOf(
                AppResult.Success(
                    DeleteOutcome.PendingConsent(
                        request = PendingIntentToken("token"),
                        ids = persistentListOf("b"),
                    ),
                ),
            )
            val vm = viewModel()
            vm.onIntent(DuplicatesIntent.ScreenStarted)
            settle()
            vm.onIntent(DuplicatesIntent.CompletionAnimationFinished)

            vm.effects.test {
                vm.onIntent(DuplicatesIntent.DeletePressed)
                vm.onIntent(DuplicatesIntent.DeleteConfirmed)
                settle()
                assertTrue(awaitItem() is DuplicatesEffect.RequestDeleteConsent)
                assertFalse(vm.state.value.error != null)

                vm.onIntent(DuplicatesIntent.DeleteConsentResult(granted = true))
                settle()
                assertTrue(awaitItem() is DuplicatesEffect.NavigateToCleanResult)
            }
            assertEquals(listOf("b"), deleter.requested.last().map(ScannedFile::id))
        }

    /** A stale id restored from `SavedStateHandle` is dropped, never sent to the deleter. */
    @Test
    fun `a restored selection keeps only ids that are still present`() = mainDispatcher.runVmTest {
        finder.emissions = listOf(DuplicateScanProgress.Finished(persistentListOf(group())))
        val savedState = SavedStateHandle().apply {
            set("files.selectedIds", arrayListOf("b", "gone"))
        }
        val vm = viewModel(savedState)

        vm.onIntent(DuplicatesIntent.ScreenStarted)
        settle()

        assertEquals(setOf("b"), vm.state.value.selectedIds.toSet())
    }

    private companion object {
        const val FileBytes = 1_024L

        fun file(id: String, modifiedAt: Long) = ScannedFile(
            id = id,
            path = "/storage/$id.jpg",
            name = "$id.jpg",
            sizeBytes = FileBytes,
            kind = FileKind.Image,
            origin = FileOrigin.PlainFile,
            lastModifiedAtMillis = modifiedAt,
        )

        fun group() = DuplicateGroup(
            md5 = "digest",
            files = listOf(file("a", 300L), file("b", 200L), file("c", 100L)).toImmutableList(),
            newestId = "a",
        )
    }
}
