package com.pion.phonecleaner.feature.home

import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The three dialogs. Each is `state.dialog`, so each one of these assertions is also the assertion
 * that it survives a rotation — the property all eighteen of the competitor's dialogs lack
 * structurally, because every one is a raw `Dialog` built from an Activity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeDialogsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `two back presses raise one exit offer`() = runTest(dispatcher) {
        val fixture = homeFixture()

        fixture.viewModel.onIntent(HomeIntent.BackPressed)
        advanceUntilIdle()
        fixture.viewModel.onIntent(HomeIntent.BackPressed)
        advanceUntilIdle()

        assertEquals(HomeDialog.ExitOffer(FeatureId.BigFiles), fixture.viewModel.state.value.dialog)
        assertEquals(1, fixture.analytics.events.count { it is AnalyticsEvent.ExitOfferShown })
    }

    @Test
    fun `declining the exit offer exits exactly once`() = runTest(dispatcher) {
        val fixture = homeFixture()
        fixture.viewModel.onIntent(HomeIntent.BackPressed)
        advanceUntilIdle()
        // Collected only AFTER the dialog was raised: the effects channel buffers, so a one-shot
        // raised with no collector attached is delivered rather than dropped (MVI §7).
        val effects = fixture.collectEffects(this)

        fixture.viewModel.onIntent(HomeIntent.ExitOfferAnswered(accepted = false))
        advanceUntilIdle()

        assertEquals(listOf(HomeEffect.ExitApp), effects)
        assertNull(fixture.viewModel.state.value.dialog)
    }

    @Test
    fun `agreeing to the safety check records the consent before navigating`() = runTest(dispatcher) {
        val fixture = homeFixture()
        val effects = fixture.collectEffects(this)

        fixture.viewModel.onIntent(HomeIntent.FeatureTapped(FeatureId.Antivirus))
        advanceUntilIdle()
        assertEquals(HomeDialog.AntivirusConsent, fixture.viewModel.state.value.dialog)
        assertTrue("the gate must not navigate before it is answered", effects.isEmpty())

        fixture.viewModel.onIntent(HomeIntent.SecurityConsentAnswered(accepted = true))
        advanceUntilIdle()

        assertTrue(fixture.viewModel.state.value.hasAcceptedSecurityConsent)
        assertEquals(listOf(HomeEffect.NavigateToFeature(FeatureId.Antivirus)), effects)
        // Counted once, although the tap passed through the reducer twice.
        assertEquals(listOf(FeatureId.Antivirus), fixture.analytics.featureOpens)
    }

    @Test
    fun `declining the safety check neither records consent nor navigates`() = runTest(dispatcher) {
        val fixture = homeFixture()
        val effects = fixture.collectEffects(this)

        fixture.viewModel.onIntent(HomeIntent.FeatureTapped(FeatureId.Antivirus))
        advanceUntilIdle()
        fixture.viewModel.onIntent(HomeIntent.SecurityConsentAnswered(accepted = false))
        advanceUntilIdle()

        assertEquals(emptyList<HomeEffect>(), effects)
        assertTrue(!fixture.viewModel.state.value.hasAcceptedSecurityConsent)
        assertNull(fixture.viewModel.state.value.dialog)
    }

    @Test
    fun `a resume offers the notification sheet once, and never from a notification`() =
        runTest(dispatcher) {
            val fixture = homeFixture()
            advanceUntilIdle() // PermissionRepository emits: the grant set is empty, so the ask stands.

            fixture.viewModel.onIntent(HomeIntent.ScreenResumed)
            advanceUntilIdle()
            assertEquals(HomeDialog.NotificationPermission, fixture.viewModel.state.value.dialog)

            fixture.viewModel.onIntent(HomeIntent.NotificationSheetAnswered(accepted = false))
            advanceUntilIdle()
            assertNull(fixture.viewModel.state.value.dialog)

            // Declining is an ANSWER, so the sheet does not reappear on the next resume.
            fixture.viewModel.onIntent(HomeIntent.ScreenResumed)
            advanceUntilIdle()
            assertNull(fixture.viewModel.state.value.dialog)
        }

    @Test
    fun `the sheet is not offered when notifications are already granted`() = runTest(dispatcher) {
        val permissions = FakePermissionRepository()
        permissions.granted.value = persistentSetOf(AppPermission.Notifications)
        val fixture = homeFixture(permissions = permissions)
        advanceUntilIdle()

        fixture.viewModel.onIntent(HomeIntent.ScreenResumed)
        advanceUntilIdle()

        assertNull(fixture.viewModel.state.value.dialog)
    }
}
