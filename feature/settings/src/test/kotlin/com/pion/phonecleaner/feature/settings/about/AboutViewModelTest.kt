package com.pion.phonecleaner.feature.settings.about

import app.cash.turbine.test
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.feature.settings.testing.FakeAppInfoProvider
import com.pion.phonecleaner.feature.settings.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.settings.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AboutViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun viewModel(isDebugBuild: Boolean = false) =
        AboutViewModel(FakeAppInfoProvider(isDebugBuild = isDebugBuild))

    @Test
    fun `initial state is the injected app info`() {
        val state = viewModel().state.value

        assertEquals("Phone Cleaner", state.appName)
        assertEquals("1.2.3", state.versionName)
        assertEquals(42L, state.versionCode)
    }

    @Test
    fun `the developer section is invisible in a release build`() {
        assertFalse(viewModel(isDebugBuild = false).state.value.isDeveloperSectionVisible)
        assertTrue(viewModel(isDebugBuild = true).state.value.isDeveloperSectionVisible)
    }

    @Test
    fun `tapping a legal row raises the effect carrying that document`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(AboutIntent.LegalDocumentTapped(LegalDocument.TermsOfService))

            val effect = awaitItem()
            assertTrue(effect is AboutEffect.NavigateToLegalDocument)
            assertEquals(LegalDocument.TermsOfService, effect.document)
            expectNoEvents()
        }
    }

    /**
     * The guard §3.2 asks for: the row does not exist in a release build, so a synthesised intent
     * must not reach the destination either.
     */
    @Test
    fun `a release build ignores the developer intent entirely`() = mainDispatcher.runVmTest {
        val vm = viewModel(isDebugBuild = false)

        vm.effects.test {
            vm.onIntent(AboutIntent.DeveloperRowTapped)
            expectNoEvents()
        }
    }

    @Test
    fun `a debug build routes the developer intent`() = mainDispatcher.runVmTest {
        val vm = viewModel(isDebugBuild = true)

        vm.effects.test {
            vm.onIntent(AboutIntent.DeveloperRowTapped)
            assertTrue(awaitItem() is AboutEffect.NavigateToDeveloperTools)
        }
    }

    @Test
    fun `back raises navigate back`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(AboutIntent.BackPressed)
            assertTrue(awaitItem() is AboutEffect.NavigateBack)
        }
    }
}
