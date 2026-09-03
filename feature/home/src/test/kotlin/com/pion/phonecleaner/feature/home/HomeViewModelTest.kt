package com.pion.phonecleaner.feature.home

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.catalog.FeatureAvailability
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The navigation and refresh half of `docs/screens/11-home.md` §4.1. The dialogs are
 * [HomeDialogsTest]; the ticker cases are absent because `NetworkTrafficRepository` does not exist
 * (see the UNKNOWN on `HomeIntent.ScreenStopped`).
 *
 * [`the analytics id of a feature tap is that feature's own id`] is the one that matters most: it is
 * the regression test for the competitor's live `100525`/`100526` transposition, where a Running Apps
 * tap from the grid reports as Battery Info's id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `every feature tap raises exactly one navigation`() = runTest(dispatcher) {
        val fixture = homeFixture()
        val effects = fixture.collectEffects(this)
        // Antivirus is the one entry point with a pre-flight; it is covered in HomeDialogsTest.
        val ungated = openable.filter { it != FeatureId.Antivirus }

        ungated.forEach { fixture.viewModel.onIntent(HomeIntent.FeatureTapped(it)) }
        advanceUntilIdle()

        assertEquals(ungated.map { HomeEffect.NavigateToFeature(it) }, effects)
    }

    @Test
    fun `the analytics id of a feature tap is that feature's own id`() = runTest(dispatcher) {
        val fixture = homeFixture()

        openable.forEach { fixture.viewModel.onIntent(HomeIntent.FeatureTapped(it)) }
        advanceUntilIdle()

        // The destination and the id are one enum constant, so they cannot be edited apart.
        assertEquals(openable, fixture.analytics.featureOpens)
        assertEquals(openable, fixture.featureUsage.marked)
    }

    /**
     * The lock is in the reducer, so a tap that reaches it anyway — a stale composition, a deep link,
     * an accepted exit offer — still goes nowhere. Not tracked and not marked used either: a feature
     * that could not be opened was not opened, and counting it would put a figure in the usage ledger
     * that no screen ever rendered (`FeatureAvailability`).
     */
    @Test
    fun `a coming-soon feature navigates nowhere and is not counted`() = runTest(dispatcher) {
        val fixture = homeFixture()
        val effects = fixture.collectEffects(this)

        FeatureAvailability.comingSoon.forEach {
            fixture.viewModel.onIntent(HomeIntent.FeatureTapped(it))
        }
        advanceUntilIdle()

        assertEquals(emptyList<HomeEffect>(), effects)
        assertEquals(emptyList<FeatureId>(), fixture.analytics.featureOpens)
        assertEquals(emptyList<FeatureId>(), fixture.featureUsage.marked)
    }

    /** A deep link is the one route into a feature that never passes a tile it could disable. */
    @Test
    fun `a deep link to a coming-soon feature navigates nowhere`() = runTest(dispatcher) {
        val fixture = homeFixture(
            savedState = SavedStateHandle(
                mapOf(HomeArgs.FEATURE to FeatureId.NetworkTest.name),
            ),
        )
        val effects = fixture.collectEffects(this)

        fixture.viewModel.onIntent(HomeIntent.ScreenStarted)
        advanceUntilIdle()

        assertEquals(emptyList<HomeEffect>(), effects)
    }

    /** The features `FeatureAvailability` locks, in enum order. */
    private val openable: List<FeatureId> =
        FeatureId.entries.filter(FeatureAvailability::isAvailable)

    @Test
    fun `a gated feature asks for one permission and does not navigate`() = runTest(dispatcher) {
        val fixture = homeFixture(
            permissions = FakePermissionRepository { persistentSetOf(AppPermission.UsageStats) },
        )
        val effects = fixture.collectEffects(this)

        fixture.viewModel.onIntent(HomeIntent.FeatureTapped(FeatureId.NetworkTraffic))
        advanceUntilIdle()

        assertEquals(listOf(HomeEffect.RequestPermission(AppPermission.UsageStats)), effects)
        assertEquals(FeatureId.NetworkTraffic, fixture.viewModel.state.value.permissionGate)
    }

    @Test
    fun `a granted permission resumes the gated feature without counting the tap twice`() =
        runTest(dispatcher) {
            val permissions = FakePermissionRepository { persistentSetOf(AppPermission.UsageStats) }
            val fixture = homeFixture(permissions = permissions)
            val effects = fixture.collectEffects(this)

            fixture.viewModel.onIntent(HomeIntent.FeatureTapped(FeatureId.NetworkTraffic))
            advanceUntilIdle()
            permissions.missing = { persistentSetOf() } // the user granted it in Settings
            fixture.viewModel.onIntent(
                HomeIntent.PermissionResolved(AppPermission.UsageStats, granted = true),
            )
            advanceUntilIdle()

            assertEquals(
                listOf(
                    HomeEffect.RequestPermission(AppPermission.UsageStats),
                    HomeEffect.NavigateToFeature(FeatureId.NetworkTraffic),
                ),
                effects,
            )
            // Counted once, although the tap passed through the gate twice.
            assertEquals(listOf(FeatureId.NetworkTraffic), fixture.analytics.featureOpens)
            assertNull(fixture.viewModel.state.value.permissionGate)
        }

    @Test
    fun `a deep link navigates once and not again on a second start`() = runTest(dispatcher) {
        val fixture = homeFixture(
            savedState = SavedStateHandle(mapOf(HomeArgs.FEATURE to FeatureId.AppLock.name)),
        )
        val effects = fixture.collectEffects(this)

        fixture.viewModel.onIntent(HomeIntent.ScreenStarted)
        advanceUntilIdle()
        fixture.viewModel.onIntent(HomeIntent.ScreenStarted)
        advanceUntilIdle()

        assertEquals(listOf(HomeEffect.NavigateToFeature(FeatureId.AppLock)), effects)
    }

    @Test
    fun `a resume refreshes the estimate once and always lowers the busy flag`() = runTest(dispatcher) {
        val fixture = homeFixture()

        fixture.viewModel.onIntent(HomeIntent.ScreenResumed)
        // The second arrives while the first is in flight and must not reach the repository.
        fixture.viewModel.onIntent(HomeIntent.ScreenResumed)
        advanceUntilIdle()
        assertEquals(1, fixture.junk.refreshes)
        assertFalse(fixture.viewModel.state.value.isJunkEstimating)

        // …and the one after it must, or the guard has quietly become a latch.
        fixture.viewModel.onIntent(HomeIntent.ScreenResumed)
        advanceUntilIdle()
        assertEquals(2, fixture.junk.refreshes)
    }

    @Test
    fun `a cached zero is not the same statement as no cached figure`() = runTest(dispatcher) {
        val junk = FakeJunkRepository()
        val fixture = homeFixture(junk = junk)
        advanceUntilIdle()

        assertEquals(JunkPill.NotMeasured, fixture.viewModel.state.value.junkPill)
        junk.cached.value = 0L
        advanceUntilIdle()
        assertEquals(JunkPill.Clean, fixture.viewModel.state.value.junkPill)
        junk.cached.value = 4_096L
        advanceUntilIdle()
        assertEquals(JunkPill.Measured(4_096L), fixture.viewModel.state.value.junkPill)
    }

    @Test
    fun `the layout holds every feature exactly once`() {
        val laidOut = HomeSections.build().flatMap { section -> section.tiles.map { it.feature } }
        assertEquals(laidOut.size, laidOut.toSet().size) // no feature drawn twice
        assertEquals(FeatureId.entries.size - 1, laidOut.size) // the hero is not a tile
        assertEquals(FeatureId.entries.toSet(), laidOut.toSet() + HomeSections.heroFeature)
    }

    /**
     * A deferred feature stays ON the page, locked — it is not removed. The state carries the lock so
     * the grid draws it and this test can read it without composing anything.
     */
    @Test
    fun `the layout locks exactly the coming-soon features and hides none of them`() {
        val tiles = HomeSections.build().flatMap { it.tiles }
        val locked = tiles.filter { it.isComingSoon }.map { it.feature }.toSet()

        assertEquals(FeatureAvailability.comingSoon - HomeSections.heroFeature, locked)
        // The hero is not a tile, so its own lock is the one State derives.
        assertTrue(HomeState().isHeroComingSoon)
    }
}
