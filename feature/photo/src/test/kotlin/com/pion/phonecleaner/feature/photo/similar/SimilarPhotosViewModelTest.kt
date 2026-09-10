package com.pion.phonecleaner.feature.photo.similar

import app.cash.turbine.test
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.SimilarScanProgress
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.usecase.DeletePhotosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ScanSimilarPhotosUseCase
import com.pion.phonecleaner.feature.photo.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.photo.testing.FakePhotoRepository
import com.pion.phonecleaner.feature.photo.testing.FakeSimilarPhotoScanner
import com.pion.phonecleaner.feature.photo.testing.FakeSimilarPhotoSessionStore
import com.pion.phonecleaner.feature.photo.testing.FakeTrashRepository
import com.pion.phonecleaner.feature.photo.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.photo.testing.RecordingAnalytics
import com.pion.phonecleaner.feature.photo.testing.RecordingFeatureUsage
import com.pion.phonecleaner.feature.photo.testing.group
import com.pion.phonecleaner.feature.photo.testing.photo
import com.pion.phonecleaner.feature.photo.testing.runVmTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** `docs/screens/13-photo-and-media.md` §1.2 and the six deltas of §1.4. */
class SimilarPhotosViewModelTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val opener = photo(1, sizeBytes = 500L)
    private val duplicateA = photo(2, sizeBytes = 400L)
    private val duplicateB = photo(3, sizeBytes = 300L)
    private val scanned = persistentListOf(group("g1", opener, duplicateA, duplicateB))

    private val scanner = FakeSimilarPhotoScanner(
        emissions = listOf(
            SimilarScanProgress.Hashing(done = 2, total = 3),
            SimilarScanProgress.Done(groups = scanned, skipped = 1),
        ),
    )
    private val repository = FakePhotoRepository()
    private val session = FakeSimilarPhotoSessionStore()
    private val analytics = RecordingAnalytics()

    private fun viewModel() = SimilarPhotosViewModel(
        scanSimilar = ScanSimilarPhotosUseCase(scanner),
        deletePhotos = DeletePhotosUseCase(repository, FakeTrashRepository()),
        session = session,
        markFeatureUsed = MarkFeatureUsedUseCase(RecordingFeatureUsage()),
        analytics = analytics,
        permissions = FakePermissionRepository(),
    )

    private fun started() = viewModel().also {
        it.onIntent(SimilarPhotosIntent.ScreenStarted)
        it.onIntent(SimilarPhotosIntent.CompletionAnimationFinished)
    }

    @Test
    fun `the scan pre-selects every member but the opener, and reports what it skipped`() =
        main.runVmTest {
            val vm = started()

            assertEquals(setOf(PhotoId(2), PhotoId(3)), vm.state.value.selectedIds)
            // A hash failure is not a similarity: it is counted and said out loud (§1.4).
            assertEquals(1, vm.state.value.skipped)
            assertEquals(1_200L, vm.state.value.totalBytes)
            assertEquals(700L, vm.state.value.selectedBytes)
            assertEquals(ToolPhase.Ready, vm.state.value.phase)
            assertTrue(vm.state.value.canDelete)
        }

    @Test
    fun `keep one selects the whole group except its opener`() = main.runVmTest {
        val vm = started()
        vm.onIntent(SimilarPhotosIntent.SelectAllToggled)
        assertEquals(3, vm.state.value.selectedCount)
        vm.onIntent(SimilarPhotosIntent.SelectAllToggled)
        assertEquals(0, vm.state.value.selectedCount)

        vm.onIntent(SimilarPhotosIntent.GroupCleanupPressed("g1"))

        assertEquals(setOf(PhotoId(2), PhotoId(3)), vm.state.value.selectedIds)
    }

    @Test
    fun `opening a photo resolves its own group and index`() = main.runVmTest {
        val vm = started()

        vm.effects.test {
            vm.onIntent(SimilarPhotosIntent.PhotoOpened(PhotoId(3)))
            assertEquals(SimilarPhotosEffect.OpenPreview("g1", 2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PendingConsent is a state the screen renders, not an error`() = main.runVmTest {
        val token = PendingIntentToken("intent-sender")
        repository.nextDelete = AppResult.Success(
            DeleteOutcome.PendingConsent(
                request = token,
                ids = persistentListOf(duplicateA.contentUri, duplicateB.contentUri),
            ),
        )
        val vm = started()

        vm.effects.test {
            vm.onIntent(SimilarPhotosIntent.DeletePressed)
            vm.onIntent(SimilarPhotosIntent.DeleteConfirmed)
            assertEquals(SimilarPhotosEffect.RequestDeleteConsent(token), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, vm.state.value.pendingConsentUris.size)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `a granted consent prunes the group and reports the bytes it held`() = main.runVmTest {
        repository.nextDelete = AppResult.Success(
            DeleteOutcome.PendingConsent(
                request = PendingIntentToken("intent-sender"),
                ids = persistentListOf(duplicateA.contentUri, duplicateB.contentUri),
            ),
        )
        val vm = started()

        vm.effects.test {
            vm.onIntent(SimilarPhotosIntent.DeletePressed)
            vm.onIntent(SimilarPhotosIntent.DeleteConfirmed)
            // The consent request the system dialog answers; the buffered channel replays it here.
            assertTrue(awaitItem() is SimilarPhotosEffect.RequestDeleteConsent)

            vm.onIntent(SimilarPhotosIntent.DeleteConsentResult(granted = true))
            val effect = awaitItem() as SimilarPhotosEffect.NavigateToCleanResult
            assertEquals(FeatureId.SimilarPhotos, effect.summary.feature)
            assertEquals(700L, effect.summary.freedBytes)
            assertEquals(2, effect.summary.itemCount)
            assertEquals(CleanupOutcome.Cleaned, effect.summary.outcome)
            cancelAndIgnoreRemainingEvents()
        }
        // A group that falls to one member is no longer a group, so the empty state is immediate.
        assertTrue(vm.state.value.groups.isEmpty())
        assertTrue(vm.state.value.showEmptyState)
    }

    @Test
    fun `a declined consent says so and puts the phase back down`() = main.runVmTest {
        val vm = started()
        vm.onIntent(SimilarPhotosIntent.DeletePressed)
        vm.onIntent(SimilarPhotosIntent.DeleteConfirmed)

        vm.onIntent(SimilarPhotosIntent.DeleteConsentResult(granted = false))

        assertTrue(vm.state.value.consentDeclined)
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
        assertTrue(vm.state.value.pendingConsentUris.isEmpty())
        // Nothing was removed, so the selection is untouched.
        assertFalse(vm.state.value.selectedIds.isEmpty())
    }
}
