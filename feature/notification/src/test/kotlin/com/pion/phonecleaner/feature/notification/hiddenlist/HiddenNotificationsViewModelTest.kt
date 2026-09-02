package com.pion.phonecleaner.feature.notification.hiddenlist

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.policy.MinimumDuration
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.usecase.ClearHiddenNotificationsUseCase
import com.pion.phonecleaner.domain.usecase.DismissHiddenNotificationUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ObserveHiddenNotificationsUseCase
import com.pion.phonecleaner.domain.usecase.ObserveNotificationHidingSettingsUseCase
import com.pion.phonecleaner.domain.usecase.SetNotificationHidingUseCase
import com.pion.phonecleaner.feature.notification.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.notification.testing.FakeHiddenNotificationNotifier
import com.pion.phonecleaner.feature.notification.testing.FakeNotificationCleanerRepository
import com.pion.phonecleaner.feature.notification.testing.FakeNotificationHidingSettingsStore
import com.pion.phonecleaner.feature.notification.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.notification.testing.hidden
import com.pion.phonecleaner.feature.notification.testing.runVmTest
import com.pion.phonecleaner.feature.notification.testing.settings
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration

/** `docs/screens/17-notification-and-permissions.md` §3.2 and the deltas of §3.5. */
internal class HiddenNotificationsViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repository = FakeNotificationCleanerRepository(
        listOf(hidden("k1", "com.chat"), hidden("k2", "com.mail")),
    )
    private val store = FakeNotificationHidingSettingsStore()
    private val notifier = FakeHiddenNotificationNotifier()
    private val usage = FakeFeatureUsageRepository()

    /**
     * `Duration.ZERO`, which is the whole reason the floor is a `MinimumDuration` constructor argument
     * and not a `delay` inside the ViewModel (`LLM.md` §3.2).
     */
    private fun viewModel() = HiddenNotificationsViewModel(
        observeHidden = ObserveHiddenNotificationsUseCase(repository),
        observeSettings = ObserveNotificationHidingSettingsUseCase(store),
        dismiss = DismissHiddenNotificationUseCase(repository),
        clearAll = ClearHiddenNotificationsUseCase(repository, notifier, MinimumDuration(Duration.ZERO)),
        setHiding = SetNotificationHidingUseCase(store),
        markFeatureUsed = MarkFeatureUsedUseCase(usage),
    )

    @Test
    fun `the room flow is the whole refresh mechanism`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()
        assertEquals(2, viewModel.state.value.notifications.size)

        repository.rows.value = kotlinx.collections.immutable.persistentListOf(hidden("k3"))

        // No event bus, no re-read: the store emitted and the screen already has it (§3.5).
        assertEquals(listOf("k3"), viewModel.state.value.notifications.map { it.key })
    }

    @Test
    fun `opening the screen stamps the feature's own usage key`() = dispatcherRule.runVmTest {
        viewModel()

        assertEquals(listOf(FeatureId.NotificationCleaner), usage.marked)
    }

    @Test
    fun `a row tap launches the app and only then dismisses`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()

        viewModel.onIntent(HiddenNotificationsIntent.NotificationTapped("k1"))

        assertEquals(HiddenNotificationsEffect.LaunchApp("com.chat"), viewModel.effects.first())
        assertEquals(listOf("k1"), repository.dismissed)
        // The row went because the repository flow re-emitted, not because the ViewModel edited a list.
        assertEquals(listOf("k2"), viewModel.state.value.notifications.map { it.key })
    }

    @Test
    fun `clear all holds the stage until the animation reports back`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()

        viewModel.onIntent(HiddenNotificationsIntent.ClearAllTapped)

        // The count is durable: it lives in state, so a rotation cannot lose it (§3.5).
        assertEquals(ClearStage.Finished(2), viewModel.state.value.clearStage)
        assertTrue(viewModel.state.value.isClearing)
        assertEquals(1, notifier.dismissals)

        viewModel.onIntent(HiddenNotificationsIntent.ClearAnimationFinished)

        assertEquals(ClearStage.Idle, viewModel.state.value.clearStage)
        assertEquals(
            HiddenNotificationsEffect.NavigateToCleanResult(2),
            viewModel.effects.first(),
        )
    }

    @Test
    fun `a second clear while clearing is ignored`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()
        viewModel.onIntent(HiddenNotificationsIntent.ClearAllTapped)

        viewModel.onIntent(HiddenNotificationsIntent.ClearAllTapped)

        assertEquals(1, repository.clearCalls)
    }

    @Test
    fun `a failed clear returns the stage to idle`() = dispatcherRule.runVmTest {
        repository.clearError = AppError.Storage(cause = "locked")
        val viewModel = viewModel()

        viewModel.onIntent(HiddenNotificationsIntent.ClearAllTapped)

        assertEquals(ClearStage.Idle, viewModel.state.value.clearStage)
        assertTrue(viewModel.effects.first() is HiddenNotificationsEffect.ShowMessage)
    }

    @Test
    fun `back is blocked while clearing`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()
        viewModel.onIntent(HiddenNotificationsIntent.ClearAllTapped)

        viewModel.onIntent(HiddenNotificationsIntent.BackPressed)

        assertEquals(
            HiddenNotificationsEffect.ShowClearInProgressMessage,
            viewModel.effects.first(),
        )
    }

    @Test
    fun `the paused banner is rendered, never navigated to`() = dispatcherRule.runVmTest {
        store.state.value = settings(master = false)
        val viewModel = viewModel()

        assertTrue(!viewModel.state.value.isMasterEnabled)

        viewModel.onIntent(HiddenNotificationsIntent.ResumeHidingTapped)

        // The banner's action writes the switch here rather than sending the user off to find it.
        assertEquals(listOf(true), store.masterWrites)
        assertTrue(viewModel.state.value.isMasterEnabled)
    }
}
