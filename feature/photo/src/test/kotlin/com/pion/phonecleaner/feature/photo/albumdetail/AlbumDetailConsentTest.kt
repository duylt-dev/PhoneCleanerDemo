package com.pion.phonecleaner.feature.photo.albumdetail

import app.cash.turbine.test
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.photo.testing.runVmTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `DeleteOutcome.PendingConsent` is a **state the UI renders**, not an error: on API 30+
 * `MediaStore.createDeleteRequest` raising a system dialog is the ordinary path
 * (`docs/system-architecture.md` §8.4, `docs/screens/13-photo-and-media.md` §7.2).
 */
class AlbumDetailConsentTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val fixture = AlbumDetailFixture()
    private val token = PendingIntentToken("intent-sender")

    @Test
    fun `pending consent is a state the screen renders, not an error`() = main.runVmTest {
        fixture.deleteRaisesConsent(token)
        val vm = fixture.selectedFirst()

        vm.effects.test {
            vm.onIntent(AlbumDetailIntent.DeleteConfirmed)

            assertEquals(AlbumDetailEffect.RequestDeleteConsent(token), awaitItem())
            assertTrue(vm.state.value.isAwaitingConsent)
            assertEquals(setOf(fixture.first.contentUri), vm.state.value.pendingConsentUris)
            // Nothing is gone yet: the system is still asking.
            assertEquals(listOf(fixture.first, fixture.second), vm.state.value.photos)
            assertFalse(vm.state.value.consentDeclined)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a granted consent prunes the rows the system removed and sums their bytes`() =
        main.runVmTest {
            fixture.deleteRaisesConsent(token)
            val vm = fixture.selectedFirst()

            vm.effects.test {
                vm.onIntent(AlbumDetailIntent.DeleteConfirmed)
                skipItems(1) // the consent request, asserted above

                vm.onIntent(AlbumDetailIntent.DeleteConsentResult(granted = true))

                assertEquals(listOf(fixture.second), vm.state.value.photos)
                assertFalse(vm.state.value.isAwaitingConsent)
                assertEquals(
                    AlbumDetailEffect.NavigateToCleanResult(
                        CleanupSummary(
                            feature = FeatureId.ImageManager,
                            freedBytes = 500L,
                            itemCount = 1,
                            outcome = CleanupOutcome.Cleaned,
                        ),
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a declined consent says so and keeps the selection`() = main.runVmTest {
        fixture.deleteRaisesConsent(token)
        val vm = fixture.selectedFirst()
        vm.onIntent(AlbumDetailIntent.DeleteConfirmed)

        vm.onIntent(AlbumDetailIntent.DeleteConsentResult(granted = false))

        // The competitor leaves the selection intact and shows nothing at all (§7.5).
        assertTrue(vm.state.value.consentDeclined)
        assertEquals(setOf(PhotoId(1)), vm.state.value.selectedIds)
        assertEquals(listOf(fixture.first, fixture.second), vm.state.value.photos)
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
        assertFalse(vm.state.value.isAwaitingConsent)
    }
}
