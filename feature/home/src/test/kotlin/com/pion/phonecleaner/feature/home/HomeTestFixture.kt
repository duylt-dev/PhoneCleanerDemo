package com.pion.phonecleaner.feature.home

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * One assembled ViewModel plus the fakes a test needs to look inside it. Shared by the two test
 * classes so the wiring is written once and a new constructor argument breaks one line, not ten.
 */
internal class HomeFixture(
    val viewModel: HomeViewModel,
    val featureUsage: FakeFeatureUsageRepository,
    val analytics: RecordingAnalyticsRepository,
    val junk: FakeJunkRepository,
) {
    /**
     * Effects are a `Channel` with one consumer, so every test drains it into one list.
     *
     * A test may call this **after** raising an effect: the channel buffers, so a one-shot raised
     * while nothing is listening is delivered rather than dropped — which is the property `replay=0`
     * on a `SharedFlow` would not have (MVI §7).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun collectEffects(scope: TestScope): List<HomeEffect> {
        val received = mutableListOf<HomeEffect>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            viewModel.effects.toList(received)
        }
        return received
    }
}

internal fun homeFixture(
    savedState: SavedStateHandle = SavedStateHandle(),
    permissions: FakePermissionRepository = FakePermissionRepository(),
    junk: FakeJunkRepository = FakeJunkRepository(),
): HomeFixture {
    val featureUsage = FakeFeatureUsageRepository()
    val analytics = RecordingAnalyticsRepository()
    return HomeFixture(
        viewModel = HomeViewModel(
            savedStateHandle = savedState,
            storageInfo = FakeStorageInfoRepository(),
            cleanupLedger = FakeCleanupLedger(),
            junk = junk,
            permissions = permissions,
            featureUsage = featureUsage,
            markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
            analytics = analytics,
            log = AppLogger.NoOp,
        ),
        featureUsage = featureUsage,
        analytics = analytics,
        junk = junk,
    )
}
