package com.pion.phonecleaner.feature.settings.permissioncentre

import app.cash.turbine.test
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.feature.settings.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.settings.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.settings.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PermissionCentreViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val permissions = FakePermissionRepository()

    private fun viewModel() = PermissionCentreViewModel(permissions)

    // ── the roster ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the screen renders exactly the managed roster, in order`() {
        assertEquals(
            PermissionCentreCatalog.managed,
            viewModel().state.value.cards.map { it.permission },
        )
    }

    /**
     * `AllFiles` is never assumed grantable and `UsageStats` sits behind a pending owner decision;
     * neither may appear as a card that asks the user for it.
     */
    @Test
    fun `pending-decision and ungrantable permissions are absent`() {
        val rendered = viewModel().state.value.cards.map { it.permission }

        assertFalse(AppPermission.AllFiles in rendered)
        assertFalse(AppPermission.UsageStats in rendered)
        assertFalse(AppPermission.Overlay in rendered)
    }

    @Test
    fun `a granted permission stays visible in its own section`() {
        permissions.grant(AppPermission.Notifications)

        val state = viewModel().state.value

        assertEquals(listOf(AppPermission.Notifications), state.granted.map { it.permission })
        assertFalse(state.isAllGranted)
        assertEquals(
            PermissionCentreCatalog.managed.size - 1,
            state.missing.size,
        )
    }

    @Test
    fun `all granted is only true once every card is`() {
        PermissionCentreCatalog.managed.forEach { permissions.grant(it) }

        assertTrue(viewModel().state.value.isAllGranted)
    }

    // ── the resume round trip: the whole fix for delta 1 ──────────────────────────────────────

    @Test
    fun `granting outside the app and returning refreshes the card`() {
        val vm = viewModel()
        assertFalse(vm.state.value.cards.first().isGranted)

        permissions.granted = setOf(AppPermission.Storage)
        vm.onIntent(PermissionCentreIntent.ScreenResumed)

        assertTrue(vm.state.value.cards.first { it.permission == AppPermission.Storage }.isGranted)
        assertFalse(vm.state.value.isRefreshing)
    }

    // ── the rationale sheet is STATE ──────────────────────────────────────────────────────────

    @Test
    fun `a permission that carries a rationale opens the sheet instead of asking`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(PermissionCentreIntent.CardTapped(AppPermission.Storage))
                expectNoEvents()
            }
            assertEquals(AppPermission.Storage, vm.state.value.rationaleFor)
            assertEquals(AppPermission.Storage, vm.state.value.rationaleCard?.permission)
        }

    @Test
    fun `notifications goes straight to the system dialog`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(PermissionCentreIntent.CardTapped(AppPermission.Notifications))

            val effect = awaitItem()
            assertTrue(effect is PermissionCentreEffect.RequestRuntimePermission)
            assertEquals(AppPermission.Notifications, effect.permission)
        }
        assertNull(vm.state.value.rationaleFor)
    }

    @Test
    fun `confirming a special access opens a system screen, not a runtime dialog`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()
            vm.onIntent(PermissionCentreIntent.CardTapped(AppPermission.NotificationListener))

            vm.effects.test {
                vm.onIntent(PermissionCentreIntent.RationaleConfirmed)

                val effect = awaitItem()
                assertTrue(effect is PermissionCentreEffect.OpenSystemSettings)
                assertEquals(AppPermission.NotificationListener, effect.permission)
            }
            assertNull(vm.state.value.rationaleFor)
        }

    @Test
    fun `dismissing the sheet asks the system for nothing`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(PermissionCentreIntent.CardTapped(AppPermission.Storage))

        vm.effects.test {
            vm.onIntent(PermissionCentreIntent.RationaleDismissed)
            expectNoEvents()
        }
        assertNull(vm.state.value.rationaleFor)
    }

    @Test
    fun `tapping an already granted card does nothing`() = mainDispatcher.runVmTest {
        permissions.grant(AppPermission.Storage)
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(PermissionCentreIntent.CardTapped(AppPermission.Storage))
            expectNoEvents()
        }
        assertNull(vm.state.value.rationaleFor)
    }

    // ── a refusal is remembered, and a refresh does not erase it ──────────────────────────────

    @Test
    fun `a refusal sets wasDeclined and survives a resume`() {
        val vm = viewModel()

        vm.onIntent(
            PermissionCentreIntent.PermissionResultReceived(AppPermission.Media, granted = false),
        )
        assertTrue(vm.state.value.cards.first { it.permission == AppPermission.Media }.wasDeclined)

        vm.onIntent(PermissionCentreIntent.ScreenResumed)
        assertTrue(vm.state.value.cards.first { it.permission == AppPermission.Media }.wasDeclined)
    }

    @Test
    fun `a later grant clears wasDeclined`() {
        val vm = viewModel()
        vm.onIntent(
            PermissionCentreIntent.PermissionResultReceived(AppPermission.Media, granted = false),
        )

        vm.onIntent(
            PermissionCentreIntent.PermissionResultReceived(AppPermission.Media, granted = true),
        )

        val card = vm.state.value.cards.first { it.permission == AppPermission.Media }
        assertTrue(card.isGranted)
        assertFalse(card.wasDeclined)
    }

    @Test
    fun `back raises navigate back`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(PermissionCentreIntent.BackPressed)
            assertTrue(awaitItem() is PermissionCentreEffect.NavigateBack)
        }
    }
}
