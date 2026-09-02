package com.pion.phonecleaner.feature.settings.settings

import app.cash.turbine.test
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.settings.AppLanguage
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCentreCatalog
import com.pion.phonecleaner.feature.settings.testing.FakeAppInfoProvider
import com.pion.phonecleaner.feature.settings.testing.FakeLanguageRepository
import com.pion.phonecleaner.feature.settings.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.settings.testing.FakeResidentWidgetSettings
import com.pion.phonecleaner.feature.settings.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.settings.testing.StorageFailure
import com.pion.phonecleaner.feature.settings.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val languages = FakeLanguageRepository()
    private val widget = FakeResidentWidgetSettings()
    private val permissions = FakePermissionRepository()

    private fun viewModel() = SettingsViewModel(
        languageRepository = languages,
        appInfo = FakeAppInfoProvider(),
        widgetSettings = widget,
        permissions = permissions,
    )

    // ── the widget switch: PENDING OWNER DECISION 4 ───────────────────────────────────────────

    @Test
    fun `the resident widget is off until the user turns it on`() {
        assertFalse(viewModel().state.value.isResidentWidgetEnabled)
    }

    @Test
    fun `toggling writes through the repository, and the collector renders the result`() {
        val vm = viewModel()

        vm.onIntent(SettingsIntent.ResidentWidgetToggled(true))

        assertEquals(listOf(true), widget.writes)
        assertTrue(vm.state.value.isResidentWidgetEnabled)
    }

    /** No optimistic copy: a failed write must leave the switch showing what the store holds. */
    @Test
    fun `a failed write leaves the switch off`() {
        widget.writeResult = StorageFailure
        val vm = viewModel()

        vm.onIntent(SettingsIntent.ResidentWidgetToggled(true))

        assertEquals(listOf(true), widget.writes)
        assertFalse(vm.state.value.isResidentWidgetEnabled)
    }

    // ── the language row's trailing text ──────────────────────────────────────────────────────

    @Test
    fun `following the system is rendered as null, not as an unknown`() {
        assertNull(viewModel().state.value.currentLanguage)
    }

    @Test
    fun `the applied language reaches the row`() {
        languages.applied.value = AppLanguage("ja-JP")

        assertEquals(AppLanguage("ja-JP"), viewModel().state.value.currentLanguage)
    }

    // ── the permission badge ──────────────────────────────────────────────────────────────────

    @Test
    fun `the badge counts the same roster the centre draws`() {
        val vm = viewModel()

        // The fake starts with an empty emission, so nothing is granted yet.
        assertEquals(PermissionCentreCatalog.managed.size, vm.state.value.missingPermissionCount)

        permissions.grant(AppPermission.Notifications)

        assertEquals(PermissionCentreCatalog.managed.size - 1, vm.state.value.missingPermissionCount)
    }

    @Test
    fun `the badge reaches zero when everything is granted`() {
        val vm = viewModel()

        PermissionCentreCatalog.managed.forEach { permissions.grant(it) }

        assertEquals(0, vm.state.value.missingPermissionCount)
    }

    // ── navigation is an Effect ───────────────────────────────────────────────────────────────

    @Test
    fun `each row raises exactly one navigation effect`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(SettingsIntent.LanguageRowTapped)
            assertTrue(awaitItem() is SettingsEffect.NavigateToLanguage)

            vm.onIntent(SettingsIntent.PermissionCentreRowTapped)
            assertTrue(awaitItem() is SettingsEffect.NavigateToPermissionCentre)

            vm.onIntent(SettingsIntent.AboutRowTapped)
            assertTrue(awaitItem() is SettingsEffect.NavigateToAbout)

            vm.onIntent(SettingsIntent.BackPressed)
            assertTrue(awaitItem() is SettingsEffect.NavigateBack)

            expectNoEvents()
        }
    }

    /** `init` observes; resume must not be a second source of truth. */
    @Test
    fun `resume does nothing`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        val before = vm.state.value

        vm.effects.test {
            vm.onIntent(SettingsIntent.ScreenResumed)
            expectNoEvents()
        }
        assertEquals(before, vm.state.value)
    }
}
