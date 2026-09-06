package com.pion.phonecleaner.feature.photo.blurry

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.photo.BlurScanProgress
import com.pion.phonecleaner.domain.model.photo.BlurTier
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.usecase.DeletePhotosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ScanBlurryPhotosUseCase
import com.pion.phonecleaner.feature.photo.testing.FakeBlurryPhotoScanner
import com.pion.phonecleaner.feature.photo.testing.FakeBlurryPhotoSessionStore
import com.pion.phonecleaner.feature.photo.testing.FakePhotoRepository
import com.pion.phonecleaner.feature.photo.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.photo.testing.RecordingAnalytics
import com.pion.phonecleaner.feature.photo.testing.RecordingFeatureUsage
import com.pion.phonecleaner.feature.photo.testing.group
import com.pion.phonecleaner.feature.photo.testing.photo
import com.pion.phonecleaner.feature.photo.testing.runVmTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The blurry-photo grid.
 *
 * The cases that are **not** copies of `SimilarPhotosViewModelTest` are the ones that pin the
 * owner's pre-selection decision and its consequences: everything arrives ticked, a tier of one
 * survives a delete, and the notice that says so goes away the moment the user unticks anything.
 */
class BlurryPhotosViewModelTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val veryBlurryA = photo(1, sizeBytes = 500L)
    private val veryBlurryB = photo(2, sizeBytes = 400L)
    private val slightlyBlurry = photo(3, sizeBytes = 300L)

    private val scanned = persistentListOf(
        group(BlurTier.VeryBlurry.name, veryBlurryA, veryBlurryB),
        group(BlurTier.SlightlyBlurry.name, slightlyBlurry),
    )

    private val scanner = FakeBlurryPhotoScanner(
        emissions = listOf(
            BlurScanProgress.Scoring(done = 2, total = 3),
            BlurScanProgress.Done(groups = scanned, skipped = 1),
        ),
    )
    private val repository = FakePhotoRepository()
    private val session = FakeBlurryPhotoSessionStore()
    private val analytics = RecordingAnalytics()

    private fun viewModel() = BlurryPhotosViewModel(
        scanBlurry = ScanBlurryPhotosUseCase(scanner),
        deletePhotos = DeletePhotosUseCase(repository),
        session = session,
        markFeatureUsed = MarkFeatureUsedUseCase(RecordingFeatureUsage()),
        analytics = analytics,
    )

    private fun started() = viewModel().also {
        it.onIntent(BlurryPhotosIntent.ScreenStarted)
        it.onIntent(BlurryPhotosIntent.CompletionAnimationFinished)
    }

    @Test
    fun `the scan reports measured counts, not a fixed floor`() = main.runVmTest {
        val vm = viewModel()

        vm.onIntent(BlurryPhotosIntent.ScreenStarted)

        val state = vm.state.value
        assertEquals(3, state.toScore)
        assertEquals(1, state.skipped)
        // The scan finishes into Completing; the screen's animation moves it to Ready.
        assertEquals(ToolPhase.Completing, state.phase)
    }

    /** The owner's decision of 2026-09-06, and the reason this screen exists in this shape. */
    @Test
    fun `every row the scan found arrives pre-selected`() = main.runVmTest {
        val vm = started()

        val state = vm.state.value
        assertEquals(setOf(PhotoId(1), PhotoId(2), PhotoId(3)), state.selectedIds)
        assertEquals(3, state.selectedCount)
        assertEquals(1200L, state.selectedBytes)
        assertTrue(state.canDelete)
    }

    /**
     * The mitigation that decision leaves in place: a user who touched nothing is one button away
     * from deleting everything the scan found, and the screen has to say so.
     */
    @Test
    fun `the pre-selected notice shows until the user changes the selection`() = main.runVmTest {
        val vm = started()
        assertTrue(vm.state.value.isPreselected)

        vm.onIntent(BlurryPhotosIntent.PhotoToggled(PhotoId(2)))

        // From here the selection is the user's, and claiming it was made for them would be false.
        assertFalse(vm.state.value.isPreselected)
    }

    @Test
    fun `the groups are the tiers, in tier order`() = main.runVmTest {
        val vm = started()

        assertEquals(
            listOf(BlurTier.VeryBlurry.name, BlurTier.SlightlyBlurry.name),
            vm.state.value.groups.map { it.key },
        )
    }

    @Test
    fun `a tier header toggles only its own tier`() = main.runVmTest {
        val vm = started()

        vm.onIntent(BlurryPhotosIntent.TierToggled(BlurTier.VeryBlurry.name))

        // That tier was fully selected, so the header clears it — and leaves the other alone.
        assertEquals(setOf(PhotoId(3)), vm.state.value.selectedIds)

        vm.onIntent(BlurryPhotosIntent.TierToggled(BlurTier.VeryBlurry.name))
        assertEquals(setOf(PhotoId(1), PhotoId(2), PhotoId(3)), vm.state.value.selectedIds)
    }

    @Test
    fun `select-all clears when everything is already selected`() = main.runVmTest {
        val vm = started()

        vm.onIntent(BlurryPhotosIntent.SelectAllToggled)

        assertEquals(emptySet<PhotoId>(), vm.state.value.selectedIds)
        assertFalse(vm.state.value.canDelete)
    }

    @Test
    fun `opening a photo carries the group key and the index within it`() = main.runVmTest {
        val vm = started()

        vm.effects.test {
            vm.onIntent(BlurryPhotosIntent.PhotoOpened(PhotoId(2)))
            assertEquals(
                BlurryPhotosEffect.OpenPreview(BlurTier.VeryBlurry.name, 1),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a delete prunes the rows and reports what was actually freed`() = main.runVmTest {
        repository.nextDelete = AppResult.Success(
            DeleteOutcome.Deleted(
                ids = persistentListOf(veryBlurryA.contentUri, veryBlurryB.contentUri),
                freedBytes = 900L,
                failedPaths = persistentListOf(),
            ),
        )
        val vm = started()

        vm.effects.test {
            vm.onIntent(BlurryPhotosIntent.DeletePressed)
            vm.onIntent(BlurryPhotosIntent.DeleteConfirmed)

            val effect = awaitItem() as BlurryPhotosEffect.NavigateToCleanResult
            assertEquals(FeatureId.BlurryPhotos, effect.summary.feature)
            assertEquals(900L, effect.summary.freedBytes)
            assertEquals(2, effect.summary.itemCount)
            assertEquals(CleanupOutcome.Cleaned, effect.summary.outcome)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    /**
     * Where this store parts company with the similar one: `InMemorySimilarPhotoSessionStore.remove`
     * drops a group that falls below two members, because a group of one is not a similarity. One
     * remaining blurry photo is still a blurry photo.
     */
    @Test
    fun `a tier that falls to one member survives the delete`() = main.runVmTest {
        repository.nextDelete = AppResult.Success(
            DeleteOutcome.Deleted(
                ids = persistentListOf(veryBlurryA.contentUri),
                freedBytes = 500L,
                failedPaths = persistentListOf(),
            ),
        )
        val vm = started()

        vm.onIntent(BlurryPhotosIntent.DeletePressed)
        vm.onIntent(BlurryPhotosIntent.DeleteConfirmed)

        val groups = vm.state.value.groups
        assertEquals(2, groups.size)
        assertEquals(listOf(veryBlurryB), groups.first().photos)
        assertFalse(vm.state.value.showEmptyState)
    }

    @Test
    fun `pending consent is a state, not an error`() = main.runVmTest {
        val token = PendingIntentToken("intent-sender")
        repository.nextDelete = AppResult.Success(
            DeleteOutcome.PendingConsent(
                request = token,
                ids = persistentListOf(veryBlurryA.contentUri),
            ),
        )
        val vm = started()

        vm.effects.test {
            vm.onIntent(BlurryPhotosIntent.DeletePressed)
            vm.onIntent(BlurryPhotosIntent.DeleteConfirmed)
            assertEquals(BlurryPhotosEffect.RequestDeleteConsent(token), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(vm.state.value.error)
        assertEquals(setOf(veryBlurryA.contentUri), vm.state.value.pendingConsentUris)
    }

    @Test
    fun `a declined consent removes nothing and says so`() = main.runVmTest {
        repository.nextDelete = AppResult.Success(
            DeleteOutcome.PendingConsent(
                request = PendingIntentToken("intent-sender"),
                ids = persistentListOf(veryBlurryA.contentUri),
            ),
        )
        val vm = started()
        vm.onIntent(BlurryPhotosIntent.DeletePressed)
        vm.onIntent(BlurryPhotosIntent.DeleteConfirmed)

        vm.onIntent(BlurryPhotosIntent.DeleteConsentResult(granted = false))

        assertTrue(vm.state.value.consentDeclined)
        assertEquals(3, vm.state.value.groups.sumOf { it.photos.size })
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    @Test
    fun `a failed scan surfaces the error rather than an empty grid`() = main.runVmTest {
        scanner.emissions = listOf(BlurScanProgress.Failed(AppError.PermissionDenied()))
        val vm = viewModel()

        vm.onIntent(BlurryPhotosIntent.ScreenStarted)

        assertEquals(AppError.PermissionDenied(), vm.state.value.error)
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    /**
     * The `ErrorCard`'s retry raises `ScreenStarted`, and a failed scan leaves the screen at
     * `Ready` — so a bare `phase != Idle` guard would swallow it and draw a button that does
     * nothing. `SimilarPhotosViewModel` still has that shape; recorded in `LLM.md` §11.
     */
    @Test
    fun `retry after a failed scan actually re-scans`() = main.runVmTest {
        scanner.emissions = listOf(BlurScanProgress.Failed(AppError.PermissionDenied()))
        val vm = viewModel()
        vm.onIntent(BlurryPhotosIntent.ScreenStarted)
        assertEquals(AppError.PermissionDenied(), vm.state.value.error)

        scanner.emissions = listOf(BlurScanProgress.Done(groups = scanned, skipped = 0))
        vm.onIntent(BlurryPhotosIntent.ScreenStarted)
        vm.onIntent(BlurryPhotosIntent.CompletionAnimationFinished)

        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.groups.sumOf { it.photos.size })
        // A retry is the same visit: the open event is not counted twice.
        assertEquals(1, analytics.events.count { it is AnalyticsEvent.FeatureOpened })
    }

    @Test
    fun `a finished scan is not restarted by a returning screen`() = main.runVmTest {
        val vm = started()
        scanner.emissions = listOf(BlurScanProgress.Done(groups = persistentListOf(), skipped = 0))

        vm.onIntent(BlurryPhotosIntent.ScreenStarted)

        // Still the first scan's result: a returning LaunchedEffect must not wipe the grid.
        assertEquals(3, vm.state.value.groups.sumOf { it.photos.size })
    }

    @Test
    fun `back cancels the scan and is an Effect, never a flag in state`() = main.runVmTest {
        val vm = started()

        vm.effects.test {
            vm.onIntent(BlurryPhotosIntent.BackPressed)
            assertEquals(BlurryPhotosEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty scan is the empty state and offers no delete`() = main.runVmTest {
        scanner.emissions = listOf(BlurScanProgress.Done(groups = persistentListOf(), skipped = 0))
        val vm = started()

        assertTrue(vm.state.value.showEmptyState)
        assertFalse(vm.state.value.canDelete)
        // Nothing was found, so there is nothing to say was pre-selected.
        assertFalse(vm.state.value.isPreselected)
    }
}
