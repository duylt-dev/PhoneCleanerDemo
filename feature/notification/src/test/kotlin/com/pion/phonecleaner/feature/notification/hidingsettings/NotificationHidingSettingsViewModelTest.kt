package com.pion.phonecleaner.feature.notification.hidingsettings

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.usecase.ObserveNotificationHidingSettingsUseCase
import com.pion.phonecleaner.domain.usecase.SetNotificationHidingUseCase
import com.pion.phonecleaner.feature.notification.testing.FakeNotificationHidingSettingsStore
import com.pion.phonecleaner.feature.notification.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.notification.testing.hidingApp
import com.pion.phonecleaner.feature.notification.testing.runVmTest
import com.pion.phonecleaner.feature.notification.testing.settings
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** `docs/screens/17-notification-and-permissions.md` §2.2 and the deltas of §2.5. */
internal class NotificationHidingSettingsViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val store = FakeNotificationHidingSettingsStore(
        settings(master = true, apps = listOf(hidingApp("com.a", true), hidingApp("com.b"))),
    )

    private fun viewModel() = NotificationHidingSettingsViewModel(
        observeSettings = ObserveNotificationHidingSettingsUseCase(store),
        setHiding = SetNotificationHidingUseCase(store),
    )

    @Test
    fun `one collector fills both halves and lowers loading`() = dispatcherRule.runVmTest {
        val state = viewModel().state.value

        assertTrue(!state.isLoading)
        assertTrue(state.isMasterEnabled)
        assertEquals(2, state.apps.size)
        assertEquals(1, state.enabledCount)
    }

    @Test
    fun `a second emission replaces the list rather than appending to it`() =
        dispatcherRule.runVmTest {
            val viewModel = viewModel()

            store.state.value = settings(apps = listOf(hidingApp("com.a", true)))

            // `InteencActivity:159` binds with addAll, which duplicates every row on re-emission (§2.5).
            assertEquals(1, viewModel.state.value.apps.size)
        }

    @Test
    fun `disabling the last app never writes the master switch`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()

        viewModel.onIntent(NotificationHidingSettingsIntent.AppToggled("com.a", enabled = false))

        // The per-app action must not mutate a global setting with no undo and no message (§2.5).
        assertEquals(listOf("com.a" to false), store.appWrites)
        assertTrue(store.masterWrites.isEmpty())
        assertEquals(0, viewModel.state.value.enabledCount)
    }

    @Test
    fun `the master toggle never touches the rows`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()

        viewModel.onIntent(NotificationHidingSettingsIntent.MasterToggled(enabled = false))

        assertEquals(listOf(false), store.masterWrites)
        assertTrue(store.appWrites.isEmpty())
        assertEquals(2, viewModel.state.value.apps.size)
    }

    @Test
    fun `a busy package is cleared on the failure arm too`() = dispatcherRule.runVmTest {
        store.writeError = AppError.Unexpected("disk")
        val viewModel = viewModel()

        viewModel.onIntent(NotificationHidingSettingsIntent.AppToggled("com.b", enabled = true))

        assertTrue(viewModel.state.value.togglingPackages.isEmpty())
        assertTrue(viewModel.effects.first() is NotificationHidingSettingsEffect.ShowMessage)
    }

    @Test
    fun `an upstream failure lowers loading and offers a retry`() = dispatcherRule.runVmTest {
        store.observeError = IllegalStateException("package manager")

        val viewModel = viewModel()

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertTrue(state.error != null)
        // The competitor's screen stays blank forever: a PackageManager throw escapes its coroutine.
        assertTrue(state.isEmpty)
    }

    @Test
    fun `retry with nothing to retry does not open a second collector`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()

        viewModel.onIntent(NotificationHidingSettingsIntent.RetryTapped)

        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `back pops`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()

        viewModel.onIntent(NotificationHidingSettingsIntent.BackPressed)

        assertEquals(NotificationHidingSettingsEffect.NavigateBack, viewModel.effects.first())
    }
}
