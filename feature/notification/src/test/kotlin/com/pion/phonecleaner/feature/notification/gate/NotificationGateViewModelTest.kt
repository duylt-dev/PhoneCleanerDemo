package com.pion.phonecleaner.feature.notification.gate

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.usecase.ObserveNotificationHidingSettingsUseCase
import com.pion.phonecleaner.domain.usecase.SetNotificationHidingUseCase
import com.pion.phonecleaner.feature.notification.testing.FakeNotificationHidingSettingsStore
import com.pion.phonecleaner.feature.notification.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.notification.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.notification.testing.runVmTest
import com.pion.phonecleaner.feature.notification.testing.settings
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The three-way branch of `od.i.H()`, as a reducer
 * (`docs/screens/17-notification-and-permissions.md` §1.2).
 */
internal class NotificationGateViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val store = FakeNotificationHidingSettingsStore()
    private val permissions = FakePermissionRepository()

    private fun viewModel() = NotificationGateViewModel(
        observeSettings = ObserveNotificationHidingSettingsUseCase(store),
        setHiding = SetNotificationHidingUseCase(store),
        permissions = permissions,
    )

    @Test
    fun `stays Resolving until the composable has reported a start`() = dispatcherRule.runVmTest {
        permissions.granted = setOf(AppPermission.NotificationListener)

        val state = viewModel().state.value

        // The CTA must never be tappable before the reason is known (§1.1).
        assertEquals(NotificationGateReason.Resolving, state.reason)
        assertTrue(!state.isReady)
    }

    @Test
    fun `no listener access wins over the master switch`() = dispatcherRule.runVmTest {
        permissions.granted = emptySet()
        store.state.value = settings(master = false)
        val viewModel = viewModel()

        viewModel.onIntent(NotificationGateIntent.ScreenStarted)

        // Resolution order: the interceptor cannot run at all without listener access (§1.2).
        assertEquals(NotificationGateReason.ListenerAccessMissing, viewModel.state.value.reason)
    }

    @Test
    fun `access plus master off resolves to the paused wall`() = dispatcherRule.runVmTest {
        permissions.granted = setOf(AppPermission.NotificationListener)
        store.state.value = settings(master = false)
        val viewModel = viewModel()

        viewModel.onIntent(NotificationGateIntent.ScreenStarted)

        assertEquals(NotificationGateReason.HidingDisabled, viewModel.state.value.reason)
    }

    @Test
    fun `access plus master on navigates straight through`() = dispatcherRule.runVmTest {
        permissions.granted = setOf(AppPermission.NotificationListener)
        store.state.value = settings(master = true)
        val viewModel = viewModel()

        viewModel.onIntent(NotificationGateIntent.ScreenStarted)

        assertEquals(NotificationGateEffect.NavigateToHiddenList, viewModel.effects.first())
    }

    @Test
    fun `the paused wall's CTA flips the master switch itself and navigates`() =
        dispatcherRule.runVmTest {
            permissions.granted = setOf(AppPermission.NotificationListener)
            store.state.value = settings(master = false)
            val viewModel = viewModel()
            viewModel.onIntent(NotificationGateIntent.ScreenStarted)

            viewModel.onIntent(NotificationGateIntent.PrimaryCtaTapped)

            // The competitor's "Enable" enables nothing — it navigates and asks the user to find a
            // switch the app already owns (§1.5).
            assertEquals(listOf(true), store.masterWrites)
            assertEquals(NotificationGateEffect.NavigateToHiddenList, viewModel.effects.first())
        }

    @Test
    fun `a failed master write keeps the wall up`() = dispatcherRule.runVmTest {
        permissions.granted = setOf(AppPermission.NotificationListener)
        store.state.value = settings(master = false)
        store.writeError = AppError.Unexpected("disk")
        val viewModel = viewModel()
        viewModel.onIntent(NotificationGateIntent.ScreenStarted)

        viewModel.onIntent(NotificationGateIntent.PrimaryCtaTapped)

        assertEquals(NotificationGateReason.HidingDisabled, viewModel.state.value.reason)
        assertTrue(viewModel.effects.first() is NotificationGateEffect.ShowMessage)
    }

    @Test
    fun `the missing-access CTA opens settings and marks the wait`() = dispatcherRule.runVmTest {
        permissions.granted = emptySet()
        val viewModel = viewModel()
        viewModel.onIntent(NotificationGateIntent.ScreenStarted)

        viewModel.onIntent(NotificationGateIntent.PrimaryCtaTapped)

        assertTrue(viewModel.state.value.isAwaitingSystemGrant)
        assertEquals(
            NotificationGateEffect.OpenNotificationListenerSettings,
            viewModel.effects.first(),
        )
    }

    @Test
    fun `returning from settings with the grant given resolves through`() = dispatcherRule.runVmTest {
        permissions.granted = emptySet()
        val viewModel = viewModel()
        viewModel.onIntent(NotificationGateIntent.ScreenStarted)
        viewModel.onIntent(NotificationGateIntent.PrimaryCtaTapped)

        // There is no callback for BIND_NOTIFICATION_LISTENER_SERVICE; ON_START is the only signal.
        permissions.granted = setOf(AppPermission.NotificationListener)
        viewModel.onIntent(NotificationGateIntent.ScreenStarted)

        assertTrue(!viewModel.state.value.isAwaitingSystemGrant)
    }
}
