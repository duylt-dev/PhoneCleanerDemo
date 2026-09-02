package com.pion.phonecleaner.feature.photo.privacy

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.photo.GeotagScanProgress
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.StripStep
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.usecase.LoadGeotaggedPhotosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.StripPhotoLocationUseCase
import com.pion.phonecleaner.feature.photo.testing.FakeExifRepository
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

/** `docs/screens/13-photo-and-media.md` §5.2 and the six deltas of §5.5. */
class PhotoPrivacyViewModelTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val tagged1 = photo(1, sizeBytes = 400L)
    private val tagged2 = photo(2, sizeBytes = 600L)
    private val scanned = persistentListOf(group("2026-08", tagged1, tagged2))

    private val exif = FakeExifRepository(
        scan = listOf(
            GeotagScanProgress.Scanning(scanned = 1, total = 2),
            GeotagScanProgress.Done(groups = scanned),
        ),
    )
    private val analytics = RecordingAnalytics()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = PhotoPrivacyViewModel(
        savedStateHandle = savedState,
        loadGeotagged = LoadGeotaggedPhotosUseCase(exif),
        stripLocation = StripPhotoLocationUseCase(exif),
        markFeatureUsed = MarkFeatureUsedUseCase(RecordingFeatureUsage()),
        analytics = analytics,
    )

    private fun ready(savedState: SavedStateHandle = SavedStateHandle()) =
        viewModel(savedState).also {
            it.onIntent(PhotoPrivacyIntent.ScreenStarted)
            it.onIntent(PhotoPrivacyIntent.CompletionAnimationFinished)
        }

    @Test
    fun `the scan reports real counts and pre-selects nothing`() = main.runVmTest {
        val vm = viewModel()

        vm.onIntent(PhotoPrivacyIntent.ScreenStarted)

        assertEquals(1, vm.state.value.scanned)
        assertEquals(2, vm.state.value.toScan)
        assertEquals(scanned, vm.state.value.groups)
        // Removing a location tag is not reversible, so nothing is selected for the user.
        assertTrue(vm.state.value.selectedIds.isEmpty())
        assertEquals(ToolPhase.Completing, vm.state.value.phase)

        vm.onIntent(PhotoPrivacyIntent.CompletionAnimationFinished)
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
        assertFalse(vm.state.value.showEmptyState)
        assertEquals(listOf(AnalyticsEvent.FeatureOpened(FeatureId.PhotoPrivacy)), analytics.events)
    }

    @Test
    fun `a month header is a span, so toggling it is set arithmetic over its members`() =
        main.runVmTest {
            val vm = ready()

            vm.onIntent(PhotoPrivacyIntent.MonthToggled("2026-08"))
            assertEquals(setOf(PhotoId(1), PhotoId(2)), vm.state.value.selectedIds)
            assertEquals(1_000L, vm.state.value.selectedBytes)
            assertTrue(vm.state.value.canClear)

            vm.onIntent(PhotoPrivacyIntent.MonthToggled("2026-08"))
            assertTrue(vm.state.value.selectedIds.isEmpty())
            assertFalse(vm.state.value.canClear)
        }

    @Test
    fun `a cleared row leaves the list before the Effect, and the summary frees no bytes`() =
        main.runVmTest {
            exif.steps = listOf(
                StripStep(index = 0, total = 2, id = PhotoId(1), failed = false),
                StripStep(index = 1, total = 2, id = PhotoId(2), failed = false),
            )
            val vm = ready()
            vm.onIntent(PhotoPrivacyIntent.MonthToggled("2026-08"))
            vm.onIntent(PhotoPrivacyIntent.ClearPressed)
            assertTrue(vm.state.value.isClearConfirmVisible)

            vm.effects.test {
                vm.onIntent(PhotoPrivacyIntent.ClearConfirmed)

                assertEquals(listOf(PhotoId(1), PhotoId(2)), exif.strippedIds)
                assertTrue(vm.state.value.groups.isEmpty())
                assertNull(vm.state.value.strip)
                assertEquals(ToolPhase.Ready, vm.state.value.phase)
                assertEquals(
                    PhotoPrivacyEffect.NavigateToCleanResult(
                        CleanupSummary(
                            feature = FeatureId.PhotoPrivacy,
                            // A tag is removed in place: `DataCleared` is why the shared result
                            // screen renders no size block (§5.2).
                            freedBytes = 0L,
                            itemCount = 2,
                            outcome = CleanupOutcome.DataCleared,
                        ),
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a row the engine failed on stays in the list, stays selected and is counted`() =
        main.runVmTest {
            exif.steps = listOf(
                StripStep(index = 0, total = 2, id = PhotoId(1), failed = false),
                StripStep(index = 1, total = 2, id = PhotoId(2), failed = true),
            )
            val vm = ready()
            vm.onIntent(PhotoPrivacyIntent.MonthToggled("2026-08"))

            vm.onIntent(PhotoPrivacyIntent.ClearConfirmed)

            // The competitor's engine returns success unconditionally, which is what makes its own
            // failure toast unreachable (§5.5). A failed row can be tried again here.
            assertEquals(listOf(tagged2), vm.state.value.groups.single().photos)
            assertEquals(setOf(PhotoId(2)), vm.state.value.selectedIds)
        }

    @Test
    fun `back while the scan is in flight raises a stop confirm rather than a toast`() =
        main.runVmTest {
            exif.scan = listOf(GeotagScanProgress.Scanning(scanned = 1, total = 9))
            val vm = viewModel()
            vm.onIntent(PhotoPrivacyIntent.ScreenStarted)
            assertTrue(vm.state.value.isBusy)

            vm.onIntent(PhotoPrivacyIntent.BackPressed)
            assertTrue(vm.state.value.isStopConfirmVisible)

            vm.effects.test {
                vm.onIntent(PhotoPrivacyIntent.StopConfirmed)
                assertEquals(PhotoPrivacyEffect.NavigateBack, awaitItem())
                assertFalse(vm.state.value.isStopConfirmVisible)
                assertEquals(ToolPhase.Ready, vm.state.value.phase)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a restored selection drops ids the new scan no longer sees`() = main.runVmTest {
        val saved = SavedStateHandle(
            mapOf(PhotoPrivacyViewModel.SELECTION_KEY to longArrayOf(1L, 99L)),
        )

        val vm = ready(saved)

        // Keeping id 99 would put a count on the bar that no visible cell explains.
        assertEquals(setOf(PhotoId(1)), vm.state.value.selectedIds)
    }

    @Test
    fun `a denied read is an error on state, not an exception out of an unhandled scope`() =
        main.runVmTest {
            exif.scan = listOf(GeotagScanProgress.Failed(AppError.PermissionDenied()))
            val vm = viewModel()

            vm.onIntent(PhotoPrivacyIntent.ScreenStarted)

            assertEquals(AppError.PermissionDenied(), vm.state.value.error)
            assertEquals(ToolPhase.Ready, vm.state.value.phase)
        }
}
